package org.example.ragdemo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 声明配置：
 * 消息路由：生产者发到交换机(rag.exchange) → 按 routingKey(pdf.upload) → 投递到队列(rag.pdf.queue)
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "rag.exchange";
    public static final String QUEUE = "rag.pdf.queue";
    public static final String ROUTING_KEY = "pdf.upload";

    /** 直连交换机：根据 routingKey 精确路由 */
    @Bean
    public DirectExchange ragExchange() {
        return new DirectExchange(EXCHANGE);
    }

    /** 队列：true = 持久化（RabbitMQ 重启后队列还在） */
    @Bean
    public Queue pdfQueue() {
        return new Queue(QUEUE, true);
    }

    /** 绑定：把队列绑到交换机，路由键 pdf.upload */
    @Bean
    public Binding pdfBinding(DirectExchange ragExchange, Queue pdfQueue) {
        return BindingBuilder.bind(pdfQueue).to(ragExchange).with(ROUTING_KEY);
    }
}
