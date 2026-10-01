package com.hire.job.service;

import com.hire.model.dto.JobReviewDTO;
import com.hire.model.dto.JobSaveDTO;
import com.hire.model.vo.JobDetailVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.JobReviewVO;
import com.hire.model.vo.JobStatisticsVO;
import com.hire.model.vo.JobVO;
import com.hire.model.vo.PageVO;

/**
 * 职位业务接口(职位CRUD + 审核)
 */
public interface JobService {

    /** 发布职位(初始待审核),返回新职位ID，对应 API 3.1 */
    Long publishJob(JobSaveDTO dto);

    /** 编辑职位(招聘中/审核拒绝的职位编辑后重新进入待审核)，对应 API 3.2 */
    void updateJob(Long id, JobSaveDTO dto);

    /** 上下架:0-下架(仅招聘中) / 1-申请上架(企业下架或审核拒绝→待审核)，对应 API 3.3 */
    void changeStatus(Long id, Integer status);

    /** 查询我的职位列表(企业)，对应 API 3.4 */
    PageVO<JobVO> listMyJobs(Integer page, Integer size, Integer status, String keyword);

    /** 公开职位详情(仅招聘中可见,递增浏览量)，对应 API 3.5 */
    JobDetailVO getJobDetail(Long id);

    /** 管理员查询所有职位(含全部状态)，对应 API 3.8 */
    PageVO<JobVO> listJobsForAdmin(Integer page, Integer size, Integer status, String city,
                                   Long employerId, String keyword);

    /** 管理员查看职位详情(含非公开状态,不递增浏览量)，对应 API 3.9 */
    JobDetailVO getJobDetailForAdmin(Long id);

    /** 管理员强制下架职位(仅招聘中)，对应 API 3.10 */
    void offlineJobByAdmin(Long id);

    /** 管理员审核职位(approve/reject)，对应 API 3.11 */
    JobReviewVO reviewJob(Long id, JobReviewDTO dto);

    /** 内部接口:根据ID获取职位信息(不存在返回 null)，对应 API 3.12 */
    JobInfoVO getJobInfo(Long id);

    /** 投递数递增，由 application-service 投递成功后远程调用(文档 4.7 流程) */
    void increaseApplyCount(Long id);

    /** 内部接口:平台职位统计，对应 API 3.13 */
    JobStatisticsVO getJobStatistics(Integer days);

    /** 全量同步职位到 Elasticsearch(索引重建后恢复数据用)，返回同步条数 */
    int syncAllJobsToEs();
}
