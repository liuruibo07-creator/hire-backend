package com.hire.api.clients;

import com.hire.common.Result;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.JobStatisticsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 远程调用 job-service。
 *
 * 注意：UserClientInterceptor 会自动把网关透传的 userId 放进请求头，
 * 因此 job-service 同样能拿到当前操作人。
 */
@FeignClient(value = "job-service")
public interface JobClient {

    /**
     * 查询职位信息（application-service 用它校验职位是否存在、是否招聘中），对应 API 3.12
     */
    @GetMapping("/jobs/{id}/info")
    Result<JobInfoVO> getJobInfo(@PathVariable("id") Long id);

    /**
     * 投递成功后递增职位的投递数，对应 API 4.7 投递流程。
     * 属于 job-service 的私有数据，不能由 application-service 直接改库。
     */
    @PostMapping("/jobs/{id}/apply-count")
    Result increaseApplyCount(@PathVariable("id") Long id);

    /**
     * 平台职位统计（user-service 2.8 内部调用），对应 API 3.13
     */
    @GetMapping("/jobs/statistics/internal")
    Result<JobStatisticsVO> getJobStatistics(@RequestParam(value = "days", required = false) Integer days);
}
