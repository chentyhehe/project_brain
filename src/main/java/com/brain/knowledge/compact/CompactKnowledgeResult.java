package com.brain.knowledge.compact;

import java.nio.file.Path;
import java.util.List;

public record CompactKnowledgeResult(
        int scannedFiles,
        int compactedFiles,
        int skippedFiles,
        List<Path> rewrittenFiles,
        List<String> skippedReasons) {
}
