package com.neu.easypam.search.mq;

import com.neu.easypam.common.mq.FileIndexMessage;
import com.neu.easypam.search.document.FileDocument;
import com.neu.easypam.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 监听文件变更消息，同步到 ES（支持幂等和重试）
 * 
 * 幂等性：ES 的 index 操作天然幂等（相同ID会覆盖）
 * 顺序性：同一文件的操作需要顺序执行，使用 ORDERLY 模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "file-index-topic",
        consumerGroup = "search-index-consumer",
        consumeMode = ConsumeMode.ORDERLY,  // 顺序消费，保证同一文件的操作顺序
        maxReconsumeTimes = 3,
        consumeThreadNumber = 20  // 增加消费线程数（默认20，可根据需要调整）
)
public class FileIndexConsumer implements RocketMQListener<FileIndexMessage> {

    private final SearchService searchService;

    @Override
    public void onMessage(FileIndexMessage message) {
        Long fileId = message.getFileId();
        String type = message.getType();
        log.info("收到文件索引消息：type={}, fileId={}", type, fileId);
        
        try {
            switch (type) {
                case "CREATE":
                case "UPDATE":
                    // ES index 操作天然幂等，相同ID会覆盖
                    FileDocument doc = convertToDocument(message);
                    searchService.indexFile(doc);
                    log.info("文件索引{}成功：fileId={}", type, fileId);
                    break;
                case "DELETE":
                    // 删除操作幂等，不存在也不报错
                    searchService.deleteIndex(fileId);
                    log.info("文件索引删除成功：fileId={}", fileId);
                    break;
                default:
                    log.warn("未知消息类型：{}", type);
            }
        } catch (Exception e) {
            log.error("处理文件索引消息失败，将进行重试：fileId={}", fileId, e);
            throw new RuntimeException("文件索引处理失败: " + e.getMessage(), e);
        }
    }

    private FileDocument convertToDocument(FileIndexMessage message) {
        FileDocument doc = new FileDocument();
        doc.setId(message.getFileId());
        doc.setUserId(message.getUserId());
        doc.setParentId(message.getParentId());
        doc.setFileName(message.getFileName());
        doc.setFileType(message.getFileType());
        doc.setContentType(message.getContentType());
        doc.setFileSize(message.getFileSize());
        doc.setIsFolder(message.getIsFolder());
        doc.setCreateTime(message.getCreateTime());
        doc.setUpdateTime(message.getUpdateTime());
        return doc;
    }
}
