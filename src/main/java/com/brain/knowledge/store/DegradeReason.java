package com.brain.knowledge.store;

public enum DegradeReason {
    NONE,
    MISSING_CHROMA_CONFIG,
    MISSING_EMBEDDING_CONFIG,
    CHROMA_UNHEALTHY,
    EMBEDDING_UNHEALTHY,
    NETWORK_TIMEOUT
}
