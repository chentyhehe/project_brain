package com.brain.tools;

import com.brain.knowledge.restore.RebuildKnowledgeResult;
import com.brain.knowledge.restore.RestoreService;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RebuildKnowledgeTool implements BrainTool {
    private final RestoreService restoreService = new RestoreService();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("可选项目根目录。"),
                "overwrite", ToolSupport.booleanProperty("是否覆盖当前 `.knowledge` 文件。"),
                "include_root_agents", ToolSupport.booleanProperty("是否一并恢复根 `AGENTS.md`。"),
                "threshold_kb", ToolSupport.integerProperty("用于重新精简主知识目录的阈值，默认 1024KB。"),
                "force_compact", ToolSupport.booleanProperty("是否忽略阈值直接执行 compact。")
        );
        return ToolSupport.tool("rebuild_knowledge", "从 .knowledge_local 与 Chroma 重建主知识目录",
                "先从 `.knowledge_local` 恢复主知识目录，再重建 Chroma，并重新执行 compact_knowledge 生成新的精简版 `.knowledge`。",
                props, List.of());
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            boolean overwrite = ToolSupport.optionalBoolean(arguments, "overwrite", true);
            boolean includeRootAgents = ToolSupport.optionalBoolean(arguments, "include_root_agents", false);
            int thresholdKb = ToolSupport.optionalInt(arguments, "threshold_kb", 1024);
            boolean forceCompact = ToolSupport.optionalBoolean(arguments, "force_compact", true);
            RebuildKnowledgeResult result = restoreService.rebuildKnowledge(
                    projectPath, overwrite, includeRootAgents, thresholdKb, forceCompact);
            return ToolSupport.textResult("""
                    rebuild_knowledge 已执行。
                    - restored_files: %s
                    - chroma_synced_files: %s
                    - compacted_files: %s
                    - restore_skipped: %s
                    - compact_skipped: %s
                    """.formatted(
                    result.restoreKnowledgeResult().restoredFiles(),
                    result.restoreChromaResult().syncResult().syncedFiles(),
                    result.compactKnowledgeResult().compactedFiles(),
                    result.restoreKnowledgeResult().skippedReasons().isEmpty() ? "[]" : result.restoreKnowledgeResult().skippedReasons(),
                    result.compactKnowledgeResult().skippedReasons().isEmpty() ? "[]" : result.compactKnowledgeResult().skippedReasons()));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
