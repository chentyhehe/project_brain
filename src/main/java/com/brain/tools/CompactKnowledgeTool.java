package com.brain.tools;

import com.brain.knowledge.compact.CompactKnowledgeResult;
import com.brain.knowledge.compact.CompactionService;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CompactKnowledgeTool implements BrainTool {
    private final CompactionService service = new CompactionService();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("可选项目根目录。"),
                "source_paths", ToolSupport.stringArrayProperty("可选相对或绝对文件路径列表，仅精简指定文件。"),
                "threshold_kb", ToolSupport.integerProperty("精简阈值，默认 1024KB。"),
                "force", ToolSupport.booleanProperty("是否忽略阈值限制。")
        );
        return ToolSupport.tool("compact_knowledge", "精简主知识目录中的大文本记录",
                "在确认 `.knowledge_local` 已备份且 Chroma 已入库后，对允许范围内的 `.knowledge` 文件写入摘要 + 引用形式的精简内容。",
                props, List.of());
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            List<String> sourcePaths = ToolSupport.stringList(arguments, "source_paths");
            int thresholdKb = ToolSupport.optionalInt(arguments, "threshold_kb", 1024);
            boolean force = parseForce(arguments);
            CompactKnowledgeResult result = sourcePaths.isEmpty()
                    ? service.compactProject(projectPath, thresholdKb, force)
                    : service.compactPaths(projectPath, resolvePaths(projectPath, sourcePaths), thresholdKb, force);
            return ToolSupport.textResult(format(result));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private boolean parseForce(Map<String, Object> arguments) {
        return ToolSupport.optionalBoolean(arguments, "force", false);
    }

    private List<Path> resolvePaths(Path projectPath, List<String> sourcePaths) {
        List<Path> resolved = new ArrayList<>();
        for (String sourcePath : sourcePaths) {
            Path candidate = Path.of(sourcePath);
            resolved.add(candidate.isAbsolute() ? candidate : projectPath.resolve(sourcePath).normalize());
        }
        return List.copyOf(resolved);
    }

    private String format(CompactKnowledgeResult result) {
        return """
                compact_knowledge 已执行。
                - scanned_files: %s
                - compacted_files: %s
                - skipped_files: %s
                - rewritten_files: %s
                - skipped_reasons: %s
                """.formatted(
                result.scannedFiles(),
                result.compactedFiles(),
                result.skippedFiles(),
                result.rewrittenFiles().isEmpty() ? "[]" : result.rewrittenFiles(),
                result.skippedReasons().isEmpty() ? "[]" : result.skippedReasons());
    }
}
