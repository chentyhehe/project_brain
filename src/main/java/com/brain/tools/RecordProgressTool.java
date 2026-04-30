package com.brain.tools;

import com.brain.knowledge.backup.ProcessRecordWriter;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RecordProgressTool implements BrainTool {
    private final ProcessRecordWriter writer = new ProcessRecordWriter();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "summary", ToolSupport.stringProperty("本次进展的简要说明。"),
                "details", ToolSupport.stringProperty("可选详细进展。"),
                "modules_affected", ToolSupport.stringArrayProperty("可选受影响模块列表。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。")
        );
        return ToolSupport.tool("record_progress", "记录任务进展",
                "将过程进展写入 `.knowledge_local/events`，为后续 Chroma 补录保留原始过程轨迹。",
                props, List.of("summary"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            String summary = ToolSupport.requiredString(arguments, "summary");
            String details = ToolSupport.optionalString(arguments, "details");
            List<String> modulesAffected = ToolSupport.stringList(arguments, "modules_affected");
            writer.recordProgress(projectPath, summary, details, modulesAffected);
            return ToolSupport.textResult("已记录任务进展到 `.knowledge_local/events`。");
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
