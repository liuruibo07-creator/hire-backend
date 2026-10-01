package com.hire.job.config;

import com.hire.job.constant.JobConstants;
import com.hire.job.service.JobEsService;
import com.hire.job.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化 Elasticsearch 职位索引
 * 索引不存在则按 IK 分词映射自动创建,并从数据库全量同步职位数据;失败不影响服务启动
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobIndexInitializer implements ApplicationRunner {

    private final JobEsService jobEsService;
    private final JobService jobService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!jobEsService.indexExists()) {
                jobEsService.createIndexIfAbsent();
                log.info("Elasticsearch 索引 {} 创建成功,开始全量同步职位数据", JobConstants.ES_INDEX);
                int count = jobService.syncAllJobsToEs();
                log.info("Elasticsearch 索引 {} 全量同步完成,共{}条", JobConstants.ES_INDEX, count);
            } else {
                log.info("Elasticsearch 索引 {} 已存在,跳过创建", JobConstants.ES_INDEX);
            }
        } catch (Exception e) {
            log.warn("初始化 Elasticsearch 索引失败,服务启动不受影响:{}", e.getMessage());
        }
    }
}