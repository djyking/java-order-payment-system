package com.acme.order.gateway;

import java.util.Set;
import java.util.Map;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Gateway 订单路由的 Sentinel 限流规则。
 *
 * @author heyu
 * @since 2026-09-02
 */
@Configuration
public class GatewaySentinelConfiguration {

    @PostConstruct
    void loadRules() {
        GatewayFlowRule orderRule = new GatewayFlowRule("order")
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
            .setGrade(1)
            .setCount(50)
            .setIntervalSec(1);
        GatewayRuleManager.loadRules(Set.of(orderRule));
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    /**
     * Sentinel 1.8.x 的默认 WebFlux handler 针对旧版 Spring 编译。这里使用整数状态码构造响应，
     * 避免 Spring Framework 6.2 移除 status(HttpStatus) 后在限流路径触发 NoSuchMethodError。
     */
    @Bean
    BlockRequestHandler sentinelBlockRequestHandler() {
        return (exchange, cause) -> ServerResponse.status(429)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "code", "SYSTEM_BUSY",
                "message", "请求过于频繁，请稍后重试",
                "data", "",
                "traceId", exchange.getRequest().getId()));
    }
}
