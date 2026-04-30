package com.brain.knowledge.runtime;

import com.brain.knowledge.chroma.ChromaConfig;
import com.brain.knowledge.embedding.EmbeddingConfig;
import com.brain.knowledge.store.StoreMode;

import java.nio.file.Path;

public final class StoreConfigurationLoader {
    public StoreConfiguration load(Path projectRoot) {
        return new StoreConfiguration(
                StoreMode.fromConfig(setting("PROJECT_BRAIN_STORE_MODE", "project.brain.store.mode")),
                new ChromaConfig(
                        setting("PROJECT_BRAIN_CHROMA_URL", "project.brain.chroma.url"),
                        setting("PROJECT_BRAIN_CHROMA_NAMESPACE", "project.brain.chroma.namespace"),
                        intSetting("PROJECT_BRAIN_CHROMA_TIMEOUT_MS",
                                "project.brain.chroma.timeout-ms",
                                ChromaConfig.DEFAULT_TIMEOUT_MS)),
                new EmbeddingConfig(
                        setting("PROJECT_BRAIN_EMBEDDING_PROVIDER", "project.brain.embedding.provider"),
                        setting("PROJECT_BRAIN_EMBEDDING_URL", "project.brain.embedding.url"),
                        setting("PROJECT_BRAIN_EMBEDDING_MODEL", "project.brain.embedding.model"),
                        setting("PROJECT_BRAIN_EMBEDDING_API_KEY", "project.brain.embedding.api-key"),
                        intSetting("PROJECT_BRAIN_EMBEDDING_TIMEOUT_MS",
                                "project.brain.embedding.timeout-ms",
                                EmbeddingConfig.DEFAULT_TIMEOUT_MS)));
    }

    private String setting(String envName, String propertyName) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    private int intSetting(String envName, String propertyName, int defaultValue) {
        String value = setting(envName, propertyName);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
