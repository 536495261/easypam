package com.neu.easypam.search.mq;

import com.neu.easypam.common.mq.FileIndexMessage;
import com.neu.easypam.search.document.FileDocument;
import com.neu.easypam.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 监听文件变更消息，同步到 ES
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "file-index-topic",
        consumerGroup = "search-index-consumer"
)
public class FileIndexConsumer implements RocketMQListener<FileIndexMessage> {

    private final SearchService searchService;

    @Override
    public void onMessage(FileIndexMessage message) {
        log.info("收到文件索引消息：type={}, fileId={}", message.getType(), message.getFileId());
        
        try {
            switch (message.getType()) {
                case "CREATE":
                case "UPDATE":
                    FileDocument doc = convertToDocument(message);
                    searchService.indexFile(doc);
                    break;
                case "DELETE":
                    searchService.deleteIndex(message.getFileId());
                    break;
                default:
                    log.warn("未知消息类型：{}", message.getType());
            }
        } catch (Exception e) {
            log.error("处理文件索引消息失败", e);
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
