package com.brain.knowledge.chroma;

public record ChromaConfig(String url, String namespace, int timeoutMs) {
    public static final String DEFAULT_NAMESPACE = "project-brain";
    public static final int DEFAULT_TIMEOUT_MS = 3000;

    public boolean configured() {
        return url != null && !url.isBlank();
    }

    public String namespaceOrDefault() {
        if (namespace == null || namespace.isBlank()) {
            return DEFAULT_NAMESPACE;
        }
        return namespace.trim();
    }
}
