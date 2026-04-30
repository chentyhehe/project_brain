package com.brain.knowledge.ingest;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IngestionManifestStore {
    private static final String MANIFEST_FILE = "ingestion_manifest.json";

    private final McpJsonMapper mapper = McpJsonDefaults.getMapper();

    public IngestionManifest readPrimary(Path projectRoot) throws IOException {
        return read(primaryPath(projectRoot));
    }

    public IngestionManifest readBackup(Path projectRoot) throws IOException {
        return read(backupPath(projectRoot));
    }

    public void writePrimary(Path projectRoot, IngestionManifest manifest) throws IOException {
        write(primaryPath(projectRoot), manifest);
    }

    public void writeBackup(Path projectRoot, IngestionManifest manifest) throws IOException {
        write(backupPath(projectRoot), manifest);
    }

    public boolean isIngested(IngestionManifest manifest, String sourceFile, String contentHash) {
        return manifest.find(sourceFile, contentHash)
                .map(IngestionRecord::ingested)
                .orElse(false);
    }

    public boolean canCompact(IngestionManifest manifest, String sourceFile, String contentHash) {
        return manifest.find(sourceFile, contentHash)
                .map(IngestionRecord::readyForCompaction)
                .orElse(false);
    }

    public Path primaryPath(Path projectRoot) {
        return projectRoot.resolve(".knowledge").resolve(MANIFEST_FILE);
    }

    public Path backupPath(Path projectRoot) {
        return projectRoot.resolve(".knowledge_local").resolve(MANIFEST_FILE);
    }

    private IngestionManifest read(Path path) throws IOException {
        if (Files.notExists(path)) {
            return IngestionManifest.empty();
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return IngestionManifest.empty();
        }
        return mapper.readValue(json, IngestionManifest.class);
    }

    private void write(Path path, IngestionManifest manifest) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writeValueAsString(manifest) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
