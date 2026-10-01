package com.hire.application.controller;

import com.hire.application.service.AdminApplicationService;
import com.hire.common.domain.Result;
import com.hire.model.vo.AdminApplicationVO;
import com.hire.model.vo.JobApplicationStatisticsVO;
import com.hire.model.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin application statistics controller, API doc 4.12.
 * Gateway route /api/application/** strips /api/application (StripPrefix=2),
 * so frontend /api/application/admin/jobs/{jobId}/statistics maps to /admin/jobs/{jobId}/statistics here.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminApplicationService adminApplicationService;

    @GetMapping("/jobs/{jobId}/statistics")
    public Result<JobApplicationStatisticsVO> getJobStatistics(@PathVariable Long jobId) {
        return Result.success(adminApplicationService.getJobApplicationStatistics(jobId));
    }

    /**
     * 管理员分页查询全平台投递记录（对应 API 文档 4.13）
     * 仅管理员角色可调用；所有过滤参数均为可选。
     * 登录/角色/参数合法性校验均在 AdminApplicationService.listAllApplications 内完成。
     *
     * @param page      页码，默认 1
     * @param size      每页条数，默认 10
     * @param jobId     按职位ID筛选，可空
     * @param employerId 按企业用户ID筛选，可空
     * @param userId    按求职者用户ID筛选，可空
     * @param status    投递状态 0~4，可空
     * @param startDate 投递起始日期 YYYY-MM-DD（含当日），可空
     * @param endDate   投递结束日期 YYYY-MM-DD（含当日），可空
     * @return 分页的投递记录列表
     */
    @GetMapping("/applications")
    public Result<PageResult<AdminApplicationVO>> listAllApplications(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long employerId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(adminApplicationService.listAllApplications(
                page, size, jobId, employerId, userId, status, startDate, endDate));
    }
}
