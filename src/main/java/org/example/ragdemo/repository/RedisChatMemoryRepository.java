package org.example.ragdemo.repository;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class RedisChatMemoryRepository implements ChatMemoryRepository {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    public RedisChatMemoryRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }
    @Override
    public List<String> findConversationIds() {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match("chat:memory:*").count(100).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys.stream().map(s -> s.replace("chat:memory:", "")).toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {   // 读某会话消息
        String json = stringRedisTemplate.opsForValue().get("chat:memory:"+conversationId);
        if (json != null) {
            try {
                List<Map<String, String>> result = objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                return result.stream().map(r -> "USER".equals(r.get("type"))
                        ?(Message) new UserMessage(r.get("content"))
                        : new AssistantMessage(r.get("content"))).toList();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return List.of();
    }
    @Override
    public void saveAll(String conversationId, List<Message> messages) { // 全量写某会话消息
        try {
            List<Map<String, String>> result = messages
                    .stream()
                    .map(s ->
                            Map.of("type", s.getMessageType().name(), "content", s.getText())).toList();
            stringRedisTemplate.opsForValue().set("chat:memory:"+conversationId, objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void deleteByConversationId(String conversationId) {          // 删某会话
        try {
            stringRedisTemplate.delete("chat:memory:"+conversationId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
