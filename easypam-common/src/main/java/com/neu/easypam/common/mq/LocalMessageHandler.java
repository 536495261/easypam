package com.neu.easypam.common.mq;

/**
 * 本地消息处理接口
 * 各服务实现此接口，用于消息发送失败时的本地存储
 */
public interface LocalMessageHandler {

    /**
     * 保存失败消息到本地
     * @param topic 消息主题
     * @param messageBody 消息内容JSON
     * @param hashKey 顺序消息的hashKey（可为null）
     */
    void saveFailedMessage(String topic, String messageBody, String hashKey);

    /**
     * 标记消息发送成功
     */
    void markSuccess(String messageId);

    /**
     * 标记消息重试
     */
    void markRetry(String messageId, String errorMsg);
}
