package com.hire.job.controller;

import com.hire.common.domain.Result;
import com.hire.job.service.JobService;
import com.hire.model.dto.JobReviewDTO;
import com.hire.model.vo.JobDetailVO;
import com.hire.model.vo.JobReviewVO;
import com.hire.model.vo.JobVO;
import com.hire.model.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员职位管理接口,对应 API 设计文档 3.8、3.9、3.10、3.11
 */
@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
public class AdminJobController {

    private final JobService jobService;

    /** 3.8 查询所有职位(管理员,支持按状态/城市/企业/关键词筛选,含全部状态) */
    @GetMapping
    public Result<PageVO<JobVO>> listJobsForAdmin(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                  @RequestParam(value = "size", defaultValue = "10") Integer size,
                                                  @RequestParam(value = "status", required = false) Integer status,
                                                  @RequestParam(value = "city", required = false) String city,
                                                  @RequestParam(value = "employerId", required = false) Long employerId,
                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success("查询成功", jobService.listJobsForAdmin(page, size, status, city, employerId, keyword));
    }

    /** 3.9 管理员查看职位详情(含已下架职位,不递增浏览量) */
    @GetMapping("/{id}")
    public Result<JobDetailVO> getJobDetail(@PathVariable("id") Long id) {
        return Result.success("查询成功", jobService.getJobDetailForAdmin(id));
    }

    /** 3.10 管理员强制下架违规职位(仅招聘中,状态置为2) */
    @PutMapping("/{id}/offline")
    public Result<Void> offlineJob(@PathVariable("id") Long id) {
        jobService.offlineJobByAdmin(id);
        return Result.success("职位已下架", null);
    }

    /** 3.11 管理员审核职位(仅待审核,approve/reject) */
    @PutMapping("/{id}/review")
    public Result<JobReviewVO> reviewJob(@PathVariable("id") Long id, @RequestBody JobReviewDTO dto) {
        return Result.success("审核完成", jobService.reviewJob(id, dto));
    }
}
