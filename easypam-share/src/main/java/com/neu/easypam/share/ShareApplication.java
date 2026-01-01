package com.neu.easypam.share;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.neu.easypam")
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.neu.easypam.share.mapper")
public class ShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShareApplication.class, args);
    }
}
