package com.neu.easypam.common.feign;

import com.neu.easypam.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "easypam-storage", path = "/storage")
public interface StorageFeignClient {

    /**
     * 检查空间是否足够
     */
    @GetMapping("/check")
    Result<Boolean> checkSpace(@RequestHeader("X-User-Id") Long userId, @RequestParam("size") Long fileSize);

    /**
     * 校验空间（空间不足抛异常）
     */
    @PostMapping("/validate")
    Result<Void> validateSpace(@RequestHeader("X-User-Id") Long userId, @RequestParam("size") Long fileSize);

    /**
     * 增加已用空间
     */
    @PostMapping("/add-used")
    Result<Void> addUsedSpace(@RequestHeader("X-User-Id") Long userId, @RequestParam("size") Long fileSize);

    /**
     * 减少已用空间
     */
    @PostMapping("/reduce-used")
    Result<Void> reduceUsedSpace(@RequestHeader("X-User-Id") Long userId, @RequestParam("size") Long fileSize);
}
