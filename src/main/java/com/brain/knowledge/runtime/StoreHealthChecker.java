package com.brain.knowledge.runtime;

import com.brain.knowledge.chroma.ChromaConfig;
import com.brain.knowledge.embedding.EmbeddingConfig;

public interface StoreHealthChecker {
    HealthCheckResult checkChroma(ChromaConfig config);

    HealthCheckResult checkEmbedding(EmbeddingConfig config);
}
