package com.brain.knowledge.store;

public enum StoreMode {
    LOCAL_MD("local-md"),
    HYBRID_CHROMA("hybrid-chroma");

    private final String configValue;

    StoreMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static StoreMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL_MD;
        }
        String normalized = value.trim().toLowerCase();
        for (StoreMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        return LOCAL_MD;
    }
}
