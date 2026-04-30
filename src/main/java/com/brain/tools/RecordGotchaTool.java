package com.brain.tools;

import com.brain.knowledge.backup.ProcessRecordWriter;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RecordGotchaTool implements BrainTool {
    private final ProcessRecordWriter writer = new ProcessRecordWriter();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "title", ToolSupport.stringProperty("本次踩坑或边界条件标题。"),
                "details", ToolSupport.stringProperty("踩坑详情或经验说明。"),
                "modules_affected", ToolSupport.stringArrayProperty("可选受影响模块列表。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。")
        );
        return ToolSupport.tool("record_gotcha", "记录踩坑经验",
                "将踩坑、边界条件或临时经验写入 `.knowledge_local/events`，供后续归纳和回流。",
                props, List.of("title", "details"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            String title = ToolSupport.requiredString(arguments, "title");
            String details = ToolSupport.requiredString(arguments, "details");
            List<String> modulesAffected = ToolSupport.stringList(arguments, "modules_affected");
            writer.recordGotcha(projectPath, title, details, modulesAffected);
            return ToolSupport.textResult("已记录踩坑经验到 `.knowledge_local/events`。");
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
