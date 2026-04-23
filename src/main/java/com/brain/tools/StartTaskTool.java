package com.brain.tools;

import com.brain.knowledge.ContextLoader;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class StartTaskTool implements BrainTool {
    private final ContextLoader loader = new ContextLoader();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "task_description", ToolSupport.stringProperty("当前任务的自然语言描述。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。")
        );
        return ToolSupport.tool("start_task", "生成任务上下文关联提示词",
                "读取项目知识库，并生成交给当前 AI 做上下文关联分析的中文 prompt；工具本身不调用大模型。",
                props, List.of("task_description"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            String taskDescription = ToolSupport.requiredString(arguments, "task_description");
            String context = loader.load(projectPath, taskDescription);
            return ToolSupport.textResult(buildPrompt(projectPath, taskDescription, context));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private String buildPrompt(Path projectPath, String taskDescription, String context) {
        return """
                请作为当前项目的代码分析助手，基于下方知识库内容做任务上下文关联分析。
                project-brain 只读取本地知识库并生成本提示词，不会调用大模型。

                ## 当前任务
                - 项目路径：%s
                - 用户任务：%s

                ## 请输出
                1. 任务意图
                2. 相关模块和依据
                3. 关键约束和踩坑
                4. 缺失上下文，需要继续读取哪些文件
                5. 下一步执行建议
                6. 完成后建议回流沉淀的知识

                ## 已加载知识上下文
                %s
                """.formatted(projectPath.toAbsolutePath().normalize(), taskDescription, context);
    }
}
