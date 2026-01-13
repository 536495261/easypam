package com.neu.easypam.file.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.file.entity.LocalMessage;

import java.util.List;

public interface LocalMessageService extends IService<LocalMessage> {

    /**
     * 保存待发送消息
     */
    void saveMessage(String topic, String messageBody, String hashKey);

    /**
     * 标记消息发送成功
     */
    void markSuccess(String messageId);

    /**
     * 标记消息发送失败，设置下次重试时间
     */
    void markRetry(String messageId, String errorMsg);

    /**
     * 获取待重试的消息
     */
    List<LocalMessage> getPendingMessages(int limit);

    /**
     * 尝试锁定消息（乐观锁，防止并发处理）
     * @return true 表示锁定成功，可以处理
     */
    boolean tryLock(String messageId);
}
