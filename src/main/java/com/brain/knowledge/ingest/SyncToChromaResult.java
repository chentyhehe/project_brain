package com.brain.knowledge.ingest;

import com.brain.knowledge.runtime.RuntimeResolution;

import java.util.List;

public record SyncToChromaResult(
        RuntimeResolution resolution,
        int scannedFiles,
        int pendingFiles,
        int syncedFiles,
        int skippedFiles,
        List<String> errors) {
}
