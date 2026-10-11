package org.example.ragdemo.config;

import org.example.ragdemo.repository.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(15)
                .build();
        // 用上面的 builder 链，返回 MessageWindowChatMemory
        return chatMemory;
    }
    @Bean
    public MessageChatMemoryAdvisor chatMemoryAdvisor(ChatMemory chatMemory) {
        MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor
                .builder(chatMemory)
                .build();
        return advisor;
    }
}
