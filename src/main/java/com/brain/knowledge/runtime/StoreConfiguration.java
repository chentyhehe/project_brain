package com.brain.knowledge.runtime;

import com.brain.knowledge.chroma.ChromaConfig;
import com.brain.knowledge.embedding.EmbeddingConfig;
import com.brain.knowledge.store.StoreMode;

public record StoreConfiguration(
        StoreMode requestedMode,
        ChromaConfig chroma,
        EmbeddingConfig embedding) {
}
