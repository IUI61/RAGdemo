package org.example.ragdemo.service.Impl;

import org.example.ragdemo.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements RagService {
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagServiceImpl(VectorStore vectorStore, ChatClient.Builder builder) {
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
    }
    public Map<String, Object> ask(String question) {
        // 第 1 步：向量检索，找最相关的 5 块
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(5).build());

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

        // 返回答案 + 用到的来源列表（前端可以展示"引用了哪几页"）
        return Map.of(
                "question", question,
                "answer", answer,
                "sources", docs.stream()
                        .map(d -> Map.of("file", d.getMetadata().getOrDefault("file_name", "未知"),
                                "page", d.getMetadata().getOrDefault("page_number", "?")))
                        .toList()
        );
    }
}
