package com.neu.easypam.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        // 必须设置，否则Sentinel不会对Gateway生效
        System.setProperty("csp.sentinel.app.type", "1");
        SpringApplication.run(GatewayApplication.class, args);
    }
}
