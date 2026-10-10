package org.example.ragdemo.controller;

import org.example.ragdemo.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RagController {
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** 问答接口：/rag/ask?question=宿舍几点关门 */
    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam("question") String question) {
        return ragService.ask(question);
    }
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestParam String question) {
        return ragService.askStream(question);
    }

}
