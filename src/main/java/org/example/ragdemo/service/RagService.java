package org.example.ragdemo.service;

import reactor.core.publisher.Flux;
import java.util.Map;

public interface RagService {
    Map<String, Object> ask(String question);
    Flux<String> askStream(String question);
}
