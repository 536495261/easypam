package com.neu.easypam.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Sentinel限流配置
 * API分组和限流规则全部从Nacos动态加载
 */
@Slf4j
@Configuration
@EnableScheduling
public class SentinelConfig {

    @PostConstruct
    public void init() {
        initBlockHandler();
        log.info("Sentinel配置初始化完成，规则从Nacos加载");
    }

    /**
     * 定时打印当前加载的规则（调试用，生产环境可删除）
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void printRules() {
        Set<GatewayFlowRule> flowRules = GatewayRuleManager.getRules();
        Set<ApiDefinition> apiDefinitions = GatewayApiDefinitionManager.getApiDefinitions();
        log.info("当前Gateway限流规则数量: {}, API分组数量: {}", flowRules.size(), apiDefinitions.size());
        if (!flowRules.isEmpty()) {
            flowRules.forEach(rule -> log.info("限流规则: resource={}, count={}", rule.getResource(), rule.getCount()));
        }
        if (!apiDefinitions.isEmpty()) {
            apiDefinitions.forEach(api -> log.info("API分组: name={}, predicates={}", api.getApiName(), api.getPredicateItems()));
        }
    }

    /**
     * 限流返回
     */
    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            @Override
            public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable t) {
                String body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}";
                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body);
            }
        });
    }
}
