package com.neu.easypam.notify.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.notify.entity.Notification;
import com.neu.easypam.notify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "通知管理")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "获取通知列表")
    @GetMapping("/list")
    public Result<List<Notification>> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(notificationService.listByUserId(userId, limit));
    }

    @Operation(summary = "获取未读数量")
    @GetMapping("/unread/count")
    public Result<Long> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(notificationService.countUnread(userId));
    }

    @Operation(summary = "标记为已读")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @Operation(summary = "全部标记已读")
    @PutMapping("/read/all")
    public Result<Void> markAllAsRead(@RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        notificationService.deleteNotification(id, userId);
        return Result.success();
    }
}
