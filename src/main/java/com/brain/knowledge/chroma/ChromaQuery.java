package com.brain.knowledge.chroma;

import java.util.Map;

public record ChromaQuery(
        ChromaCollection collection,
        String queryText,
        int limit,
        Map<String, Object> filters) {
}
