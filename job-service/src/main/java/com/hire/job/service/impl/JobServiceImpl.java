package com.hire.job.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hire.api.clients.UserClient;
import com.hire.common.context.UserContext;
import com.hire.common.utils.JsonUtils;
import com.hire.common.utils.RedisUtil;
import com.hire.common.utils.SnowflakeIdWorker;
import com.hire.job.constant.JobConstants;
import com.hire.job.exception.BusinessException;
import com.hire.job.mapper.JobMapper;
import com.hire.job.service.JobCategoryService;
import com.hire.job.service.JobEsService;
import com.hire.job.service.JobService;
import com.hire.model.dto.JobReviewDTO;
import com.hire.model.dto.JobSaveDTO;
import com.hire.model.entity.Job;
import com.hire.model.vo.JobDetailVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.JobReviewVO;
import com.hire.model.vo.JobStatisticsVO;
import com.hire.model.vo.JobVO;
import com.hire.model.vo.PageVO;
import com.hire.model.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 职位业务实现：职位 CRUD、审核、统计。
 * ES 同步失败只记日志不影响主流程(索引可后续重建)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** Redis 键前缀:职位基本信息缓存(job:info:{id}) */
    private static final String KEY_JOB_INFO = "job:info:";
    /** Redis 键前缀:职位浏览量计数(job:view:{id}) */
    private static final String KEY_JOB_VIEW = "job:view:";
    /** Redis 键前缀:平台职位统计缓存(job:stats:{days}) */
    private static final String KEY_JOB_STATS = "job:stats:";
    /** 职位基本信息缓存 TTL:10 分钟+随机抖动 */
    private static final long JOB_INFO_TTL_MINUTES = 10;
    /** 统计类缓存 TTL:60 秒(管理端可接受分钟级延迟) */
    private static final long STATS_TTL_SECONDS = 60;

    private final JobMapper jobMapper;
    private final JobCategoryService jobCategoryService;
    private final JobEsService jobEsService;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final UserClient userClient;
    private final RedisUtil redisUtil;

    // ==================== 企业侧 ====================

    @Override
    public Long publishJob(JobSaveDTO dto) {
        Long employerId = currentEmployerId();
        checkRequired(dto);

        Job job = new Job();
        job.setId(snowflakeIdWorker.nextId());
        job.setEmployerId(employerId);
        copyFromDto(job, dto);
        job.setStatus(JobConstants.STATUS_PENDING);   // 初始待审核,审核通过前不写 ES
        job.setViewCount(0);
        job.setApplyCount(0);
        job.setDeleted(0);
        job.setCreateTime(LocalDateTime.now());
        jobMapper.insert(job);
        return job.getId();
    }

    @Override
    public void updateJob(Long id, JobSaveDTO dto) {
        checkRequired(dto);
        Job job = getOwnedJob(id);

        Integer oldStatus = job.getStatus();
        // 管理员下架的职位不允许企业编辑
        if (oldStatus != null && oldStatus == JobConstants.STATUS_ADMIN_OFFLINE) {
            throw new BusinessException(403, "该职位已被管理员下架,不允许编辑");
        }
        // 招聘中/审核拒绝/待审核的职位编辑后重新进入待审核;企业下架保持下架
        Integer newStatus = oldStatus;
        if (oldStatus == JobConstants.STATUS_ONLINE
                || oldStatus == JobConstants.STATUS_REJECTED
                || oldStatus == JobConstants.STATUS_PENDING) {
            newStatus = JobConstants.STATUS_PENDING;
        }

        Job update = new Job();
        update.setId(id);
        copyFromDto(update, dto);
        update.setStatus(newStatus);
        jobMapper.update(update);
        // 职位内容/状态已变化,删除基本信息缓存,避免 Feign 调用方拿到旧数据
        redisUtil.delete(KEY_JOB_INFO + id);
        if (newStatus == JobConstants.STATUS_PENDING) {
            jobMapper.clearReview(id);
        }
        // 原招聘中的旧版本从 ES 移除,避免搜索到过期内容
        if (oldStatus != null && oldStatus == JobConstants.STATUS_ONLINE) {
            deleteFromEsQuietly(id);
        }
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        Job job = getOwnedJob(id);
        Integer current = job.getStatus();
        if (status == null || (status != JobConstants.STATUS_OFFLINE && status != JobConstants.STATUS_ONLINE)) {
            throw new BusinessException(400, "状态值不合法,仅支持 0-下架 / 1-申请上架");
        }
        if (status == JobConstants.STATUS_OFFLINE) {
            // 仅招聘中可下架
            if (current == null || current != JobConstants.STATUS_ONLINE) {
                throw new BusinessException(409, "仅招聘中的职位可以下架");
            }
            jobMapper.updateStatus(id, JobConstants.STATUS_OFFLINE);
            deleteFromEsQuietly(id);
        } else {
            // 申请上架:仅企业下架/审核拒绝可申请,进入待审核
            if (current == null || (current != JobConstants.STATUS_OFFLINE && current != JobConstants.STATUS_REJECTED)) {
                throw new BusinessException(409, "当前状态不能申请上架");
            }
            jobMapper.updateStatus(id, JobConstants.STATUS_PENDING);
            jobMapper.clearReview(id);
        }
        // 上下架两个分支都会改变职位状态,统一删除基本信息缓存
        redisUtil.delete(KEY_JOB_INFO + id);
    }

    @Override
    public PageVO<JobVO> listMyJobs(Integer page, Integer size, Integer status, String keyword) {
        Long employerId = currentEmployerId();
        int p = normalizePage(page);
        int s = normalizeSize(size);
        long total = jobMapper.countByEmployer(employerId, status, keyword);
        List<Job> jobs = jobMapper.selectPageByEmployer((p - 1) * s, s, employerId, status, keyword);
        List<JobVO> list = new ArrayList<>();
        for (Job job : jobs) {
            JobVO vo = new JobVO();
            BeanUtil.copyProperties(job, vo);
            vo.setCategoryName(getCategoryNameQuietly(job.getCategoryId()));
            list.add(vo);
        }
        return new PageVO<>(total, list);
    }

    @Override
    public JobDetailVO getJobDetail(Long id) {
        Job job = requireJob(id);
        // 公开详情仅招聘中可见
        if (job.getStatus() == null || job.getStatus() != JobConstants.STATUS_ONLINE) {
            throw new BusinessException(404, "职位不存在或已下架");
        }
        incrementViewCountToRedis(job);
        return buildDetailVO(job);
    }

    // ==================== 管理员侧 ====================

    @Override
    public PageVO<JobVO> listJobsForAdmin(Integer page, Integer size, Integer status, String city,
                                          Long employerId, String keyword) {
        currentAdminId();
        int p = normalizePage(page);
        int s = normalizeSize(size);
        long total = jobMapper.countForAdmin(status, city, employerId, keyword);
        List<Job> jobs = jobMapper.selectPageForAdmin((p - 1) * s, s, status, city, employerId, keyword);
        List<JobVO> list = new ArrayList<>();
        for (Job job : jobs) {
            JobVO vo = new JobVO();
            BeanUtil.copyProperties(job, vo);
            vo.setEmployerName(getEmployerNameQuietly(job.getEmployerId()));
            vo.setCategoryName(getCategoryNameQuietly(job.getCategoryId()));
            list.add(vo);
        }
        return new PageVO<>(total, list);
    }

    @Override
    public JobDetailVO getJobDetailForAdmin(Long id) {
        currentAdminId();
        Job job = requireJob(id);
        return buildDetailVO(job);
    }

    @Override
    public void offlineJobByAdmin(Long id) {
        currentAdminId();
        Job job = requireJob(id);
        Integer current = job.getStatus();
        // 已管理员下架:幂等,重复请求保持 status=2
        if (current != null && current == JobConstants.STATUS_ADMIN_OFFLINE) {
            return;
        }
        if (current == null || current != JobConstants.STATUS_ONLINE) {
            throw new BusinessException(409, "仅招聘中的职位可以强制下架");
        }
        jobMapper.updateStatus(id, JobConstants.STATUS_ADMIN_OFFLINE);
        redisUtil.delete(KEY_JOB_INFO + id);
        deleteFromEsQuietly(id);
    }

    @Override
    public JobReviewVO reviewJob(Long id, JobReviewDTO dto) {
        Long adminId = currentAdminId();
        if (dto == null || StrUtil.isBlank(dto.getDecision())) {
            throw new BusinessException(400, "审核决定不能为空");
        }
        boolean approve = JobConstants.REVIEW_APPROVE.equals(dto.getDecision());
        boolean reject = JobConstants.REVIEW_REJECT.equals(dto.getDecision());
        if (!approve && !reject) {
            throw new BusinessException(400, "审核决定仅支持 approve / reject");
        }
        if (reject && StrUtil.isBlank(dto.getRemark())) {
            throw new BusinessException(400, "审核拒绝时必须填写审核意见");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 500) {
            throw new BusinessException(400, "审核意见最长500字");
        }

        Job job = requireJob(id);
        if (job.getStatus() == null || job.getStatus() != JobConstants.STATUS_PENDING) {
            throw new BusinessException(409, "仅待审核的职位可以审核");
        }

        int newStatus = approve ? JobConstants.STATUS_ONLINE : JobConstants.STATUS_REJECTED;
        LocalDateTime now = LocalDateTime.now();
        jobMapper.updateWithReview(id, newStatus, dto.getRemark(), adminId, now);
        // 审核结果改变职位状态(招聘中/已拒绝),删除基本信息缓存
        redisUtil.delete(KEY_JOB_INFO + id);

        // 审核通过同步到 ES(失败仅记日志,不影响审核结果)。注意先更新内存中的状态,避免把待审核(3)写入索引
        if (approve) {
            job.setStatus(JobConstants.STATUS_ONLINE);
            indexToEsQuietly(job);
        }

        JobReviewVO vo = new JobReviewVO();
        vo.setId(id);
        vo.setStatus(newStatus);
        vo.setReviewRemark(dto.getRemark());
        vo.setReviewedBy(adminId);
        vo.setReviewedAt(now);
        return vo;
    }

    // ==================== 内部接口 ====================

    @Override
    public JobInfoVO getJobInfo(Long id) {
        if (id == null) {
            return null;
        }
        return getJobInfoCached(id);
    }

    /**
     * 职位基本信息缓存(Cache Aside),供 application-service Feign 调用:
     * - 缓存命中直接返回;空串""=明确不存在,避免"职位不存在"每次都穿透到数据库;
     * - 未命中用 setnx 互斥锁防击穿:拿锁的请求回源回填,其余短暂等待后重试,超时兜底直查;
     * - TTL 10 分钟+随机抖动防雪崩;Redis 异常时 RedisUtil 已降级,自动走数据库。
     */
    private JobInfoVO getJobInfoCached(Long id) {
        String key = KEY_JOB_INFO + id;
        String cached = redisUtil.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : JsonUtils.jsonToObject(cached, JobInfoVO.class);
        }
        String lockKey = key + ":lock";
        for (int i = 0; i < 3; i++) {
            if (redisUtil.setIfAbsent(lockKey, "1", Duration.ofSeconds(5))) {
                try {
                    JobInfoVO info = jobMapper.selectInfoById(id);
                    redisUtil.set(key, info == null ? "" : JsonUtils.toJson(info),
                            Duration.ofMinutes(JOB_INFO_TTL_MINUTES + RandomUtil.randomInt(0, 3)));
                    return info;
                } finally {
                    redisUtil.delete(lockKey);
                }
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            cached = redisUtil.get(key);
            if (cached != null) {
                return cached.isEmpty() ? null : JsonUtils.jsonToObject(cached, JobInfoVO.class);
            }
        }
        // 锁竞争超时:兜底直查数据库,不写缓存,下次请求再回填
        return jobMapper.selectInfoById(id);
    }

    /**
     * 投递数自增。被 application-service 全局事务（Seata AT）远程调用：
     * XID 由 Feign 拦截器透传过来，本方法作为分支事务注册到 TC，
     * 本地回滚时 Seata 依据 undo_log 反向补偿；本地异常也会向 TC 汇报触发全局回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseApplyCount(Long id) {
        int rows = jobMapper.increaseApplyCount(id);
        if (rows == 0) {
            // 职位不存在则无需计数，抛异常让调用方感知，避免"投递成功但计数没增加"的数据不一致被掩盖
            throw new BusinessException(404, "职位不存在或已删除，投递数更新失败");
        }
    }

    @Override
    public JobStatisticsVO getJobStatistics(Integer days) {
        int n = (days == null) ? JobConstants.STAT_DAYS_DEFAULT : days;
        if (n != 7 && n != 30 && n != 90) {
            throw new BusinessException(400, "days 仅支持 7、30、90");
        }
        String statsKey = KEY_JOB_STATS + n;
        String cached = redisUtil.get(statsKey);
        if (cached != null) {
            return JsonUtils.jsonToObject(cached, JobStatisticsVO.class);
        }
        long jobTotal = jobMapper.countTotal();

        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        LocalDate startDate = today.minusDays(n - 1);
        LocalDateTime start = startDate.atStartOfDay();

        Map<String, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : jobMapper.countDailyNew(start)) {
            String date = row.get("date") == null ? null : row.get("date").toString();
            long count = row.get("count") == null ? 0L : ((Number) row.get("count")).longValue();
            if (date != null) {
                countMap.put(date, count);
            }
        }

        // 日期升序,无数据的日期补0
        List<JobStatisticsVO.DayCount> dailyNewJobs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String date = startDate.plusDays(i).toString();
            dailyNewJobs.add(new JobStatisticsVO.DayCount(date, countMap.getOrDefault(date, 0L)));
        }

        JobStatisticsVO vo = new JobStatisticsVO();
        vo.setJobTotal(jobTotal);
        vo.setDailyNewJobs(dailyNewJobs);
        redisUtil.set(statsKey, JsonUtils.toJson(vo), Duration.ofSeconds(STATS_TTL_SECONDS));
        return vo;
    }

    // ==================== 私有方法 ====================

    /** 校验已登录,返回当前用户ID */
    private Long currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录,请先登录");
        }
        return userId;
    }

    /** 校验企业角色,返回当前企业ID */
    private Long currentEmployerId() {
        Long userId = currentUserId();
        if (!JobConstants.ROLE_EMPLOYER.equals(UserContext.getCurrentRole())) {
            throw new BusinessException(403, "仅企业用户可以操作");
        }
        return userId;
    }

    /** 校验管理员角色,返回当前管理员ID */
    private Long currentAdminId() {
        Long userId = currentUserId();
        if (!JobConstants.ROLE_ADMIN.equals(UserContext.getCurrentRole())) {
            throw new BusinessException(403, "仅管理员可以操作");
        }
        return userId;
    }

    /** 校验职位存在且属于当前登录企业 */
    private Job getOwnedJob(Long id) {
        Job job = requireJob(id);
        Long userId = currentEmployerId();
        if (!job.getEmployerId().equals(userId)) {
            throw new BusinessException(403, "无权限操作该职位");
        }
        return job;
    }

    /** 查询职位,不存在或已删除返回 404 */
    private Job requireJob(Long id) {
        if (id == null) {
            throw new BusinessException(404, "职位不存在或已删除");
        }
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(404, "职位不存在或已删除");
        }
        return job;
    }

    /** 发布/编辑参数必填校验 */
    private void checkRequired(JobSaveDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getTitle())) {
            throw new BusinessException(400, "职位名称不能为空");
        }
        if (StrUtil.isBlank(dto.getJobType())) {
            throw new BusinessException(400, "职位类型不能为空");
        }
        if (StrUtil.isBlank(dto.getCity())) {
            throw new BusinessException(400, "工作城市不能为空");
        }
    }

    /** DTO 字段拷贝到实体(默认值兜底) */
    private void copyFromDto(Job job, JobSaveDTO dto) {
        job.setTitle(dto.getTitle());
        job.setCategoryId(dto.getCategoryId());
        job.setDescription(dto.getDescription());
        job.setJobType(dto.getJobType());
        job.setIndustry(dto.getIndustry());
        job.setCity(dto.getCity());
        job.setAddress(dto.getAddress());
        job.setExperienceReq(StrUtil.blankToDefault(dto.getExperienceReq(), "不限"));
        job.setEducationReq(StrUtil.blankToDefault(dto.getEducationReq(), "不限"));
        job.setSalaryMin(dto.getSalaryMin());
        job.setSalaryMax(dto.getSalaryMax());
        job.setSalaryMonths(dto.getSalaryMonths() == null ? 12 : dto.getSalaryMonths());
        job.setSkills(dto.getSkills());
        job.setHeadcount(dto.getHeadcount() == null ? 1 : dto.getHeadcount());
    }

    /**
     * 浏览量计数:Redis INCR 原子自增,替代"每次详情请求 UPDATE 一次数据库",
     * 由 ViewCountFlushTask 定时批量回刷 MySQL;Redis 不可用时降级回直改数据库。
     */
    private void incrementViewCountToRedis(Job job) {
        Long total = redisUtil.increment(KEY_JOB_VIEW + job.getId());
        if (total == null) {
            // Redis 不可用:回退为直接改库,保证浏览数不丢(展示侧少计1属降级误差,可接受)
            jobMapper.increaseViewCount(job.getId());
        } else if (total == 1L) {
            // 该职位第一次计数,设置过期兜底(正常由定时任务回刷后删除)
            redisUtil.expire(KEY_JOB_VIEW + job.getId(), Duration.ofDays(7));
        }
    }

    /** 组装详情VO(企业名称Feign获取,失败降级) */
    private JobDetailVO buildDetailVO(Job job) {
        JobDetailVO vo = new JobDetailVO();
        BeanUtil.copyProperties(job, vo);
        vo.setEmployerName(getEmployerNameQuietly(job.getEmployerId()));
        vo.setCategoryName(getCategoryNameQuietly(job.getCategoryId()));
        // 浏览数 = 数据库基线 + Redis 未回刷增量(管理员详情共用,不额外计数)
        Long redisDelta = redisUtil.getLong(KEY_JOB_VIEW + job.getId());
        long base = job.getViewCount() == null ? 0L : job.getViewCount();
        vo.setViewCount((int) (base + (redisDelta == null ? 0L : redisDelta)));
        return vo;
    }

    /** 企业名称:Feign 调 user-service 2.9,失败降级返回 null */
    private String getEmployerNameQuietly(Long employerId) {
        if (employerId == null) {
            return null;
        }
        try {
            com.hire.common.domain.Result<UserInfoVO> response = userClient.getUserInfo(employerId);
            return response == null || response.getData() == null ? null : response.getData().getRealName();
        } catch (Exception e) {
            log.warn("获取企业名称失败,employerId={},原因:{}", employerId, e.getMessage());
            return null;
        }
    }

    /** 类别名称:读分类缓存(JobCategoryServiceImpl 统一回源),不存在返回 null */
    private String getCategoryNameQuietly(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        try {
            return jobCategoryService.getCategoryName(categoryId);
        } catch (Exception e) {
            log.warn("获取类别名称失败,categoryId={},原因:{}", categoryId, e.getMessage());
            return null;
        }
    }

    /** ES 写入,失败仅记日志 */
    private void indexToEsQuietly(Job job) {
        try {
            jobEsService.indexJob(job,
                    getEmployerNameQuietly(job.getEmployerId()),
                    getCategoryNameQuietly(job.getCategoryId()));
        } catch (Exception e) {
            log.warn("职位同步ES失败,id={},原因:{}", job.getId(), e.getMessage());
        }
    }

    /** ES 删除,失败仅记日志 */
    private void deleteFromEsQuietly(Long id) {
        try {
            jobEsService.deleteJob(id);
        } catch (Exception e) {
            log.warn("职位从ES移除失败,id={},原因:{}", id, e.getMessage());
        }
    }

    @Override
    public int syncAllJobsToEs() {
        List<Job> jobs = jobMapper.selectAllForSync();
        for (Job job : jobs) {
            indexToEsQuietly(job);
        }
        log.info("职位全量同步ES完成,共{}条", jobs.size());
        return jobs.size();
    }

    private int normalizePage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        return (size == null || size < 1) ? 10 : size;
    }
}
