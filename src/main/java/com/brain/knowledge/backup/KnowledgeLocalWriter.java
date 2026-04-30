package com.brain.knowledge.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class KnowledgeLocalWriter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    public Path backupRoot(Path projectRoot) {
        return projectRoot.toAbsolutePath().normalize().resolve(".knowledge_local");
    }

    public void ensureInitialized(Path projectRoot) throws IOException {
        Path root = backupRoot(projectRoot);
        Files.createDirectories(root.resolve("global"));
        Files.createDirectories(root.resolve("modules"));
        Files.createDirectories(root.resolve("tasks"));
        Files.createDirectories(root.resolve("events"));
        Files.createDirectories(root.resolve("context"));
        Files.createDirectories(root.resolve("restore"));
    }

    public List<Path> snapshotKnowledge(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path backupRoot = backupRoot(root);
        ensureInitialized(root);

        List<Path> written = new ArrayList<>();
        copyFileIfExists(root.resolve("AGENTS.md"), backupRoot.resolve("restore").resolve("AGENTS.md"), written);
        mirrorDirectory(root.resolve(".knowledge").resolve("global"), backupRoot.resolve("global"), written);
        mirrorDirectory(root.resolve(".knowledge").resolve("modules"), backupRoot.resolve("modules"), written);
        mirrorDirectory(root.resolve(".knowledge").resolve("tasks"), backupRoot.resolve("tasks"), written);
        return written;
    }

    public Path appendEvent(Path projectRoot, BackupEvent event) throws IOException {
        ensureInitialized(projectRoot);
        String stage = normalizeSegment(event.stage(), "event");
        String summary = normalizeSegment(event.summary(), "record");
        String fileName = FILE_TIME.format(LocalDateTime.now()) + "-" + stage + "-" + summary + ".md";
        Path file = backupRoot(projectRoot).resolve("events").resolve(fileName);
        String content = """
                # Backup Event

                - time: %s
                - stage: %s
                - success: %s
                - summary: %s

                ## detail
                %s
                """.formatted(
                LocalDateTime.now(),
                blankAsDefault(event.stage(), "event"),
                event.success(),
                blankAsDefault(event.summary(), "record"),
                blankAsDefault(event.detail(), "<none>"));
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().normalize();
    }

    public Path writeContextSnapshot(Path projectRoot, ContextSnapshot snapshot) throws IOException {
        ensureInitialized(projectRoot);
        String category = normalizeSegment(snapshot.category(), "context");
        String summary = normalizeSegment(snapshot.summary(), "snapshot");
        String fileName = FILE_TIME.format(LocalDateTime.now()) + "-" + category + "-" + summary + ".md";
        Path file = backupRoot(projectRoot).resolve("context").resolve(fileName);
        String content = """
                # Context Snapshot

                - time: %s
                - category: %s
                - summary: %s

                ## content
                %s
                """.formatted(
                LocalDateTime.now(),
                blankAsDefault(snapshot.category(), "context"),
                blankAsDefault(snapshot.summary(), "snapshot"),
                blankAsDefault(snapshot.content(), "<none>"));
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().normalize();
    }

    public Path writeTaskContextSummary(Path projectRoot, TaskContextSummary summary) throws IOException {
        ensureInitialized(projectRoot);
        String sourceTool = normalizeSegment(summary.sourceTool(), "context-summary");
        String summaryText = normalizeSegment(summary.summary(), "summary");
        String fileName = FILE_TIME.format(LocalDateTime.now()) + "-" + sourceTool + "-" + summaryText + "-summary.md";
        Path file = backupRoot(projectRoot).resolve("context").resolve(fileName);
        String content = """
                # Task Context Summary

                - time: %s
                - source_tool: %s
                - summary: %s

                ## key_points
                %s

                ## constraints
                %s
                """.formatted(
                LocalDateTime.now(),
                blankAsDefault(summary.sourceTool(), "context-summary"),
                blankAsDefault(summary.summary(), "summary"),
                listBlock(summary.keyPoints()),
                listBlock(summary.constraints()));
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().normalize();
    }

    public Path writeTaskContextSnapshot(Path projectRoot, TaskContextSnapshot snapshot) throws IOException {
        return writeContextSnapshot(projectRoot, new ContextSnapshot(
                snapshot.category(),
                snapshot.summary(),
                snapshot.content()));
    }

    private void mirrorDirectory(Path source, Path target, List<Path> written) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    written.add(destination.toAbsolutePath().normalize());
                }
            }
        }
    }

    private void copyFileIfExists(Path source, Path target, List<Path> written) throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        written.add(target.toAbsolutePath().normalize());
    }

    private String normalizeSegment(String value, String fallback) {
        String normalized = blankAsDefault(value, fallback).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.length() > 40 ? normalized.substring(0, 40).replaceAll("-+$", "") : normalized;
    }

    private String blankAsDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String listBlock(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- <none>";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append("- ").append(value.trim()).append(System.lineSeparator());
            }
        }
        return builder.isEmpty() ? "- <none>" : builder.toString().stripTrailing();
    }
}
