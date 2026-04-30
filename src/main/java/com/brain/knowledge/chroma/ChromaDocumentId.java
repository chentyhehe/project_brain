package com.brain.knowledge.chroma;

import java.util.ArrayList;
import java.util.List;

public final class ChromaDocumentId {
    private ChromaDocumentId() {
    }

    public static String build(
            ChromaCollection collection,
            String taskSessionId,
            String stage,
            String contentHash) {
        List<String> parts = new ArrayList<>();
        parts.add(collection.collectionName());
        parts.add(sanitize(taskSessionId));
        parts.add(sanitize(stage));
        parts.add(sanitize(contentHash));
        return String.join("__", parts);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "na";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replace("sha256:", "");
        return normalized.replaceAll("[^a-z0-9._-]+", "-");
    }
}
