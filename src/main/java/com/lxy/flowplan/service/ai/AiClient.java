package com.lxy.flowplan.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

@Component
@Slf4j
public class AiClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${app.ai.model:deepseek-v4-flash}")
    private String model;

    @Value("${app.ai.timeout-seconds:20}")
    private Integer timeoutSeconds;

    public AiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PostConstruct
    public void logConfigStatus() {
        log.info("AI client config loaded: apiKeyConfigured={}, model={}, endpoint={}, timeoutSeconds={}",
                apiKey != null && !apiKey.isBlank(),
                model,
                resolveEndpoint(),
                timeoutSeconds);
    }

    public String createDraftJson(String systemPrompt, String userPrompt) {
        return createJson(systemPrompt, userPrompt, 0.2, 2400);
    }

    public String createJson(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI API key 未配置");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.putObject("response_format").put("type", "json_object");
        body.putObject("thinking").put("type", "disabled");

        ArrayNode messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", systemPrompt);
        messages.addObject()
                .put("role", "user")
                .put("content", userPrompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI API 调用失败，HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalStateException("AI API 未返回 choices");
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("AI API 返回内容为空");
            }
            return content;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI API 调用被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("AI API 调用失败：" + e.getMessage(), e);
        }
    }

    public String streamText(String systemPrompt, String userPrompt, Consumer<String> chunkConsumer) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI API key 未配置");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        body.put("temperature", 0.4);
        body.put("max_tokens", 700);
        body.putObject("thinking").put("type", "disabled");

        ArrayNode messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", systemPrompt);
        messages.addObject()
                .put("role", "user")
                .put("content", userPrompt);

        StringBuilder fullText = new StringBuilder();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI API 流式调用失败，HTTP " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String delta = extractDeltaContent(data);
                    if (delta == null || delta.isEmpty()) {
                        continue;
                    }
                    fullText.append(delta);
                    chunkConsumer.accept(delta);
                }
            }
            return fullText.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI API 流式调用被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("AI API 流式调用失败：" + e.getMessage(), e);
        }
    }

    private String extractDeltaContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode delta = choices.get(0).path("delta");
            if (delta.hasNonNull("content")) {
                return delta.path("content").asText();
            }
            if (delta.hasNonNull("reasoning_content")) {
                return delta.path("reasoning_content").asText();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveEndpoint() {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }
}
