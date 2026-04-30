package com.brain.knowledge.chroma;

public enum ChromaCollection {
    KNOWLEDGE_EVENTS("pb_knowledge_events"),
    KNOWLEDGE_SUMMARIES("pb_knowledge_summaries"),
    TASK_CONTEXT_SUMMARIES("pb_task_context_summaries"),
    COMPACTION_RECORDS("pb_compaction_records");

    private final String collectionName;

    ChromaCollection(String collectionName) {
        this.collectionName = collectionName;
    }

    public String collectionName() {
        return collectionName;
    }
}
