package com.hire.job.service;

import com.hire.model.dto.JobSearchDTO;
import com.hire.model.vo.JobSearchItemVO;
import com.hire.model.vo.PageVO;

/**
 * Elasticsearch 职位搜索接口
 */
public interface JobSearchService {

    /**
     * 职位全文搜索:关键词(IK分词) + 多条件筛选 + 高亮 + 排序 + 分页
     */
    PageVO<JobSearchItemVO> search(JobSearchDTO dto);
}