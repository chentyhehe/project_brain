package com.brain.knowledge.embedding;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpEmbeddingClient implements EmbeddingClient {
    private final EmbeddingConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final McpJsonMapper mapper = McpJsonDefaults.getMapper();

    public HttpEmbeddingClient(EmbeddingConfig config) {
        this.config = config;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint())
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(request)));
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey().trim());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Embedding request interrupted", exception);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Embedding request failed: " + response.statusCode() + " " + response.body());
        }

        return parseResponse(request.model(), response.body());
    }

    private EmbeddingResponse parseResponse(String model, String body) throws IOException {
        Map<?, ?> root = mapper.readValue(body, Map.class);
        List<List<Double>> vectors = new ArrayList<>();
        Object ollamaEmbeddings = root.get("embeddings");
        if (ollamaEmbeddings instanceof List<?> items) {
            for (Object item : items) {
                vectors.add(numberList(item));
            }
            return new EmbeddingResponse(model, List.copyOf(vectors));
        }

        Object openaiData = root.get("data");
        if (openaiData instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    vectors.add(numberList(map.get("embedding")));
                }
            }
            return new EmbeddingResponse(model, List.copyOf(vectors));
        }
        throw new IOException("Unsupported embedding response: " + body);
    }

    private List<Double> numberList(Object value) {
        List<Double> numbers = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number number) {
                    numbers.add(number.doubleValue());
                }
            }
        }
        return List.copyOf(numbers);
    }

    private URI endpoint() {
        String url = trimTrailingSlash(config.url());
        if ("ollama".equalsIgnoreCase(config.provider()) && !url.endsWith("/api/embed")) {
            if (url.endsWith("/api")) {
                return URI.create(url + "/embed");
            }
            if (url.endsWith("/v1/embeddings")) {
                return URI.create(url);
            }
            return URI.create(url + "/api/embed");
        }
        return URI.create(url);
    }

    private String payload(EmbeddingRequest request) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        if ("ollama".equalsIgnoreCase(config.provider())) {
            body.put("input", request.inputs());
        } else {
            body.put("input", request.inputs());
        }
        return mapper.writeValueAsString(body);
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
