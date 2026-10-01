package com.hire.job.task;

import com.hire.common.utils.RedisUtil;
import com.hire.job.mapper.JobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 浏览量定时回刷任务:每 5 分钟把 Redis 里的浏览计数增量批量写回 MySQL 并删除计数键,
 * 保证 Redis 只是"计数缓冲区",数据库始终是最终一致的数据源。
 *
 * 设计说明:
 * - 用 KEYS 扫描(项目数据量小;生产大数据量应改 SCAN 游标);
 * - 回刷与删除之间若并发新增计数,极端情况下会丢失个别增量,属可接受的最终一致;
 * - Redis 不可用时 keys() 返回空集合,任务空转,下次继续。
 */
@Slf4j
@Component
public class ViewCountFlushTask {

    /** 浏览量计数键前缀,与 JobServiceImpl 保持一致 */
    private static final String KEY_JOB_VIEW = "job:view:";
    /** 回刷周期(毫秒):5 分钟 */
    private static final long FLUSH_INTERVAL_MS = 300_000L;

    private final JobMapper jobMapper;
    private final RedisUtil redisUtil;

    public ViewCountFlushTask(JobMapper jobMapper, RedisUtil redisUtil) {
        this.jobMapper = jobMapper;
        this.redisUtil = redisUtil;
    }

    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS, initialDelay = 60_000L)
    public void flushViewCount() {
        for (String key : redisUtil.keys(KEY_JOB_VIEW + "*")) {
            try {
                Long id = Long.valueOf(key.substring(KEY_JOB_VIEW.length()));
                Long delta = redisUtil.getLong(key);
                redisUtil.delete(key);
                if (delta != null && delta > 0) {
                    jobMapper.addViewCount(id, delta);
                }
            } catch (Exception e) {
                log.warn("浏览量回刷失败,key={},原因:{}", key, e.getMessage());
            }
        }
    }
}
