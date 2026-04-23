package com.brain.tools;

import com.brain.knowledge.ContextLoader;
import com.brain.scanner.ModuleInfo;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ListModulesTool implements BrainTool {
    private final ContextLoader loader = new ContextLoader();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("可选项目根目录。不传时默认使用 MCP Server 当前工作目录。")
        );
        return ToolSupport.tool("list_modules", "列出知识模块",
                "返回当前项目已初始化的知识模块及其路径。", props, List.of());
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            List<ModuleInfo> modules = loader.listModules(projectPath);
            if (modules.isEmpty()) {
                return ToolSupport.textResult("未找到模块。");
            }
            StringBuilder builder = new StringBuilder("模块列表：\n");
            for (ModuleInfo module : modules) {
                builder.append("- ").append(module.name()).append(": ").append(module.path()).append(System.lineSeparator());
            }
            return ToolSupport.textResult(builder.toString().stripTrailing());
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }
}
