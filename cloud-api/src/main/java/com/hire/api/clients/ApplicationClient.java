package com.hire.api.clients;

import com.hire.common.Result;
import com.hire.model.vo.ApplicationChatContextVO;
import com.hire.model.vo.ApplicationStatisticsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 远程调用 application-service。
 *
 * 注意：网关路由 /application/** 没有配置 StripPrefix，
 * Feign 直连服务（不走网关）时路径与 Controller 的 RequestMapping 保持一致。
 */
@FeignClient(value = "application-service")
public interface ApplicationClient {

    /**
     * 平台投递统计（供 user-service 2.8 Feign 调用），对应 API 4.14。
     * days 仅支持 7/30/90，缺省 7；统计区间与补零规则同文档 2.8 节。
     */
    @GetMapping("/application/applications/statistics/internal")
    Result<ApplicationStatisticsVO> getApplicationStatistics(
            @RequestParam(value = "days", required = false) Integer days);

    /**
     * 聊天场景:企业查询投递的聊天关系(chat-service 建聊校验用)。
     * 需要 Authorization 头,application-service 会校验企业身份与投递归属。
     */
    @GetMapping("/applications/{id}/chat-context/internal")
    com.hire.common.domain.Result<ApplicationChatContextVO> getChatContext(
            @PathVariable("id") Long id, @RequestHeader("Authorization") String authorization);
}
