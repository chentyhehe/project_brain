package com.brain.knowledge;

import com.brain.knowledge.backup.KnowledgeLocalWriter;
import com.brain.knowledge.ingest.SyncToChromaResult;
import com.brain.knowledge.ingest.SyncToChromaService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class FinishTaskService {
    private final FlowbackWriter flowbackWriter = new FlowbackWriter();
    private final KnowledgeLocalWriter localWriter = new KnowledgeLocalWriter();
    private final SyncToChromaService syncService = new SyncToChromaService();

    public FinishTaskResult finish(
            Path projectRoot,
            String summary,
            List<String> decisions,
            String gotchas,
            List<String> modulesAffected) throws IOException {
        List<Path> written = flowbackWriter.write(projectRoot, summary, decisions, gotchas, modulesAffected);
        localWriter.snapshotKnowledge(projectRoot);
        SyncToChromaResult syncResult = syncService.syncPaths(projectRoot, written);
        return new FinishTaskResult(written, syncResult);
    }
}
