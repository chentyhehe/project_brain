package com.brain.knowledge.compact;

import java.nio.file.Path;
import java.util.List;

public record CompactionRecord(
        Path sourcePath,
        String sourceFile,
        String backupFile,
        String contentHash,
        String taskSessionId,
        String title,
        String summary,
        List<String> chromaDocIds) {
}
