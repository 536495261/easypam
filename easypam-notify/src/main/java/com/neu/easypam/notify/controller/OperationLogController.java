package com.neu.easypam.notify.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.notify.entity.OperationLog;
import com.neu.easypam.notify.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "查询操作日志")
    @GetMapping("/list")
    public Result<IPage<OperationLog>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(operationLogService.listByUserId(userId, page, size));
    }
}
