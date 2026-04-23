package com.brain.tools;

import com.brain.knowledge.FlowbackWriter;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FinishTaskTool implements BrainTool {
    private final FlowbackWriter writer = new FlowbackWriter();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "summary", ToolSupport.stringProperty("本次任务完成了什么。"),
                "decisions", ToolSupport.stringArrayProperty("关键决策以及原因。"),
                "gotchas", ToolSupport.stringProperty("踩坑或经验，可以为空。"),
                "modules_affected", ToolSupport.stringArrayProperty("受影响模块名称。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。")
        );
        return ToolSupport.tool("finish_task", "写入任务记录并生成知识回流提示词",
                "写入基础任务记录，并生成交给当前 AI 做知识回流沉淀检查的中文 prompt；工具本身不调用大模型。",
                props, List.of("summary", "decisions", "gotchas", "modules_affected"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            String summary = ToolSupport.requiredString(arguments, "summary");
            List<String> decisions = ToolSupport.stringList(arguments, "decisions");
            String gotchas = ToolSupport.optionalString(arguments, "gotchas");
            List<String> modulesAffected = ToolSupport.stringList(arguments, "modules_affected");
            List<Path> written = writer.write(projectPath, summary, decisions, gotchas, modulesAffected);
            return ToolSupport.textResult(buildPrompt(projectPath, summary, decisions, gotchas, modulesAffected, written));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private String buildPrompt(Path projectPath, String summary, List<String> decisions, String gotchas,
                               List<String> modulesAffected, List<Path> written) {
        return """
                finish_task 已完成本地静态写入。请作为当前项目的代码分析助手，继续判断是否需要沉淀更多知识。
                project-brain 只写入基础记录并生成本提示词，不会调用大模型。

                ## 任务结果
                - 项目路径：%s
                - summary：%s
                - decisions：%s
                - gotchas：%s
                - modules_affected：%s

                ## 已写入文件
                %s

                ## 请检查
                1. 是否需要补充全局 AGENTS
                2. 是否需要补充模块 AGENTS 的约束或 Pitfalls
                3. 是否需要补充模块 SPEC 的业务规则
                4. 是否需要更新 api.md 的接口契约
                5. 哪些内容不应回流，以及原因
                """.formatted(
                projectPath.toAbsolutePath().normalize(), summary, listOrNone(decisions),
                gotchas == null || gotchas.isBlank() ? "无" : gotchas,
                listOrNone(modulesAffected), formatPaths(written));
    }

    private String listOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "无" : values.toString();
    }

    private String formatPaths(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            builder.append("- ").append(path).append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }
}
