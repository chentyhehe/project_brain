package com.brain.knowledge;

import com.brain.knowledge.ingest.SyncToChromaResult;

import java.nio.file.Path;
import java.util.List;

public record FinishTaskResult(
        List<Path> writtenPaths,
        SyncToChromaResult syncResult) {
}
