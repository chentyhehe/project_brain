package com.brain.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class GetFileTool implements BrainTool {
    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "file_path", ToolSupport.stringProperty(".knowledge 目录下的文件路径。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。不传时默认使用 MCP Server 当前工作目录。")
        );
        return ToolSupport.tool("get_file", "读取知识文件",
                "读取 .knowledge 目录下的指定文件。", props, List.of("file_path"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectRoot = ToolSupport.projectPath(arguments).toAbsolutePath().normalize();
            Path knowledgeRoot = projectRoot.resolve(".knowledge").toAbsolutePath().normalize();
            String rawFilePath = ToolSupport.requiredString(arguments, "file_path");
            Path requested = Path.of(rawFilePath);
            Path file = requested.isAbsolute()
                    ? requested.toAbsolutePath().normalize()
                    : resolveRelativeKnowledgePath(projectRoot, knowledgeRoot, rawFilePath);

            if (!file.startsWith(knowledgeRoot)) {
                throw new IllegalArgumentException("get_file 只能读取 .knowledge 目录下的文件。");
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("文件不存在：" + file);
            }
            return ToolSupport.textResult("## 文件：" + file + System.lineSeparator()
                    + Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private Path resolveRelativeKnowledgePath(Path projectRoot, Path knowledgeRoot, String rawFilePath) {
        Path relative = Path.of(rawFilePath);
        if (relative.startsWith(".knowledge")) {
            return projectRoot.resolve(relative).toAbsolutePath().normalize();
        }
        return knowledgeRoot.resolve(relative).toAbsolutePath().normalize();
    }
}
