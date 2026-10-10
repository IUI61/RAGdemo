package org.example.ragdemo.service;

import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

public interface RagService {
    public record StreamResult(List<Map<String, Object>> sources, Flux<String> flux) {}
    Map<String, Object> ask(String question);
    StreamResult askStream(String question);
}
