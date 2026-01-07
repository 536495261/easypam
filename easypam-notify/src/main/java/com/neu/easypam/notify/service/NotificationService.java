package com.neu.easypam.notify.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.notify.entity.Notification;

import java.util.List;

public interface NotificationService extends IService<Notification> {

    /**
     * 获取用户通知列表
     */
    List<Notification> listByUserId(Long userId, int limit);

    /**
     * 获取未读通知数量
     */
    long countUnread(Long userId);

    /**
     * 标记为已读
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * 标记全部已读
     */
    void markAllAsRead(Long userId);

    /**
     * 删除通知
     */
    void deleteNotification(Long notificationId, Long userId);
}
