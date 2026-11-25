package com.budgetbee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Talks to a local Ollama server (free, runs on your own computer, nothing leaves it). */
@Component
public class OllamaClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final RestClient http;

    public OllamaClient(@Value("${budgetbee.ai.enabled:false}") boolean enabled,
                        @Value("${budgetbee.ai.base-url:http://localhost:11434}") String baseUrl,
                        @Value("${budgetbee.ai.model:llama3.2}") String model) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.model = model;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(40000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public boolean enabled() { return enabled; }

    public String model() { return model; }

    /** Returns the model's reply text, or null when AI is off, Ollama is down, or anything goes wrong. */
    public String chat(String system, String user, boolean json) {
        if (!enabled) return null;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("stream", false);
            body.put("options", Map.of("temperature", json ? 0 : 0.3));
            if (json) body.put("format", "json");
            body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
            String raw = http.post().uri(baseUrl + "/api/chat").body(body).retrieve().body(String.class);
            JsonNode content = MAPPER.readTree(raw).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
