package com.brain.knowledge.runtime;

import com.brain.knowledge.store.DegradeReason;
import com.brain.knowledge.store.StoreMode;
import com.brain.knowledge.store.StoreStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class DefaultKnowledgeRuntimeCoordinator implements KnowledgeRuntimeCoordinator {
    private final StoreConfigurationLoader configurationLoader = new StoreConfigurationLoader();
    private final StoreHealthChecker healthChecker;
    private final Duration cacheTtl;
    private volatile CacheEntry chromaCache;
    private volatile CacheEntry embeddingCache;

    public DefaultKnowledgeRuntimeCoordinator() {
        this(new HttpStoreHealthChecker(), Duration.ofSeconds(60));
    }

    DefaultKnowledgeRuntimeCoordinator(StoreHealthChecker healthChecker, Duration cacheTtl) {
        this.healthChecker = healthChecker;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public RuntimeResolution resolve(Path projectRoot) {
        return resolve(projectRoot, false);
    }

    public RuntimeResolution resolveForWrite(Path projectRoot) {
        return resolve(projectRoot, true);
    }

    RuntimeResolution resolve(Path projectRoot, boolean forceRefresh) {
        StoreConfiguration configuration = configurationLoader.load(projectRoot);
        if (configuration.requestedMode() == StoreMode.LOCAL_MD) {
            return new RuntimeResolution(StoreMode.LOCAL_MD, StoreStatus.AVAILABLE, DegradeReason.NONE, false, true);
        }
        if (!configuration.chroma().configured()) {
            return degraded(DegradeReason.MISSING_CHROMA_CONFIG);
        }
        if (!configuration.embedding().configured()) {
            return degraded(DegradeReason.MISSING_EMBEDDING_CONFIG);
        }
        HealthCheckResult chromaHealth = checkChroma(configuration, forceRefresh);
        if (!chromaHealth.healthy()) {
            return degraded(chromaHealth.degradeReason());
        }
        HealthCheckResult embeddingHealth = checkEmbedding(configuration, forceRefresh);
        if (!embeddingHealth.healthy()) {
            return degraded(embeddingHealth.degradeReason());
        }
        return new RuntimeResolution(StoreMode.HYBRID_CHROMA, StoreStatus.AVAILABLE, DegradeReason.NONE, true, true);
    }

    private RuntimeResolution degraded(DegradeReason reason) {
        return new RuntimeResolution(StoreMode.LOCAL_MD, StoreStatus.DEGRADED, reason, false, true);
    }

    private HealthCheckResult checkChroma(StoreConfiguration configuration, boolean forceRefresh) {
        String cacheKey = "chroma|" + configuration.chroma().url() + "|" + configuration.chroma().timeoutMs();
        CacheEntry current = chromaCache;
        if (!forceRefresh && current != null && current.matches(cacheKey, cacheTtl)) {
            return current.result();
        }
        HealthCheckResult result = healthChecker.checkChroma(configuration.chroma());
        chromaCache = new CacheEntry(cacheKey, result, Instant.now());
        return result;
    }

    private HealthCheckResult checkEmbedding(StoreConfiguration configuration, boolean forceRefresh) {
        String cacheKey = "embedding|" + configuration.embedding().provider()
                + "|" + configuration.embedding().url()
                + "|" + configuration.embedding().model()
                + "|" + configuration.embedding().timeoutMs();
        CacheEntry current = embeddingCache;
        if (!forceRefresh && current != null && current.matches(cacheKey, cacheTtl)) {
            return current.result();
        }
        HealthCheckResult result = healthChecker.checkEmbedding(configuration.embedding());
        embeddingCache = new CacheEntry(cacheKey, result, Instant.now());
        return result;
    }

    private record CacheEntry(String key, HealthCheckResult result, Instant checkedAt) {
        boolean matches(String expectedKey, Duration ttl) {
            return key.equals(expectedKey) && checkedAt.plus(ttl).isAfter(Instant.now());
        }
    }
}
