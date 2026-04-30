package com.brain.tools;

import com.brain.knowledge.backup.ProcessRecordWriter;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class RecordPlanTool implements BrainTool {
    private final ProcessRecordWriter writer = new ProcessRecordWriter();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "plan_summary", ToolSupport.stringProperty("本次计划的简要说明。"),
                "steps", ToolSupport.stringArrayProperty("计划步骤列表。"),
                "modules_affected", ToolSupport.stringArrayProperty("可选受影响模块列表。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。")
        );
        return ToolSupport.tool("record_plan", "记录任务计划",
                "将任务计划写入 `.knowledge_local/events` 和上下文快照，便于后续补录与恢复。",
                props, List.of("plan_summary", "steps"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            String planSummary = ToolSupport.requiredString(arguments, "plan_summary");
            List<String> steps = ToolSupport.stringList(arguments, "steps");
            List<String> modulesAffected = ToolSupport.stringList(arguments, "modules_affected");
            writer.recordPlan(projectPath, planSummary, steps, modulesAffected);
            return ToolSupport.textResult("已记录任务计划到 `.knowledge_local/events`。");
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
