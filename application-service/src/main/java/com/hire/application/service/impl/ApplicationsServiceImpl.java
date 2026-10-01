package com.hire.application.service.impl;

import com.hire.api.clients.JobClient;
import com.hire.application.mapper.AdminApplicationMapper;
import com.hire.application.mapper.ApplicationsMapper;
import com.hire.application.mapper.ResumeMapper;
import com.hire.application.service.ApplicationsService;
import com.hire.common.Result;
import com.hire.common.constant.MqConstants;
import com.hire.common.context.UserContext;
import com.hire.common.exception.BusinessException;
import com.hire.common.utils.JsonUtils;
import com.hire.common.utils.RedisUtil;
import com.hire.model.dto.ApplicationStatusChangedEvent;
import com.hire.model.dto.ApplicationStatusUpdateDTO;
import com.hire.model.dto.ApplicationSubmitDTO;
import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.model.entity.Applications;
import com.hire.model.entity.Resume;
import com.hire.model.vo.ApplicationDetailVO;
import com.hire.model.vo.ApplicationStatisticsVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.MyApplicationVO;
import com.hire.model.vo.PageResult;
import com.hire.model.vo.ReceivedApplicationVO;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 投递业务实现。
 *
 * 一次投递涉及 4 个动作：
 * 1. 校验当前登录用户与角色（网关透传 userId / role）
 * 2. 校验简历存在且属于本人（本库 t_resume）
 * 3. 远程校验职位有效 + 递增职位投递数（Feign 调 job-service）
 * 4. 写投递记录 + 投递成功后发 MQ 事件（notification-service 消费）
 *
 * 第 3、4 步是跨服务操作，这里用本地事务保证本库数据一致，
 * MQ 消息通过事务同步器在提交后才真正发出，避免“事务回滚了消息却发了”。
 *
 * 跨服务强一致（第 3 步）由 Seata AT 模式保证：本方法是全局事务发起方，
 * @GlobalTransactional 开启全局事务，XID 通过 Feign 拦截器透传给 job-service，
 * job-service 的本地事务作为分支注册到 TC；任一分支失败，TC 驱动所有分支回滚。
 */
@Slf4j
@Service
public class ApplicationsServiceImpl implements ApplicationsService {

    /** 统计天数缺省值（与文档 2.8 节、3.13 节规则一致） */
    private static final int STAT_DAYS_DEFAULT = 7;

    /** 统计时区固定为东八区，避免服务器时区差异导致日期错分 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** Redis 键前缀:投递防连点锁(app:apply:lock:{userId}:{jobId}) */
    private static final String KEY_APPLY_LOCK = "app:apply:lock:";
    /** 防连点锁有效期(秒):10 秒内同一用户对同一职位只允许一次提交 */
    private static final long APPLY_LOCK_TTL_SECONDS = 10;
    /** Redis 键前缀:平台投递统计缓存(app:stats:{days}) */
    private static final String KEY_STATS = "app:stats:";
    /** 统计类缓存 TTL:60 秒 */
    private static final long STATS_TTL_SECONDS = 60;

    @Autowired
    private ApplicationsMapper applicationsMapper;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private JobClient jobClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AdminApplicationMapper adminApplicationMapper;
    @Autowired
    private RedisUtil redisUtil;

    @Override
    // 开启全局事务
    @GlobalTransactional(name = "submit-application", rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Long submitApplication(ApplicationSubmitDTO dto) {
        // 1. 参数校验
        if (dto == null || dto.getJobId() == null || dto.getResumeId() == null) {
            throw new BusinessException("职位ID和简历ID不能为空");
        }

        // 2. 当前登录用户（由网关解析 token 后透传，UserInfoInterceptor 写入 ThreadLocal）
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }

        // 3. 角色校验：只有求职者可以投递；role 头缺失时不强校验，避免网关未透传时误伤
        String role = UserContext.getCurrentRole();
        if (role != null && !"seeker".equals(role)) {
            throw new BusinessException("只有求职者可以投递简历");
        }

        // 3.5 防连点锁：同一用户对同一职位 10 秒内只允许一次提交，挡重复点击与重复投递风暴；
        // Redis 异常时 RedisUtil.setIfAbsent 降级返回 true(放行)，正确性仍由唯一索引 uk_user_job 兜底
        String applyLockKey = KEY_APPLY_LOCK + userId + ":" + dto.getJobId();
        if (!redisUtil.setIfAbsent(applyLockKey, "1", Duration.ofSeconds(APPLY_LOCK_TTL_SECONDS))) {
            throw new BusinessException("操作太频繁，请稍后再试");
        }

        // 4. 简历必须存在且属于当前用户（必须带 userId 查询，否则可以投递别人的简历）
        Resume resume = resumeMapper.selectById(dto.getResumeId(), userId);
        if (resume == null) {
            throw new BusinessException("简历不存在");
        }

        // 5. 重复投递校验：先查一次给出友好提示，唯一索引 uk_user_job 做并发兜底
        int exists = applicationsMapper.countByUserAndJob(userId, dto.getJobId());
        if (exists > 0) {
            throw new BusinessException("已投递过该职位，请勿重复投递");
        }

        // 6. 远程校验职位：是否存在、是否招聘中，并拿到职位发布者ID
        JobInfoVO job = queryJobInfo(dto.getJobId());
        if (job == null) {
            throw new BusinessException("职位不存在");
        }
        if (!Integer.valueOf(1).equals(job.getStatus())) {
            throw new BusinessException("该职位已停止招聘");
        }

        // 7. 写投递记录
        Applications applications = Applications.builder()
                .userId(userId)
                .jobId(dto.getJobId())
                .resumeId(dto.getResumeId())
                .employerId(job.getEmployerId())
                .coverLetter(dto.getCoverLetter())
                .status(0)   // 0=待处理
                .deleted(0)
                .build();
        applicationsMapper.insert(applications);
        Long applicationId = applications.getId();

        // 8. 远程递增职位投递数（Seata 分支事务）：失败抛异常由 TC 驱动全局回滚，
        //    远程超时但对方实际成功的情况由 TC 二阶段补偿，避免“投递已存在但投递数没变”
        Result countResult = jobClient.increaseApplyCount(dto.getJobId());
        if (countResult == null || !Integer.valueOf(1).equals(countResult.getCode())) {
            throw new BusinessException("职位投递数更新失败，请稍后重试");
        }

        // 9. 组装并发送投递事件，事务提交后才真正发出
        ApplicationSubmittedEvent event = ApplicationSubmittedEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .applicationId(applicationId)
                .jobId(dto.getJobId())
                .jobTitle(job.getTitle())
                .employerId(job.getEmployerId())
                .applicantId(userId)
                .resumeId(dto.getResumeId())
                .coverLetter(dto.getCoverLetter())
                .build();
        publishApplicationSubmitted(event);

        log.info("投递成功, applicationId={}, userId={}, jobId={}", applicationId, userId, dto.getJobId());
        return applicationId;
    }

    /**
     * 分页查询当前求职者的投递历史。
     *
     * 实现要点：
     * 1. 只允许求职者本人查询，userId 从登录态获取，防止越权
     * 2. 职位基本信息通过 Feign 实时查询 job-service（同一职位的多个投递共用一次远程结果）
     * 3. 简历标题在本库 t_resume 查询（同一简历的多个投递共用一次查询结果）
     * 4. 远程查询失败不影响列表主流程：职位信息置空并记录日志，前端展示降级
     */
    @Override
    public PageResult<MyApplicationVO> listMyApplications(Integer page, Integer size, Integer status) {
        // 1. 登录与角色校验
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }
        String role = UserContext.getCurrentRole();
        if (role == null || !"seeker".equals(role)) {
            throw new BusinessException("仅求职者可查询投递记录");
        }

        // 2. 分页参数兜底：页码最小为 1，每页条数限制在 [1, 100]，防止恶意大分页拖垮数据库
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 20);
        int offset = (pageNum - 1) * pageSize;

        // 3. 先查总数，为 0 时直接返回空页，不再执行列表查询
        long total = applicationsMapper.countByUser(userId, status);
        List<MyApplicationVO> list = new ArrayList<>();
        if (total > 0) {
            List<Applications> records = applicationsMapper.selectPageByUser(userId, status, offset, pageSize);
            Map<Long, JobInfoVO> jobCache = new HashMap<>();
            Map<Long, String> resumeTitleCache = new HashMap<>();
            for (Applications record : records) {
                list.add(toMyApplicationVO(record, userId, jobCache, resumeTitleCache));
            }
        }
        return PageResult.<MyApplicationVO>builder()
                .total(total)
                .page(pageNum)
                .size(pageSize)
                .list(list)
                .build();
    }

    /**
     * 分页查询当前企业收到的投递列表。
     *
     * 实现要点：
     * 1. 只允许企业角色查询，投递记录按 employer_id（即当前登录企业）过滤，防止越权看到别家的投递
     * 2. 职位基本信息通过 Feign 实时查询 job-service（同一职位的多个投递共用一次远程结果）
     * 3. 求职者姓名与简历信息在本库 t_resume 查询（企业查看投递不限定简历归属用户）
     * 4. 远程查询失败不影响列表主流程：职位信息置空并记录日志，前端展示降级
     */
    @Override
    public PageResult<ReceivedApplicationVO> listReceivedApplications(Integer page, Integer size, Long jobId, Integer status) {
        // 1. 登录与角色校验：需要企业角色登录
        Long employerId = UserContext.getCurrentUserId();
        if (employerId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }
        String role = UserContext.getCurrentRole();
        if (role == null || !"employer".equals(role)) {
            throw new BusinessException("仅企业用户可查询收到的投递");
        }

        // 2. 分页参数兜底：页码最小为 1，每页条数限制在 [1, 20]，防止恶意大分页拖垮数据库
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 20);
        int offset = (pageNum - 1) * pageSize;

        // 3. 先查总数，为 0 时直接返回空页，不再执行列表查询
        long total = applicationsMapper.countByEmployer(employerId, jobId, status);
        List<ReceivedApplicationVO> list = new ArrayList<>();
        if (total > 0) {
            List<Applications> records = applicationsMapper.selectPageByEmployer(employerId, jobId, status, offset, pageSize);
            Map<Long, JobInfoVO> jobCache = new HashMap<>();
            Map<Long, Resume> resumeCache = new HashMap<>();
            for (Applications record : records) {
                list.add(toReceivedApplicationVO(record, jobCache, resumeCache));
            }
        }
        return PageResult.<ReceivedApplicationVO>builder()
                .total(total)
                .page(pageNum)
                .size(pageSize)
                .list(list)
                .build();
    }

    /**
     * 企业更新投递状态（1-已查看 / 2-面试邀请 / 3-已录用 / 4-已拒绝）。
     *
     * 实现要点：
     * 1. 需要企业角色登录，且投递对应的职位必须是当前企业发布（以 job-service 数据为准），防止越权
     * 2. 状态取值限定 1~4，非法值直接拒绝
     * 3. 备注为空时保留原备注，避免把企业之前填写的备注清掉
     * 4. 状态更新成功后，事务提交再发 MQ 状态变更事件，notification-service 消费后通知求职者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApplicationStatus(Long id, ApplicationStatusUpdateDTO dto) {
        // 1. 登录与角色校验：需要企业角色登录
        Long employerId = UserContext.getCurrentUserId();
        if (employerId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }
        String role = UserContext.getCurrentRole();
        if (role == null || !"employer".equals(role)) {
            throw new BusinessException("仅企业用户可更新投递状态");
        }

        // 2. 参数校验：状态必填且取值限定 1~4
        if (dto == null || dto.getStatus() == null) {
            throw new BusinessException("目标状态不能为空");
        }
        int status = dto.getStatus();
        if (status < 1 || status > 4) {
            throw new BusinessException("非法的目标状态：" + status);
        }

        // 3. 投递记录必须存在
        Applications record = applicationsMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("投递记录不存在");
        }

        // 4. 职位归属校验：该投递对应的职位必须是当前企业发布，否则视为越权操作
        JobInfoVO job = queryJobInfo(record.getJobId());
        if (job == null) {
            throw new BusinessException("职位不存在");
        }
        if (!employerId.equals(job.getEmployerId())) {
            throw new BusinessException("无权操作该投递：职位不是当前企业发布");
        }

        // 5. 更新状态：备注为空时保留原备注，避免覆盖历史备注
        String remark = (dto.getRemark() == null || dto.getRemark().trim().isEmpty())
                ? record.getRemark() : dto.getRemark().trim();
        int rows = applicationsMapper.updateStatus(id, status, remark);
        if (rows == 0) {
            throw new BusinessException("投递状态更新失败，请稍后重试");
        }

        // 6. 组装并发送状态变更事件，事务提交后才真正发出
        ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .applicationId(id)
                .jobId(record.getJobId())
                .jobTitle(job.getTitle())
                .employerId(employerId)
                .applicantId(record.getUserId())
                .resumeId(record.getResumeId())
                .status(status)
                .remark(remark)
                .build();
        publishApplicationStatusChanged(event);

        log.info("投递状态更新成功, applicationId={}, status={}, employerId={}", id, status, employerId);
    }

    /**
     * 查看投递详情：仅投递人本人或职位发布企业可见。
     *
     * 实现要点：
     * 1. 权限校验优先使用投递记录冗余的 user_id / employer_id（投递时已写入），
     *    企业侧无需再远程查职位归属，省一次 Feign 调用
     * 2. 简历详情本库查询：求职者视角带 userId 查（防越权），企业视角不限用户
     * 3. 职位信息 Feign 实时获取：职位可能已被删除，获取失败时降级为 null，不影响详情主流程
     */
    @Override
    public ApplicationDetailVO getApplicationDetail(Long id) {
        // 1. 登录校验
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }
        if (id == null) {
            throw new BusinessException("投递记录ID不能为空");
        }

        // 2. 投递记录必须存在（含未删除校验）
        Applications record = applicationsMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("投递记录不存在");
        }

        // 3. 权限校验：投递人本人或职位发布企业
        boolean isOwner = userId.equals(record.getUserId());
        boolean isEmployer = userId.equals(record.getEmployerId());
        if (!isOwner && !isEmployer) {
            throw new BusinessException("无权查看该投递详情");
        }

        // 4. 简历详情：求职者视角必须校验简历归属；企业视角不限用户（简历可能已被删除，此时为 null）
        Resume resume = isOwner
                ? resumeMapper.selectById(record.getResumeId(), userId)
                : resumeMapper.selectByIdOnly(record.getResumeId());

        // 5. 职位信息：Feign 实时获取，失败时降级为 null
        JobInfoVO job = queryJobInfoQuietly(record.getJobId());

        // 6. 组装详情 VO
        return ApplicationDetailVO.builder()
                .id(record.getId())
                .jobId(record.getJobId())
                .jobTitle(job == null ? null : job.getTitle())
                .jobCity(job == null ? null : job.getCity())
                .applicantId(record.getUserId())
                .applicantName(resume == null ? null : resume.getName())
                .resumeId(record.getResumeId())
                .resumeTitle(resume == null ? null : resume.getTitle())
                .resume(resume)
                .status(record.getStatus())
                .coverLetter(record.getCoverLetter())
                .remark(record.getRemark())
                .createTime(record.getCreateTime())
                .build();
    }

    /**
     * 投递记录 -> 收到的投递列表 VO：补充职位基本信息（Feign）与简历信息（本库查询），均带请求内缓存。
     * 求职者姓名取自投递所用简历中的姓名；简历可能已被求职者删除，此时姓名与简历信息为 null。
     */
    private ReceivedApplicationVO toReceivedApplicationVO(Applications record,
                                                          Map<Long, JobInfoVO> jobCache,
                                                          Map<Long, Resume> resumeCache) {
        // 职位基本信息：同一 jobId 只远程查一次，失败时静默降级为 null
        JobInfoVO job = jobCache.computeIfAbsent(record.getJobId(), this::queryJobInfoQuietly);

        // 简历信息：同一 resumeId 只查一次库（企业查看投递，不限定简历归属用户）
        Resume resume = resumeCache.computeIfAbsent(record.getResumeId(), id -> resumeMapper.selectByIdOnly(id));

        return ReceivedApplicationVO.builder()
                .id(record.getId())
                .jobId(record.getJobId())
                .jobTitle(job == null ? null : job.getTitle())
                .jobCity(job == null ? null : job.getCity())
                .applicantId(record.getUserId())
                .applicantName(resume == null ? null : resume.getName())
                .resumeId(record.getResumeId())
                .resumeTitle(resume == null ? null : resume.getTitle())
                .resume(resume)
                .status(record.getStatus())
                .coverLetter(record.getCoverLetter())
                .createTime(record.getCreateTime())
                .build();
    }

    /**
     * 投递记录 -> 列表 VO：补充职位基本信息（Feign）与简历标题（本库查询），均带请求内缓存。
     * 完整的职位/简历详情不在此填充，由前端点开详情时单独调详情接口，避免列表响应过大。
     */
    private MyApplicationVO toMyApplicationVO(Applications record, Long userId,
                                              Map<Long, JobInfoVO> jobCache,
                                              Map<Long, String> resumeTitleCache) {
        // 职位基本信息：同一 jobId 只远程查一次，失败时静默降级为 null
        JobInfoVO job = jobCache.computeIfAbsent(record.getJobId(), this::queryJobInfoQuietly);

        // 简历标题：同一 resumeId 只查一次库；简历可能已被删除，此时返回 null
        String resumeTitle = resumeTitleCache.computeIfAbsent(record.getResumeId(), resumeId -> {
            Resume resume = resumeMapper.selectById(resumeId, userId);
            return resume == null ? null : resume.getTitle();
        });

        return MyApplicationVO.builder()
                .id(record.getId())
                .jobId(record.getJobId())
                .jobTitle(job == null ? null : job.getTitle())
                .jobCity(job == null ? null : job.getCity())
                .resumeId(record.getResumeId())
                .resumeTitle(resumeTitle)
                .status(record.getStatus())
                .coverLetter(record.getCoverLetter())
                .createTime(record.getCreateTime())
                .build();
    }

    /**
     * 查询职位信息但不抛异常：列表场景下远程失败不应让整个接口 500
     */
    private JobInfoVO queryJobInfoQuietly(Long jobId) {
        try {
            Result<JobInfoVO> result = jobClient.getJobInfo(jobId);
            if (result == null || !Integer.valueOf(1).equals(result.getCode())) {
                log.warn("投递列表获取职位信息失败, jobId={}, msg={}",
                        jobId, result == null ? "远程调用无响应" : result.getMsg());
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.error("投递列表远程查询职位信息异常, jobId={}", jobId, e);
            return null;
        }
    }

    /**
     * Feign 查询职位信息，统一处理返回值为空或调用失败的情况
     */
    private JobInfoVO queryJobInfo(Long jobId) {
        Result<JobInfoVO> result = jobClient.getJobInfo(jobId);
        // 先判断调用是否成功：若 job-service 内部报错（例如 SQL 字段与真实表结构不匹配），
        // 必须把真实原因暴露出来，否则会被后面的 data 判空掩盖成“职位不存在”，难以排查
        if (result == null || !Integer.valueOf(1).equals(result.getCode())) {
            throw new BusinessException("职位信息查询失败：" + (result == null ? "远程调用无响应" : result.getMsg()));
        }
        return result.getData();
    }

    /**
     * 发送投递成功事件。
     *
     * 必须在事务方法内部调用：registerSynchronization 依赖线程绑定的事务同步器，
     * 只有在事务尚未结束时才有效，因此不能放到 Controller，也不能加 @Async 换新线程。
     */
    private void publishApplicationSubmitted(ApplicationSubmittedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEventSafely(event);
                }
            });
        } else {
            // 没有事务（例如被类内部无事务方法调用、或单元测试）时直接发送
            sendEventSafely(event);
        }
    }

    /**
     * 真正发送消息。
     *
     * 重点：在 afterCommit 阶段事务已经提交，这里抛出的任何异常都回滚不了数据，
     * 只会把一次已经成功的投递变成前端的“服务器异常”，因此必须全部捕获并记录，
     * 等待后续补偿（TODO 本地消息表 + 定时任务重发）。
     */
    private void sendEventSafely(ApplicationSubmittedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE,
                    MqConstants.ROUTING_KEY_APPLICATION_SUBMITTED,
                    event,
                    message -> {
                        message.getMessageProperties().setMessageId(event.getMessageId());
                        // 持久化：Broker 重启后消息不丢失
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
        } catch (Exception e) {
            log.error("投递事件发送失败, applicationId={}, messageId={}, 该消息需要补偿重发",
                    event.getApplicationId(), event.getMessageId(), e);
        }
    }

    /**
     * 发送投递状态变更事件，逻辑与 publishApplicationSubmitted 一致：事务提交后才真正发出。
     */
    private void publishApplicationStatusChanged(ApplicationStatusChangedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendStatusEventSafely(event);
                }
            });
        } else {
            // 没有事务时直接发送
            sendStatusEventSafely(event);
        }
    }

    /**
     * 真正发送状态变更消息：在 afterCommit 阶段事务已提交，这里抛出的任何异常
     * 都回滚不了数据，因此必须全部捕获并记录，等待后续补偿。
     */
    private void sendStatusEventSafely(ApplicationStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE,
                    MqConstants.ROUTING_KEY_APPLICATION_STATUS_CHANGED,
                    event,
                    message -> {
                        message.getMessageProperties().setMessageId(event.getMessageId());
                        // 持久化：Broker 重启后消息不丢失
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
        } catch (Exception e) {
            log.error("投递状态变更事件发送失败, applicationId={}, messageId={}, 该消息需要补偿重发",
                    event.getApplicationId(), event.getMessageId(), e);
        }
    }

    /**
     * 平台投递统计（API 文档 4.14 内部接口）。
     *
     * 实现要点：
     * 1. 内部接口仅供 user-service Feign 调用，不做登录态校验（与 3.13 职位统计内部接口一致）
     * 2. days 仅支持 7/30/90，缺省 7，非法值报错而非静默取默认，避免拼错参数时拿错区间
     * 3. 统计口径：不含已逻辑删除记录；按 create_time 落到自然日，统计规则同文档 2.8 节
     * 4. 区间内无投递的日期补 0，日期升序返回，调用方无需自行补齐
     */
    @Override
    public ApplicationStatisticsVO getApplicationStatistics(Integer days) {
        // 1. days 校验：仅支持 7/30/90，缺省 7
        int n = (days == null) ? STAT_DAYS_DEFAULT : days;
        if (n != 7 && n != 30 && n != 90) {
            throw new BusinessException("days 仅支持 7、30、90");
        }

        // 统计缓存(Cache Aside):内部接口被 user-service 管理端聚合调用,60 秒自然过期即可
        String statsKey = KEY_STATS + n;
        String cached = redisUtil.get(statsKey);
        if (cached != null) {
            return JsonUtils.jsonToObject(cached, ApplicationStatisticsVO.class);
        }

        // 2. 未逻辑删除的投递总数
        long applicationTotal = adminApplicationMapper.countTotalApplications();

        // 3. 按天聚合统计区间内的每日新增投递（按 create_time，含起点当日 0 点）
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        LocalDate startDate = today.minusDays(n - 1);
        LocalDateTime start = startDate.atStartOfDay();

        Map<String, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : adminApplicationMapper.countDailyNewApplications(start)) {
            String date = row.get("date") == null ? null : row.get("date").toString();
            long count = row.get("count") == null ? 0L : ((Number) row.get("count")).longValue();
            if (date != null) {
                countMap.put(date, count);
            }
        }

        // 4. 日期升序，无数据的日期补 0
        List<ApplicationStatisticsVO.DayCount> dailyNewApplications = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String date = startDate.plusDays(i).toString();
            dailyNewApplications.add(new ApplicationStatisticsVO.DayCount(date, countMap.getOrDefault(date, 0L)));
        }

        ApplicationStatisticsVO vo = new ApplicationStatisticsVO();
        vo.setApplicationTotal(applicationTotal);
        vo.setDailyNewApplications(dailyNewApplications);
        redisUtil.set(statsKey, JsonUtils.toJson(vo), Duration.ofSeconds(STATS_TTL_SECONDS));
        return vo;
    }
}
