package org.example.ragdemo.controller;

import org.example.ragdemo.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/document")
public class DocumentController {

    private final RabbitTemplate rabbitTemplate;

    public DocumentController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY,
                file.getBytes(),
                message -> {
                    message.getMessageProperties().setHeader("fileName", name);
                    return message;
                });
        return Map.of("fileName", name, "message", "已提交入库任务，后台处理中");
    }
}
