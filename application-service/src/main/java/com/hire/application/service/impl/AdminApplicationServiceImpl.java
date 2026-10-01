package com.hire.application.service.impl;

import com.hire.api.clients.JobClient;
import com.hire.application.mapper.AdminApplicationMapper;
import com.hire.application.mapper.ResumeMapper;
import com.hire.application.service.AdminApplicationService;
import com.hire.common.Result;
import com.hire.common.context.UserContext;
import com.hire.common.exception.BusinessException;
import com.hire.common.utils.JsonUtils;
import com.hire.common.utils.RedisUtil;
import com.hire.model.dto.StatusCountDTO;
import com.hire.model.entity.Applications;
import com.hire.model.entity.Resume;
import com.hire.model.vo.AdminApplicationVO;
import com.hire.model.vo.JobApplicationStatisticsVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员投递统计 Service 实现。
 *
 * 实现要点：
 * 1. 仅管理员角色可调用，role 头缺失时也不放行，避免网关未透传时误开放
 * 2. 职位存在性校验通过 Feign 调 job-service：远程失败抛异常，职位不存在报错（对应文档要求的 404 语义）
 * 3. 统计聚合在本库一条 GROUP BY status 完成，某状态无记录时兜底为 0
 *
 * 该方法为纯读操作（统计查询 + Feign 只读调用），无需事务管理；
 * 若职位远程校验抛出 BusinessException，由 GlobalExceptionHandler 统一捕获返回错误提示。
 */
@Slf4j
@Service
public class AdminApplicationServiceImpl implements AdminApplicationService {

    /** 日期参数格式：YYYY-MM-DD，严格解析（拒绝 2026/09/28、2026-9-1 等写法） */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 每页条数上限，防止恶意大分页拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    /** Redis 键前缀:管理员投递统计缓存(app:admin:stats:{jobId}) */
    private static final String KEY_ADMIN_STATS = "app:admin:stats:";
    /** 统计缓存 TTL:60 秒(管理端可接受分钟级延迟) */
    private static final long STATS_TTL_SECONDS = 60;

    @Autowired
    private AdminApplicationMapper adminApplicationMapper;

    @Autowired
    private JobClient jobClient;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public JobApplicationStatisticsVO getJobApplicationStatistics(Long jobId) {
        // 1. 登录与角色校验：仅管理员
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }
        String role = UserContext.getCurrentRole();
        if (role == null || !"admin".equals(role)) {
            throw new BusinessException("仅管理员可查看投递统计");
        }
        if (jobId == null) {
            throw new BusinessException("职位ID不能为空");
        }

        // 统计缓存(Cache Aside):管理端频繁打开同一职位的统计,60 秒自然过期即可;
        // 缓存命中时跳过本库 GROUP BY 与 Feign 职位校验
        String statsKey = KEY_ADMIN_STATS + jobId;
        String cached = redisUtil.get(statsKey);
        if (cached != null) {
            return JsonUtils.jsonToObject(cached, JobApplicationStatisticsVO.class);
        }

        // 2. 职位存在性校验：远程失败抛异常，job 为空表示职位不存在或已逻辑删除
        JobInfoVO job = queryJobInfo(jobId);
        if (job == null) {
            throw new BusinessException("职位不存在");
        }

        // 3. 本库按状态分组聚合
        List<StatusCountDTO> counts = adminApplicationMapper.countByJobGroupByStatus(jobId);
        long pending = 0, viewed = 0, interview = 0, offered = 0, rejected = 0;
        if (counts != null) {
            for (StatusCountDTO item : counts) {
                if (item == null || item.getStatus() == null || item.getTotal() == null) {
                    continue;
                }
                switch (item.getStatus()) {
                    case 0:
                        pending = item.getTotal();
                        break;
                    case 1:
                        viewed = item.getTotal();
                        break;
                    case 2:
                        interview = item.getTotal();
                        break;
                    case 3:
                        offered = item.getTotal();
                        break;
                    case 4:
                        rejected = item.getTotal();
                        break;
                    default:
                        // 未知状态忽略，避免统计被污染
                        break;
                }
            }
        }

        // 4. 组装结果
        JobApplicationStatisticsVO vo = JobApplicationStatisticsVO.builder()
                .jobId(jobId)
                .jobTitle(job.getTitle())
                .applicationTotal(pending + viewed + interview + offered + rejected)
                .pendingCount(pending)
                .viewedCount(viewed)
                .interviewCount(interview)
                .offeredCount(offered)
                .rejectedCount(rejected)
                .build();
        redisUtil.set(statsKey, JsonUtils.toJson(vo), Duration.ofSeconds(STATS_TTL_SECONDS));
        return vo;
    }

    /**
     * Feign 查询职位信息，统一处理返回值为空或调用失败的情况。
     * 远程调用失败时抛出 BusinessException，由全局异常处理器返回明确错误提示。
     */
    private JobInfoVO queryJobInfo(Long jobId) {
        Result<JobInfoVO> result = jobClient.getJobInfo(jobId);
        // 先判断调用是否成功：若 job-service 内部报错，必须把真实原因暴露出来，
        // 否则会被后面的 data 判空掩盖成"职位不存在"，难以排查
        if (result == null || !Integer.valueOf(1).equals(result.getCode())) {
            throw new BusinessException("职位信息查询失败：" + (result == null ? "远程调用无响应" : result.getMsg()));
        }
        return result.getData();
    }

    /**
     * 管理员分页查询全平台投递记录（API 文档 4.13）。
     *
     * 实现要点：
     * 1. 仅管理员角色可调用，role 头缺失时也不放行，避免网关未透传时误开放
     * 2. status 限定 0~4，日期格式限定 YYYY-MM-DD，非法参数直接报错而非静默忽略
     * 3. endDate 转为“次日 0 点”的半开区间上界，保证“含结束当日”且边界无歧义
     * 4. 职位信息（Feign）与求职者姓名（本库 t_resume）均带请求内缓存，同一职位/简历只查一次
     * 5. 远程查询失败不影响列表主流程：职位信息置空并记录日志，前端展示降级
     */
    @Override
    public PageResult<AdminApplicationVO> listAllApplications(Integer page, Integer size,
                                                              Long jobId, Long employerId, Long userId,
                                                              Integer status, String startDate, String endDate) {
        // 1. 登录与角色校验：仅管理员
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("用户未登录，请先登录");
        }
        String role = UserContext.getCurrentRole();
        if (role == null || !"admin".equals(role)) {
            throw new BusinessException("仅管理员可查询全平台投递记录");
        }

        // 2. 分页参数兜底：页码最小为 1，每页条数默认 10、上限 100
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, MAX_PAGE_SIZE);
        int offset = (pageNum - 1) * pageSize;

        // 3. status 校验：传入时必须是 0~4，非法值报错（避免拼错参数时静默返回全量）
        if (status != null && (status < 0 || status > 4)) {
            throw new BusinessException("非法的投递状态：" + status + "，取值范围 0~4");
        }

        // 4. 日期参数解析：严格 YYYY-MM-DD；endDate 转为次日 0 点（半开区间，含结束当日）
        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (startDate != null && !startDate.trim().isEmpty()) {
            startTime = parseDate(startDate.trim(), "startDate").atStartOfDay();
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            // 上界取次日 0 点、左闭右开：create_time < 次日0点 即“含 endDate 当天任意时刻”
            endTime = parseDate(endDate.trim(), "endDate").plusDays(1).atStartOfDay();
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException("startDate 不能晚于 endDate");
        }

        // 5. 先查总数，为 0 时直接返回空页，不再执行列表查询
        long total = adminApplicationMapper.countAdminApplications(
                jobId, employerId, userId, status, startTime, endTime);
        List<AdminApplicationVO> list = new ArrayList<>();
        if (total > 0) {
            List<Applications> records = adminApplicationMapper.selectAdminPage(
                    jobId, employerId, userId, status, startTime, endTime, offset, pageSize);
            Map<Long, JobInfoVO> jobCache = new HashMap<>();
            Map<Long, Resume> resumeCache = new HashMap<>();
            for (Applications record : records) {
                list.add(toAdminApplicationVO(record, jobCache, resumeCache));
            }
        }
        return PageResult.<AdminApplicationVO>builder()
                .total(total)
                .page(pageNum)
                .size(pageSize)
                .list(list)
                .build();
    }

    /**
     * 投递记录 -> 管理员列表 VO：补充职位基本信息（Feign）与求职者姓名/简历标题（本库查询），均带请求内缓存。
     * 管理员查询全平台数据，不限定简历归属用户；简历可能已被求职者删除，此时姓名与标题为 null。
     */
    private AdminApplicationVO toAdminApplicationVO(Applications record,
                                                    Map<Long, JobInfoVO> jobCache,
                                                    Map<Long, Resume> resumeCache) {
        // 职位基本信息：同一 jobId 只远程查一次，失败时静默降级为 null
        JobInfoVO job = jobCache.computeIfAbsent(record.getJobId(), this::queryJobInfoQuietly);

        // 简历信息：同一 resumeId 只查一次库（管理员视角，不限简历归属用户）
        Resume resume = record.getResumeId() == null ? null
                : resumeCache.computeIfAbsent(record.getResumeId(), id -> resumeMapper.selectByIdOnly(id));

        return AdminApplicationVO.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .applicantName(resume == null ? null : resume.getName())
                .jobId(record.getJobId())
                .jobTitle(job == null ? null : job.getTitle())
                .jobCity(job == null ? null : job.getCity())
                .employerId(record.getEmployerId())
                .resumeId(record.getResumeId())
                .resumeTitle(resume == null ? null : resume.getTitle())
                .status(record.getStatus())
                .coverLetter(record.getCoverLetter())
                .remark(record.getRemark())
                .createTime(record.getCreateTime())
                .build();
    }

    /**
     * 严格解析 YYYY-MM-DD 日期参数，格式非法时报出带参数名的明确错误。
     */
    private LocalDate parseDate(String value, String paramName) {
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException("参数 " + paramName + " 格式非法：" + value + "，应为 YYYY-MM-DD");
        }
    }

    /**
     * 查询职位信息但不抛异常：列表场景下远程失败不应让整个接口 500
     */
    private JobInfoVO queryJobInfoQuietly(Long jobId) {
        try {
            Result<JobInfoVO> result = jobClient.getJobInfo(jobId);
            if (result == null || !Integer.valueOf(1).equals(result.getCode())) {
                log.warn("管理员投递列表获取职位信息失败, jobId={}, msg={}",
                        jobId, result == null ? "远程调用无响应" : result.getMsg());
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.error("管理员投递列表远程查询职位信息异常, jobId={}", jobId, e);
            return null;
        }
    }
}
