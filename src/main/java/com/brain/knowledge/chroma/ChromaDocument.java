package com.brain.knowledge.chroma;

public record ChromaDocument(
        String id,
        ChromaCollection collection,
        String content,
        ChromaMetadata metadata,
        java.util.List<Double> embedding) {
}
