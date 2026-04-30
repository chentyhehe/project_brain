package com.brain.knowledge.ingest;

import com.brain.knowledge.chroma.ChromaCollection;
import com.brain.knowledge.chroma.ChromaDocument;
import com.brain.knowledge.chroma.ChromaDocumentId;
import com.brain.knowledge.chroma.ChromaMetadata;
import com.brain.knowledge.chroma.HttpChromaClient;
import com.brain.knowledge.embedding.EmbeddingRequest;
import com.brain.knowledge.embedding.HttpEmbeddingClient;
import com.brain.knowledge.runtime.DefaultKnowledgeRuntimeCoordinator;
import com.brain.knowledge.runtime.RuntimeResolution;
import com.brain.knowledge.runtime.StoreConfiguration;
import com.brain.knowledge.runtime.StoreConfigurationLoader;
import com.brain.knowledge.store.StoreMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class SyncToChromaService {
    private final DefaultKnowledgeRuntimeCoordinator runtimeCoordinator = new DefaultKnowledgeRuntimeCoordinator();
    private final StoreConfigurationLoader configurationLoader = new StoreConfigurationLoader();
    private final IngestionManifestStore manifestStore = new IngestionManifestStore();

    public SyncToChromaResult syncProject(Path projectRoot) throws IOException {
        return syncPaths(projectRoot, scanDefaultPaths(projectRoot));
    }

    public SyncToChromaResult syncPaths(Path projectRoot, List<Path> sourcePaths) throws IOException {
        return syncPaths(projectRoot, sourcePaths, false);
    }

    public SyncToChromaResult rebuildFromBackup(Path projectRoot) throws IOException {
        return syncPaths(projectRoot, scanBackupPaths(projectRoot), true);
    }

    public SyncToChromaResult rebuildFromBackup(Path projectRoot, List<Path> sourcePaths) throws IOException {
        return syncPaths(projectRoot, sourcePaths, true);
    }

    private SyncToChromaResult syncPaths(Path projectRoot, List<Path> sourcePaths, boolean ignoreManifest) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        RuntimeResolution resolution = runtimeCoordinator.resolveForWrite(root);
        if (resolution.storeMode() != StoreMode.HYBRID_CHROMA || !resolution.chromaEnabled()) {
            return new SyncToChromaResult(resolution, sourcePaths.size(), 0, 0, sourcePaths.size(), List.of());
        }

        StoreConfiguration configuration = configurationLoader.load(root);
        HttpEmbeddingClient embeddingClient = new HttpEmbeddingClient(configuration.embedding());
        HttpChromaClient chromaClient = new HttpChromaClient(configuration.chroma());
        IngestionManifest primary = manifestStore.readPrimary(root);
        IngestionManifest backup = manifestStore.readBackup(root);

        int pending = 0;
        int synced = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Path path : uniquePaths(sourcePaths)) {
            if (!Files.isRegularFile(path)) {
                skipped++;
                continue;
            }
            String sourceFile = relativeSource(root, path);
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (isCompactedSummary(content)) {
                skipped++;
                continue;
            }
            String contentHash = ContentHashing.sha256(content);
            if (!ignoreManifest && (manifestStore.isIngested(primary, sourceFile, contentHash)
                    || manifestStore.isIngested(backup, sourceFile, contentHash))) {
                skipped++;
                continue;
            }
            pending++;
            try {
                List<String> chunks = chunk(content);
                if (chunks.isEmpty()) {
                    skipped++;
                    continue;
                }
                List<List<Double>> vectors = embeddingClient
                        .embed(new EmbeddingRequest(configuration.embedding().model(), chunks))
                        .vectors();
                if (vectors.size() != chunks.size()) {
                    throw new IOException("embedding vector count mismatch: chunks=" + chunks.size() + ", vectors=" + vectors.size());
                }
                List<ChromaDocument> documents = toDocuments(root, path, sourceFile, contentHash, chunks, vectors);
                List<String> docIds = chromaClient.upsert(documents);
                IngestionRecord record = new IngestionRecord(
                        sourceFile,
                        backupFile(root, sourceFile),
                        taskSessionId(path),
                        stage(path),
                        contentHash,
                        true,
                        false,
                        docIds,
                        Instant.now().toString(),
                        null);
                primary = primary.upsert(record);
                backup = backup.upsert(record);
                synced++;
            } catch (Exception exception) {
                errors.add(sourceFile + ": " + exception.getMessage());
            }
        }

        manifestStore.writePrimary(root, primary);
        manifestStore.writeBackup(root, backup);
        return new SyncToChromaResult(resolution, sourcePaths.size(), pending, synced, skipped, List.copyOf(errors));
    }

    private List<Path> scanDefaultPaths(Path projectRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        collectMarkdownFiles(projectRoot.resolve(".knowledge").resolve("tasks"), files);
        collectMarkdownFiles(projectRoot.resolve(".knowledge_local").resolve("tasks"), files);
        collectMarkdownFiles(projectRoot.resolve(".knowledge_local").resolve("events"), files);
        collectMarkdownFiles(projectRoot.resolve(".knowledge_local").resolve("context"), files);
        return List.copyOf(files);
    }

    private List<Path> scanBackupPaths(Path projectRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        collectMarkdownFiles(projectRoot.resolve(".knowledge_local").resolve("tasks"), files);
        collectMarkdownFiles(projectRoot.resolve(".knowledge_local").resolve("events"), files);
        collectMarkdownFiles(projectRoot.resolve(".knowledge_local").resolve("context"), files);
        return List.copyOf(files);
    }

    private void collectMarkdownFiles(Path root, List<Path> files) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .forEach(files::add);
        }
    }

    private Set<Path> uniquePaths(List<Path> sourcePaths) {
        Set<Path> unique = new LinkedHashSet<>();
        for (Path path : sourcePaths) {
            if (path != null) {
                unique.add(path.toAbsolutePath().normalize());
            }
        }
        return unique;
    }

    private List<ChromaDocument> toDocuments(
            Path root,
            Path path,
            String sourceFile,
            String contentHash,
            List<String> chunks,
            List<List<Double>> vectors) {
        List<ChromaDocument> documents = new ArrayList<>();
        String taskSessionId = taskSessionId(path);
        String stage = stage(path);
        String module = module(path);
        ChromaCollection collection = collection(path);
        String backupFile = backupFile(root, sourceFile);
        String createdAt = Instant.now().toString();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkStage = stage + "-" + (i + 1);
            documents.add(new ChromaDocument(
                    ChromaDocumentId.build(collection, taskSessionId, chunkStage, contentHash),
                    collection,
                    chunks.get(i),
                    new ChromaMetadata(
                            root.toString(),
                            root.getFileName().toString(),
                            module,
                            stage,
                            taskSessionId,
                            sourceFile,
                            backupFile,
                            contentHash,
                            true,
                            false,
                            createdAt),
                    vectors.get(i)));
        }
        return List.copyOf(documents);
    }

    private List<String> chunk(String content) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("(\\r?\\n){2,}");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (!current.isEmpty() && current.length() + 2 + trimmed.length() > 1800) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append(System.lineSeparator()).append(System.lineSeparator());
            }
            current.append(trimmed);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return List.copyOf(chunks);
    }

    private String relativeSource(Path root, Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        return "./" + relative.toString().replace('\\', '/');
    }

    private String backupFile(Path root, String sourceFile) {
        if (sourceFile.startsWith("./.knowledge_local/")) {
            return sourceFile;
        }
        if (sourceFile.startsWith("./.knowledge/")) {
            return sourceFile.replace("./.knowledge/", "./.knowledge_local/");
        }
        return root.resolve(".knowledge_local").resolve("restore").resolve(Path.of(sourceFile).getFileName()).toString();
    }

    private String taskSessionId(Path path) {
        String file = path.getFileName().toString();
        int dot = file.lastIndexOf('.');
        return dot >= 0 ? file.substring(0, dot) : file;
    }

    private String stage(Path path) {
        String parent = path.getParent() == null ? "" : path.getParent().getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (parent) {
            case "events" -> "progress";
            case "context" -> "context";
            case "tasks" -> "finish";
            default -> parent.isBlank() ? "knowledge" : parent;
        };
    }

    private String module(Path path) {
        String text = path.toString().replace('\\', '/');
        if (text.contains("/modules/")) {
            String[] parts = text.split("/modules/");
            if (parts.length > 1) {
                String remaining = parts[1];
                int slash = remaining.indexOf('/');
                if (slash > 0) {
                    return remaining.substring(0, slash);
                }
            }
        }
        return "knowledge";
    }

    private ChromaCollection collection(Path path) {
        String parent = path.getParent() == null ? "" : path.getParent().getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (parent) {
            case "context" -> ChromaCollection.TASK_CONTEXT_SUMMARIES;
            case "events" -> ChromaCollection.KNOWLEDGE_EVENTS;
            default -> ChromaCollection.KNOWLEDGE_SUMMARIES;
        };
    }

    private boolean isCompactedSummary(String content) {
        return content.contains("archive_ref:") && content.contains("backup_ref:");
    }
}
