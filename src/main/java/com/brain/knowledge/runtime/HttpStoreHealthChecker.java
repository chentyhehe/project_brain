package com.brain.knowledge.runtime;

import com.brain.knowledge.chroma.ChromaConfig;
import com.brain.knowledge.embedding.EmbeddingConfig;
import com.brain.knowledge.store.DegradeReason;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpStoreHealthChecker implements StoreHealthChecker {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public HealthCheckResult checkChroma(ChromaConfig config) {
        try {
            HttpRequest request = HttpRequest.newBuilder(chromaHeartbeatUri(config.url()))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2 && response.body() != null
                    && response.body().toLowerCase().contains("heartbeat")) {
                return HealthCheckResult.healthy("chroma heartbeat ok");
            }
            return HealthCheckResult.degraded(
                    DegradeReason.CHROMA_UNHEALTHY,
                    "chroma heartbeat unexpected status=" + response.statusCode());
        } catch (java.net.http.HttpTimeoutException exception) {
            return HealthCheckResult.degraded(DegradeReason.NETWORK_TIMEOUT, exception.getMessage());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return HealthCheckResult.degraded(DegradeReason.CHROMA_UNHEALTHY, exception.getMessage());
        }
    }

    @Override
    public HealthCheckResult checkEmbedding(EmbeddingConfig config) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(embeddingUri(config))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(embeddingPayload(config)));
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.apiKey().trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2 && response.body() != null
                    && response.body().toLowerCase().contains("embedding")) {
                return HealthCheckResult.healthy("embedding healthcheck ok");
            }
            return HealthCheckResult.degraded(
                    DegradeReason.EMBEDDING_UNHEALTHY,
                    "embedding probe unexpected status=" + response.statusCode());
        } catch (java.net.http.HttpTimeoutException exception) {
            return HealthCheckResult.degraded(DegradeReason.NETWORK_TIMEOUT, exception.getMessage());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return HealthCheckResult.degraded(DegradeReason.EMBEDDING_UNHEALTHY, exception.getMessage());
        }
    }

    private URI chromaHeartbeatUri(String configuredUrl) {
        String url = trimTrailingSlash(configuredUrl);
        if (url.endsWith("/api/v2/heartbeat")) {
            return URI.create(url);
        }
        if (url.endsWith("/api/v2")) {
            return URI.create(url + "/heartbeat");
        }
        return URI.create(url + "/api/v2/heartbeat");
    }

    private URI embeddingUri(EmbeddingConfig config) {
        String url = trimTrailingSlash(config.url());
        String provider = config.provider() == null ? "" : config.provider().trim().toLowerCase();
        if ("ollama".equals(provider)) {
            if (url.endsWith("/api/embed")) {
                return URI.create(url);
            }
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

    private String embeddingPayload(EmbeddingConfig config) {
        String model = json(config.model());
        if ("ollama".equalsIgnoreCase(config.provider())) {
            return "{\"model\":\"" + model + "\",\"input\":\"healthcheck\"}";
        }
        return "{\"model\":\"" + model + "\",\"input\":[\"healthcheck\"]}";
    }

    private String json(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
