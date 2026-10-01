package com.hire.gateway.Listener;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class RouteConfigListener {
    //Nacos配置管理器,通过它可以获取ConfigService对象
    @Autowired
    private NacosConfigManager nacosConfigManager;
    //路由定义写入器,通过它可以将RouteDefinition写入内存
    @Autowired
    private RouteDefinitionWriter writer;
    //路由json解析器(Jackson比hutool的JSONUtil.toList更可靠,且RouteDefinition可反序列化)
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    //路由id集合
    private List<String> routeIds = new ArrayList<>();
    /*
     * 路由配置监听器
     */
    @PostConstruct //在对象实例化之后执行,执行一次,用来做一些初始化操作
    public void routeListener() throws NacosException {
        log.info("routeListener start");
        //1.获取路由配置信息
        ConfigService configService = nacosConfigManager.getConfigService();
        String configInfoJson = configService.getConfig("gateway-routes.json", "DEFAULT_GROUP", 3000);
        //2.更新内存中路由信息
        //2.1将获取到的路由json数组转化弄成RouteDefinition集合
        addRouteInfo(configInfoJson);
        //3.添加监听器
        configService.addListener("gateway-routes.json", "DEFAULT_GROUP", new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            //configInfo形参是nacos路由修改之后的整个信息 先删再插
            @Override
            public void receiveConfigInfo(String configInfo) {
                //1.删除所有旧的路由信息
                for (String routeId : routeIds) {
                    writer.delete(Mono.just(routeId)).subscribe();
                }
                //2.清空路由id集合
                routeIds.clear();
                //3.插入新的路由信息
                addRouteInfo(configInfo);
            }
        });
    }

    private void addRouteInfo(String configInfo) {
        //配置不存在或为空时不抛异常,保证网关可以正常启动,等配置发布后再由监听器刷新
        if (!StringUtils.hasText(configInfo)) {
            log.warn("gateway-routes.json 配置为空,网关将以无动态路由模式启动");
            return;
        }
        try {
            //将路由json数组反序列化成RouteDefinition集合
            List<RouteDefinition> routeDefinitions =
                    objectMapper.readValue(configInfo, new TypeReference<List<RouteDefinition>>() {});
            //遍历路由信息,通过save方法依次添加到内存路由表中
            for (RouteDefinition routeDefinition : routeDefinitions) {
                String routeId = routeDefinition.getId();
                routeIds.add(routeId);
                writer.save(Mono.just(routeDefinition)).subscribe();
            }
        } catch (Exception e) {
            log.error("解析 gateway-routes.json 失败,请检查Nacos中的路由配置是否为合法的JSON数组: {}", e.getMessage(), e);
        }
    }
}
