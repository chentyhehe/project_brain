package com.brain.knowledge.restore;

import com.brain.knowledge.compact.CompactKnowledgeResult;
import com.brain.knowledge.compact.CompactionService;
import com.brain.knowledge.ingest.SyncToChromaResult;
import com.brain.knowledge.ingest.SyncToChromaService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class RestoreService {
    private final SyncToChromaService syncService = new SyncToChromaService();
    private final CompactionService compactionService = new CompactionService();

    public RestoreKnowledgeResult restoreKnowledge(Path projectRoot, boolean overwrite, boolean includeRootAgents)
            throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path backupRoot = root.resolve(".knowledge_local");
        if (!Files.isDirectory(backupRoot)) {
            throw new IllegalStateException(".knowledge_local 不存在，无法执行恢复。");
        }

        List<Path> written = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int restoredCount = 0;
        int skippedCount = 0;

        restoredCount += restoreDirectory(backupRoot.resolve("global"), root.resolve(".knowledge").resolve("global"), overwrite, written, skipped);
        restoredCount += restoreDirectory(backupRoot.resolve("modules"), root.resolve(".knowledge").resolve("modules"), overwrite, written, skipped);
        restoredCount += restoreDirectory(backupRoot.resolve("tasks"), root.resolve(".knowledge").resolve("tasks"), overwrite, written, skipped);
        skippedCount = skipped.size();

        if (includeRootAgents) {
            Path backupAgents = backupRoot.resolve("restore").resolve("AGENTS.md");
            Path targetAgents = root.resolve("AGENTS.md");
            if (Files.isRegularFile(backupAgents)) {
                if (Files.exists(targetAgents) && !overwrite) {
                    skipped.add("AGENTS.md: 已存在且 overwrite=false");
                } else {
                    Files.createDirectories(targetAgents.getParent());
                    Files.copy(backupAgents, targetAgents, StandardCopyOption.REPLACE_EXISTING);
                    written.add(targetAgents.toAbsolutePath().normalize());
                    restoredCount++;
                }
            }
        }
        return new RestoreKnowledgeResult(restoredCount, skippedCount, List.copyOf(written), List.copyOf(skipped));
    }

    public RestoreChromaResult restoreChroma(Path projectRoot) throws IOException {
        SyncToChromaResult result = syncService.rebuildFromBackup(projectRoot);
        return new RestoreChromaResult(result);
    }

    public RebuildKnowledgeResult rebuildKnowledge(
            Path projectRoot,
            boolean overwrite,
            boolean includeRootAgents,
            int thresholdKb,
            boolean forceCompact) throws IOException {
        RestoreKnowledgeResult restoreKnowledgeResult = restoreKnowledge(projectRoot, overwrite, includeRootAgents);
        RestoreChromaResult restoreChromaResult = restoreChroma(projectRoot);
        CompactKnowledgeResult compactKnowledgeResult = compactionService.compactProject(projectRoot, thresholdKb, forceCompact);
        return new RebuildKnowledgeResult(restoreKnowledgeResult, restoreChromaResult, compactKnowledgeResult);
    }

    private int restoreDirectory(
            Path backupDir,
            Path targetDir,
            boolean overwrite,
            List<Path> written,
            List<String> skipped) throws IOException {
        if (!Files.isDirectory(backupDir)) {
            return 0;
        }
        int restored = 0;
        try (Stream<Path> stream = Files.walk(backupDir)) {
            for (Path source : stream.toList()) {
                if (Files.isDirectory(source)) {
                    continue;
                }
                Path relative = backupDir.relativize(source);
                Path target = targetDir.resolve(relative);
                if (Files.exists(target) && !overwrite) {
                    skipped.add(target + ": 已存在且 overwrite=false");
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                written.add(target.toAbsolutePath().normalize());
                restored++;
            }
        }
        return restored;
    }
}
