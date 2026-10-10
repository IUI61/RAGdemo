package org.example.ragdemo.service.Impl;


import lombok.extern.slf4j.Slf4j;
import org.example.ragdemo.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagServiceImpl implements RagService {
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RagServiceImpl(VectorStore vectorStore
            , ChatClient.Builder builder
            , StringRedisTemplate stringRedisTemplate
            , ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }
    public Map<String, Object> ask(String question) {
        // 从 Redis 缓存中获取问题的答案
        String redisKey = "rag:docs:" + question;
        try {
            String docsJson = stringRedisTemplate.opsForValue().get(redisKey);
            if(docsJson != null){
                return objectMapper.readValue(docsJson, Map.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 第 1 步：向量检索，找最相关的 5 块
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(5).similarityThreshold(0.5).build());
        List<Double> scores = docs.stream().map(d -> {
            return d.getScore();
        }).collect(Collectors.toList());
        log.info("Scores: {}", scores);
        if(docs.isEmpty()) {
            return Map.of("question", question, "answer", "根据现有资料无法回答", "sources", List.of());
        }
        // 第 2 步：把检索到的内容拼成上下文，每块标注文件名和页码
        String context = docs.stream()
                .map(d -> "[来源: " + d.getMetadata().getOrDefault("file_name", "未知")
                        + " 第" + d.getMetadata().getOrDefault("page_number", "?") + "页]\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        // 第 3 步：系统提示词——只准按资料回答，没有就说不知道（这是防幻觉的关键）
        String systemPrompt = """
                你是一个校园服务问答助手。请只根据下面提供的资料回答问题。
                规则：
                1. 答案必须基于资料内容，不要编造资料里没有的信息。
                2. 如果资料里没有相关内容，直接回答"根据现有资料无法回答"。
                3. 回答时在末尾标注信息来源（文件名和页码）。

                资料：
                %s
                """.formatted(context);

        // 第 4 步：把提示词 + 问题发给 DeepSeek
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
        //写入redis
        Map<String, Object> result = Map.of(
                "question", question,
                "answer", answer,
                "sources", docs.stream()
                        .map(d -> Map.of("file", d.getMetadata().getOrDefault("file_name", "未知"),
                                "page", d.getMetadata().getOrDefault("page_number", "?")))
                        .toList()
        );
        try {
            stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 返回答案 + 用到的来源列表（前端可以展示"引用了哪几页"）
        return result;
    }
    public Flux<String> askStream(String question) {
        // 从 Redis 缓存中获取问题的答案
        String redisKey = "rag:docs:" + question;
        try {
            String docsJson = stringRedisTemplate.opsForValue().get(redisKey);
            if(docsJson != null){
                return Flux.just(objectMapper.readValue(docsJson, Map.class).get("answer").toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 第 1 步：向量检索，找最相关的 5 块
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(5).similarityThreshold(0.5).build());
        List<Double> scores = docs.stream().map(d -> {
            return d.getScore();
        }).collect(Collectors.toList());
        log.info("Scores: {}", scores);
        if(docs.isEmpty()) {
            return Flux.just("根据现有资料无法回答");
        }
        // 第 2 步：把检索到的内容拼成上下文，每块标注文件名和页码
        String context = docs.stream()
                .map(d -> "[来源: " + d.getMetadata().getOrDefault("file_name", "未知")
                        + " 第" + d.getMetadata().getOrDefault("page_number", "?") + "页]\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        // 第 3 步：系统提示词——只准按资料回答，没有就说不知道（这是防幻觉的关键）
        String systemPrompt = """
                你是一个校园服务问答助手。请只根据下面提供的资料回答问题。
                规则：
                1. 答案必须基于资料内容，不要编造资料里没有的信息。
                2. 如果资料里没有相关内容，直接回答"根据现有资料无法回答"。
                3. 回答时在末尾标注信息来源（文件名和页码）。

                资料：
                %s
                """.formatted(context);

        // 第 4 步：把提示词 + 问题发给 DeepSeek
        StringBuilder sb = new StringBuilder();
        Flux<String> answer = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content()
                .doOnNext(a -> {sb.append(a); })        // 每段到达：拼串
                .doOnComplete(() -> {Map<String, Object> result = Map.of(
                            "question", question,
                            "answer", sb.toString(),
                            "sources", docs.stream()
                                    .map(d -> Map.of("file", d.getMetadata().getOrDefault("file_name", "未知"),
                                            "page", d.getMetadata().getOrDefault("page_number", "?")))
                                    .toList()
                    );try {
                        stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(result), Duration.ofMinutes(5));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });  // 流完：写缓存
        // 返回答案 + 用到的来源列表（前端可以展示"引用了哪几页"）
        return answer;
    }
}
