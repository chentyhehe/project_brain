package com.brain.tools;

import com.brain.knowledge.restore.RestoreChromaResult;
import com.brain.knowledge.restore.RestoreService;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RestoreChromaTool implements BrainTool {
    private final RestoreService restoreService = new RestoreService();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("可选项目根目录。")
        );
        return ToolSupport.tool("restore_chroma", "从 .knowledge_local 重建 Chroma",
                "扫描 `.knowledge_local` 的任务、事件和上下文文件，重新写入 Chroma。",
                props, List.of());
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            RestoreChromaResult result = restoreService.restoreChroma(projectPath);
            return ToolSupport.textResult("""
                    restore_chroma 已执行。
                    - scanned_files: %s
                    - pending_files: %s
                    - synced_files: %s
                    - skipped_files: %s
                    - errors: %s
                    """.formatted(
                    result.syncResult().scannedFiles(),
                    result.syncResult().pendingFiles(),
                    result.syncResult().syncedFiles(),
                    result.syncResult().skippedFiles(),
                    result.syncResult().errors().isEmpty() ? "[]" : result.syncResult().errors()));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
