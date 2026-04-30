package com.brain.knowledge.compact;

import com.brain.knowledge.backup.BackupEvent;
import com.brain.knowledge.backup.KnowledgeLocalWriter;
import com.brain.knowledge.ingest.ContentHashing;
import com.brain.knowledge.ingest.IngestionManifest;
import com.brain.knowledge.ingest.IngestionManifestStore;
import com.brain.knowledge.ingest.IngestionRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class CompactionService {
    private final IngestionManifestStore manifestStore = new IngestionManifestStore();
    private final KnowledgeLocalWriter localWriter = new KnowledgeLocalWriter();

    public CompactKnowledgeResult compactProject(Path projectRoot, int thresholdKb, boolean force) throws IOException {
        return compactPaths(projectRoot, scanDefaultPaths(projectRoot), thresholdKb, force);
    }

    public CompactKnowledgeResult compactPaths(
            Path projectRoot,
            List<Path> sourcePaths,
            int thresholdKb,
            boolean force) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        IngestionManifest primary = manifestStore.readPrimary(root);
        IngestionManifest backup = manifestStore.readBackup(root);

        int compacted = 0;
        int skipped = 0;
        List<Path> rewritten = new ArrayList<>();
        List<String> skippedReasons = new ArrayList<>();

        for (Path sourcePath : uniquePaths(sourcePaths)) {
            if (!Files.isRegularFile(sourcePath)) {
                skipped++;
                skippedReasons.add(sourcePath + ": 文件不存在");
                continue;
            }
            if (!isAllowed(sourcePath, root)) {
                skipped++;
                skippedReasons.add(sourcePath + ": 第一版仅允许精简 .knowledge/tasks/*.md");
                continue;
            }

            String sourceFile = relativeSource(root, sourcePath);
            String content = Files.readString(sourcePath, StandardCharsets.UTF_8);
            if (isCompacted(content)) {
                skipped++;
                skippedReasons.add(sourceFile + ": 已经是精简摘要");
                continue;
            }
            if (!force && Files.size(sourcePath) < ((long) thresholdKb * 1024L)) {
                skipped++;
                skippedReasons.add(sourceFile + ": 文件未达到精简阈值 " + thresholdKb + "KB");
                continue;
            }

            String contentHash = ContentHashing.sha256(content);
            IngestionRecord record = primary.find(sourceFile, contentHash).orElse(null);
            if (record == null) {
                record = backup.find(sourceFile, contentHash).orElse(null);
            }
            if (record == null) {
                skipped++;
                skippedReasons.add(sourceFile + ": 未找到入库记录");
                continue;
            }
            if (!record.readyForCompaction()) {
                skipped++;
                skippedReasons.add(sourceFile + ": 未满足已入库且存在 chromaDocIds 的安全门槛");
                continue;
            }

            Path backupFile = resolveBackupFile(root, record.backupFile());
            if (!Files.isRegularFile(backupFile)) {
                skipped++;
                skippedReasons.add(sourceFile + ": 缺少 .knowledge_local 原文副本");
                continue;
            }

            CompactionRecord compactionRecord = new CompactionRecord(
                    sourcePath,
                    sourceFile,
                    record.backupFile(),
                    contentHash,
                    record.taskSessionId(),
                    extractTitle(content, sourcePath),
                    extractSummary(content),
                    record.chromaDocIds());

            Files.writeString(sourcePath, compactedContent(compactionRecord), StandardCharsets.UTF_8);
            rewritten.add(sourcePath.toAbsolutePath().normalize());
            compacted++;

            IngestionRecord compactedRecord = record.withCompacted(Instant.now().toString());
            primary = primary.upsert(compactedRecord);
            backup = backup.upsert(compactedRecord);
            localWriter.appendEvent(root, new BackupEvent(
                    "compact_knowledge",
                    compactionRecord.taskSessionId(),
                    "sourceFile=" + compactionRecord.sourceFile()
                            + ", backupRef=" + compactionRecord.backupFile()
                            + ", archiveRef=" + compactionRecord.chromaDocIds(),
                    true));
        }

        manifestStore.writePrimary(root, primary);
        manifestStore.writeBackup(root, backup);
        return new CompactKnowledgeResult(
                sourcePaths.size(),
                compacted,
                skipped,
                List.copyOf(rewritten),
                List.copyOf(skippedReasons));
    }

    private List<Path> scanDefaultPaths(Path projectRoot) throws IOException {
        Path tasksRoot = projectRoot.resolve(".knowledge").resolve("tasks");
        if (!Files.isDirectory(tasksRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(tasksRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .toList();
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

    private boolean isAllowed(Path sourcePath, Path projectRoot) {
        Path tasksRoot = projectRoot.resolve(".knowledge").resolve("tasks").toAbsolutePath().normalize();
        return sourcePath.toAbsolutePath().normalize().startsWith(tasksRoot);
    }

    private boolean isCompacted(String content) {
        return content.contains("archive_ref:") && content.contains("backup_ref:");
    }

    private String relativeSource(Path root, Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        return "./" + relative.toString().replace('\\', '/');
    }

    private Path resolveBackupFile(Path root, String backupFile) {
        String normalized = backupFile == null ? "" : backupFile.trim().replace('\\', '/');
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return root.resolve(normalized.replace('/', java.io.File.separatorChar)).normalize();
    }

    private String extractTitle(String content, Path sourcePath) {
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        String fileName = sourcePath.getFileName().toString();
        return fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
    }

    private String extractSummary(String content) {
        String[] lines = content.split("\\R");
        boolean inSummary = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if ("## 任务摘要".equals(trimmed)) {
                inSummary = true;
                continue;
            }
            if (inSummary) {
                if (trimmed.startsWith("## ")) {
                    break;
                }
                if (!trimmed.isBlank()) {
                    return trimSummary(trimmed);
                }
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                continue;
            }
            return trimSummary(trimmed);
        }
        return trimSummary(content.replaceAll("\\s+", " ").trim());
    }

    private String trimSummary(String summary) {
        if (summary.length() <= 180) {
            return summary;
        }
        return summary.substring(0, 180).trim() + "...";
    }

    private String compactedContent(CompactionRecord record) {
        return """
                # %s

                ## 历史详细记录
                - 本段详细过程已归档至 Chroma
                - archive_ref: task_session_id=%s, chroma_doc_ids=%s
                - backup_ref: %s
                - summary: %s
                """.formatted(
                record.title(),
                record.taskSessionId(),
                record.chromaDocIds(),
                record.backupFile(),
                record.summary());
    }
}
