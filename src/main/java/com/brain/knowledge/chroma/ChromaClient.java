package com.brain.knowledge.chroma;

import java.io.IOException;
import java.util.List;

public interface ChromaClient {
    List<String> upsert(List<ChromaDocument> documents) throws IOException;

    List<ChromaDocument> query(ChromaQuery query) throws IOException;
}
