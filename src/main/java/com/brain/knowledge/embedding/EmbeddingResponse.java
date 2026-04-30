package com.brain.knowledge.embedding;

import java.util.List;

public record EmbeddingResponse(String model, List<List<Double>> vectors) {
}
