package com.brain.knowledge.embedding;

public record EmbeddingConfig(String provider, String url, String model, String apiKey, int timeoutMs) {
    public static final int DEFAULT_TIMEOUT_MS = 3000;

    public boolean configured() {
        return hasText(provider) && hasText(url) && hasText(model);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
