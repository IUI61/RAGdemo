package org.example.ragdemo.controller;

import org.example.ragdemo.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/document")
public class DocumentController {

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate springRedisTemplate;

    public DocumentController(RabbitTemplate rabbitTemplate, StringRedisTemplate springRedisTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.springRedisTemplate = springRedisTemplate;
    }

    /**
     * 上传 PDF：只做校验 + 发消息，立即返回。
     * 真正的解析/向量化/入库由 PdfUploadConsumer 在后台异步完成。
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("只支持 PDF 文件");
        }
        // 发消息：消息体 = PDF 字节，文件名放消息头 fileName
        String taskId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY,
                file.getBytes(),
                message -> {
                    message.getMessageProperties().setHeader("fileName", name);
                    message.getMessageProperties().setHeader("taskId", taskId);
                    return message;
                });
        springRedisTemplate.opsForValue().set("task:status:" + taskId, "PROCESSING", Duration.ofMinutes(5));
        return Map.of("fileName", name, "message", "已提交入库任务，后台处理中", "taskId", taskId);
    }
    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam("taskId") String taskId) {
        try {
            String status = springRedisTemplate.opsForValue().get("task:status:" + taskId);
            return Map.of("taskId", taskId, "status", status != null ? status : "NOT_FOUND");
        } catch (Exception e) {
            return Map.of("status", "FAILED");
        }
    }
}
