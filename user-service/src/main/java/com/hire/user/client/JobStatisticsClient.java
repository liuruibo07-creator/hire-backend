package com.hire.user.client;
import com.hire.user.model.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="job-service", contextId="userJobStatistics")
public interface JobStatisticsClient {
    @GetMapping("/jobs/statistics/internal")
    ApiResult<JsonNode> statistics(@RequestParam("days") int days);
}
