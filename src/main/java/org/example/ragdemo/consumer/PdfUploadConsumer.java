package org.example.ragdemo.consumer;

import org.example.ragdemo.config.RabbitConfig;
import org.example.ragdemo.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * PDF 入库消费者：从队列 rag.pdf.queue 取任务，异步完成解析 + 向量化 + 入库
 * 上传接口只负责"发消息"，真正耗时的工作在这里后台执行，接口因此能秒回
 */
@Component
public class PdfUploadConsumer {

    private static final Logger log = LoggerFactory.getLogger(PdfUploadConsumer.class);
    private final DocumentService documentService;
    private final StringRedisTemplate stringRedisTemplate;

    public PdfUploadConsumer(DocumentService documentService, StringRedisTemplate stringRedisTemplate) {
        this.documentService = documentService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 消息体是 PDF 字节数组，文件名放在消息头 fileName 里 */
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onPdfUpload(byte[] content, @Header("fileName") String fileName, @Header("taskId") String taskId) {
        try {
            int count = documentService.processPdf(content, fileName);
            log.info("入库完成: {} 共 {} 块", fileName, count);
            stringRedisTemplate.opsForValue().set("task:status:" + taskId, "SUCCESS", Duration.ofMinutes(5));
        } catch (Exception e) {
            // 失败先记录日志（后续可扩展死信队列做重试）
            log.error("入库失败: {}", fileName, e);
            stringRedisTemplate.opsForValue().set("task:status:" + taskId, "FAILED", Duration.ofMinutes(5));
        }
    }
}
