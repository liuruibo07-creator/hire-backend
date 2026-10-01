package com.hire.user.client;
import com.hire.user.model.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="application-service", contextId="userApplicationStatistics")
public interface ApplicationStatisticsClient {
    @GetMapping("/applications/statistics/internal")
    ApiResult<JsonNode> statistics(@RequestParam("days") int days);
}
