package com.brain.knowledge.embedding;

import java.io.IOException;

public interface EmbeddingClient {
    EmbeddingResponse embed(EmbeddingRequest request) throws IOException;
}
