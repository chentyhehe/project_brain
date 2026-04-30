package com.brain.tools;

import com.brain.knowledge.restore.RestoreKnowledgeResult;
import com.brain.knowledge.restore.RestoreService;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RestoreKnowledgeTool implements BrainTool {
    private final RestoreService restoreService = new RestoreService();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("可选项目根目录。"),
                "overwrite", ToolSupport.booleanProperty("是否覆盖当前 `.knowledge` 中已存在的文件。"),
                "include_root_agents", ToolSupport.booleanProperty("是否一并恢复根 `AGENTS.md`。")
        );
        return ToolSupport.tool("restore_knowledge", "从 .knowledge_local 恢复主知识目录",
                "从 `.knowledge_local` 恢复 `.knowledge`，可按 overwrite 规则决定覆盖策略。",
                props, List.of());
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            boolean overwrite = ToolSupport.optionalBoolean(arguments, "overwrite", false);
            boolean includeRootAgents = ToolSupport.optionalBoolean(arguments, "include_root_agents", false);
            RestoreKnowledgeResult result = restoreService.restoreKnowledge(projectPath, overwrite, includeRootAgents);
            return ToolSupport.textResult("""
                    restore_knowledge 已执行。
                    - restored_files: %s
                    - skipped_files: %s
                    - written_paths: %s
                    - skipped_reasons: %s
                    """.formatted(
                    result.restoredFiles(),
                    result.skippedFiles(),
                    result.writtenPaths().isEmpty() ? "[]" : result.writtenPaths(),
                    result.skippedReasons().isEmpty() ? "[]" : result.skippedReasons()));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
