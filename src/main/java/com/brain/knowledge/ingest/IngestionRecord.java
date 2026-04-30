package com.brain.knowledge.ingest;

import java.util.List;

public record IngestionRecord(
        String sourceFile,
        String backupFile,
        String taskSessionId,
        String stage,
        String contentHash,
        boolean ingested,
        boolean compacted,
        List<String> chromaDocIds,
        String lastIngestedAt,
        String lastCompactedAt) {
    public boolean matches(String sourceFile, String contentHash) {
        return this.sourceFile.equals(sourceFile) && this.contentHash.equals(contentHash);
    }

    public boolean readyForCompaction() {
        return ingested && chromaDocIds != null && !chromaDocIds.isEmpty();
    }

    public IngestionRecord withCompacted(String compactedAt) {
        return new IngestionRecord(
                sourceFile,
                backupFile,
                taskSessionId,
                stage,
                contentHash,
                ingested,
                true,
                chromaDocIds,
                lastIngestedAt,
                compactedAt);
    }
}
