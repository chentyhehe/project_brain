package com.brain.knowledge.restore;

import java.nio.file.Path;
import java.util.List;

public record RestoreKnowledgeResult(
        int restoredFiles,
        int skippedFiles,
        List<Path> writtenPaths,
        List<String> skippedReasons) {
}
