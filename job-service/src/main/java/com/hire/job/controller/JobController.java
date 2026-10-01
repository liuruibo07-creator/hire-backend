package com.hire.job.controller;

import com.hire.common.domain.Result;
import com.hire.job.service.JobSearchService;
import com.hire.job.service.JobService;
import com.hire.model.dto.JobSaveDTO;
import com.hire.model.dto.JobSearchDTO;
import com.hire.model.dto.JobStatusDTO;
import com.hire.model.vo.JobDetailVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.JobSearchItemVO;
import com.hire.model.vo.JobStatisticsVO;
import com.hire.model.vo.JobVO;
import com.hire.model.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 职位接口,对应 API 设计文档 3.1~3.6、3.12、3.13
 */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobSearchService jobSearchService;

    /** 3.1 发布职位(企业),初始待审核 */
    @PostMapping
    public Result<Long> publishJob(@RequestBody JobSaveDTO dto) {
        return Result.success("职位提交成功,等待审核", jobService.publishJob(dto));
    }

    /** 3.2 编辑职位(企业),招聘中的职位编辑后重新进入待审核 */
    @PutMapping("/{id}")
    public Result<Void> updateJob(@PathVariable("id") Long id, @RequestBody JobSaveDTO dto) {
        jobService.updateJob(id, dto);
        return Result.success("职位更新成功", null);
    }

    /** 3.3 上下架职位(企业):0-下架 / 1-申请上架(进入待审核) */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable("id") Long id, @RequestBody JobStatusDTO dto) {
        jobService.changeStatus(id, dto.getStatus());
        return Result.success();
    }

    /** 3.4 查询我的职位列表(企业) */
    @GetMapping("/my")
    public Result<PageVO<JobVO>> listMyJobs(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                            @RequestParam(value = "size", defaultValue = "10") Integer size,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success("查询成功", jobService.listMyJobs(page, size, status, keyword));
    }

    /** 3.6 职位搜索(Elasticsearch,白名单路径,仅返回审核通过且招聘中的职位) */
    @GetMapping("/search")
    public Result<PageVO<JobSearchItemVO>> searchJobs(JobSearchDTO dto) {
        return Result.success("查询成功", jobSearchService.search(dto));
    }

    /** 3.5 职位详情(所有用户,仅招聘中可见,同时递增浏览量) */
    @GetMapping("/{id}")
    public Result<JobDetailVO> getJobDetail(@PathVariable("id") Long id) {
        return Result.success("查询成功", jobService.getJobDetail(id));
    }

    /** 3.12 内部接口:根据ID获取职位信息(供 application-service Feign 调用) */
    @GetMapping("/{id}/info")
    public com.hire.common.Result<JobInfoVO> getJobInfo(@PathVariable("id") Long id) {
        return com.hire.common.Result.success(jobService.getJobInfo(id));
    }

    /** 3.13 内部接口:平台职位统计(供 user-service 2.8 Feign 调用,统一返回 code=200 的新版 Result) */
    @GetMapping("/statistics/internal")
    public Result<JobStatisticsVO> getJobStatistics(@RequestParam(value = "days", required = false) Integer days) {
        return Result.success(jobService.getJobStatistics(days));
    }

    /**
     * 内部接口:投递数递增(文档 4.7 投递流程,供 application-service Feign 调用)。
     * 注意:这里保持旧版 Result(code=1 表示成功)返回,application-service 按 code==1 判成功。
     */
    @PostMapping("/{id}/apply-count")
    public com.hire.common.Result<Void> increaseApplyCount(@PathVariable("id") Long id) {
        jobService.increaseApplyCount(id);
        return com.hire.common.Result.success();
    }
}
