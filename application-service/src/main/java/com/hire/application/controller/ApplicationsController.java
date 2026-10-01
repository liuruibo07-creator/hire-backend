package com.hire.application.controller;

import com.hire.application.service.ApplicationsService;
import com.hire.common.domain.Result;
import com.hire.model.dto.ApplicationStatusUpdateDTO;
import com.hire.model.dto.ApplicationSubmitDTO;
import com.hire.model.vo.ApplicationDetailVO;
import com.hire.model.vo.ApplicationStatisticsVO;
import com.hire.model.vo.MyApplicationVO;
import com.hire.model.vo.PageResult;
import com.hire.model.vo.ReceivedApplicationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Applications controller, API doc 4.7-4.11.
 * Gateway route /api/application/** strips /api/application (StripPrefix=2),
 * so frontend /api/application/applications maps to /applications here.
 */
@RestController
@RequestMapping("/applications")
public class ApplicationsController {

    @Autowired
    private ApplicationsService applicationsService;

    @PostMapping
    public Result<Long> submit(@RequestBody ApplicationSubmitDTO dto) {
        return Result.success(applicationsService.submitApplication(dto));
    }

    @GetMapping("/my")
    public Result<PageResult<MyApplicationVO>> listMyApplications(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        return Result.success(applicationsService.listMyApplications(page, size, status));
    }

    @GetMapping("/received")
    public Result<PageResult<ReceivedApplicationVO>> listReceivedApplications(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Integer status) {
        return Result.success(applicationsService.listReceivedApplications(page, size, jobId, status));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody ApplicationStatusUpdateDTO dto) {
        applicationsService.updateApplicationStatus(id, dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ApplicationDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(applicationsService.getApplicationDetail(id));
    }

    /**
     * 平台投递统计（外部访问路径 GET /application/applications/statistics/internal，对应 API 文档 4.14）
     * 内部接口：仅供 user-service 通过 Feign 调用，不对前端开放，无登录态校验（同 3.13 内部统计接口）。
     *
     * @param days 统计天数，仅支持 7/30/90，缺省 7（统计区间与补零规则同文档 2.8 节）
     * @return applicationTotal（未逻辑删除的投递总数）+ dailyNewApplications（按 create_time 统计的每日新增，日期升序、无数据补 0）
     */
    @GetMapping("/statistics/internal")
    public Result<ApplicationStatisticsVO> getStatisticsInternal(
            @RequestParam(value = "days", required = false) Integer days) {
        return Result.success(applicationsService.getApplicationStatistics(days));
    }

}
