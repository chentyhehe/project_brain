package com.brain.tools;

import com.brain.knowledge.ingest.SyncToChromaResult;
import com.brain.knowledge.ingest.SyncToChromaService;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SyncToChromaTool implements BrainTool {
    private final SyncToChromaService service = new SyncToChromaService();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("可选项目根目录。"),
                "source_paths", ToolSupport.stringArrayProperty("可选相对或绝对文件路径列表，仅补录指定文件。")
        );
        return ToolSupport.tool("sync_to_chroma", "补录历史知识到 Chroma",
                "扫描 `.knowledge` 与 `.knowledge_local` 的未入库内容，写入 Chroma 并更新 ingestion manifest。",
                props, List.of());
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            List<String> sourcePaths = ToolSupport.stringList(arguments, "source_paths");
            SyncToChromaResult result = sourcePaths.isEmpty()
                    ? service.syncProject(projectPath)
                    : service.syncPaths(projectPath, resolvePaths(projectPath, sourcePaths));
            return ToolSupport.textResult(format(result));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private List<Path> resolvePaths(Path projectPath, List<String> sourcePaths) {
        List<Path> resolved = new ArrayList<>();
        for (String sourcePath : sourcePaths) {
            Path candidate = Path.of(sourcePath);
            resolved.add(candidate.isAbsolute() ? candidate : projectPath.resolve(sourcePath).normalize());
        }
        return List.copyOf(resolved);
    }

    private String format(SyncToChromaResult result) {
        return """
                sync_to_chroma 已执行。
                - scanned_files: %s
                - pending_files: %s
                - synced_files: %s
                - skipped_files: %s
                - errors: %s
                """.formatted(
                result.scannedFiles(),
                result.pendingFiles(),
                result.syncedFiles(),
                result.skippedFiles(),
                result.errors().isEmpty() ? "[]" : result.errors());
    }
}
