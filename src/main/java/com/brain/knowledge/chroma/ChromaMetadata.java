package com.brain.knowledge.chroma;

public record ChromaMetadata(
        String projectPath,
        String projectName,
        String module,
        String stage,
        String taskSessionId,
        String sourceFile,
        String backupFile,
        String contentHash,
        boolean stable,
        boolean compacted,
        String createdAt) {
}
