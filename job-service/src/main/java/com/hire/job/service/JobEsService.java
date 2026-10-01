package com.hire.job.service;

import com.hire.model.entity.Job;

import java.io.IOException;

/**
 * Elasticsearch 职位索引同步服务
 */
public interface JobEsService {

    /** 索引/更新职位文档 */
    void indexJob(Job job, String employerName, String categoryName) throws IOException;

    /** 从索引中删除职位文档 */
    void deleteJob(Long id) throws IOException;

    /** 索引是否存在 */
    boolean indexExists() throws IOException;

    /** 索引不存在时创建(带IK分词映射) */
    void createIndexIfAbsent() throws IOException;
}