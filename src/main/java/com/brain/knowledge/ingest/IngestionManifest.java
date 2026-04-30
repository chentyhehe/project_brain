package com.brain.knowledge.ingest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record IngestionManifest(
        int version,
        String updatedAt,
        List<IngestionRecord> records) {
    public static final int CURRENT_VERSION = 1;

    public static IngestionManifest empty() {
        return new IngestionManifest(CURRENT_VERSION, Instant.now().toString(), List.of());
    }

    public Optional<IngestionRecord> find(String sourceFile, String contentHash) {
        return records.stream()
                .filter(record -> record.matches(sourceFile, contentHash))
                .findFirst();
    }

    public IngestionManifest upsert(IngestionRecord record) {
        List<IngestionRecord> updated = new ArrayList<>();
        boolean replaced = false;
        for (IngestionRecord current : records) {
            if (current.matches(record.sourceFile(), record.contentHash())) {
                updated.add(record);
                replaced = true;
            } else {
                updated.add(current);
            }
        }
        if (!replaced) {
            updated.add(record);
        }
        return new IngestionManifest(CURRENT_VERSION, Instant.now().toString(), List.copyOf(updated));
    }
}
