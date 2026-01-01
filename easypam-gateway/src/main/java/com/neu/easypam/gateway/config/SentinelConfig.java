package com.neu.easypam.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sentinel限流配置
 * API分组和限流规则全部从Nacos动态加载
 */
@Slf4j
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void init() {
        initBlockHandler();
        log.info("Sentinel配置初始化完成，规则从Nacos加载");
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
