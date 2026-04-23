package com.brain.tools;

import com.brain.knowledge.ContextLoader;
import com.brain.scanner.ModuleInfo;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClarifyTaskTool implements BrainTool {
    private final ContextLoader loader = new ContextLoader();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "raw_input", ToolSupport.stringProperty("用户的原始需求描述。"),
                "auto_confirm", Map.of(
                        "type", "boolean",
                        "description", "为 true 时跳过交互，直接输出任务确认单草稿。默认 false。"),
                "project_path", ToolSupport.stringProperty("可选项目根目录。不传时默认使用 MCP Server 当前工作目录。")
        );
        return ToolSupport.tool("clarify_task", "澄清任务需求",
                "读取模块和 SPEC.md，返回任务澄清上下文，供模型提出确认问题或生成任务确认单。", props, List.of("raw_input"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = ToolSupport.projectPath(arguments);
            String rawInput = ToolSupport.requiredString(arguments, "raw_input");
            boolean autoConfirm = ToolSupport.optionalBoolean(arguments, "auto_confirm", false);
            List<ModuleInfo> allModules = loader.listModules(projectPath);
            List<ModuleInfo> relevantModules = loader.findRelevantModules(projectPath, rawInput);
            return ToolSupport.textResult(autoConfirm
                    ? autoConfirmDraft(projectPath, rawInput, allModules, relevantModules)
                    : clarifyContext(projectPath, rawInput, allModules, relevantModules));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private String clarifyContext(Path projectPath, String rawInput, List<ModuleInfo> allModules, List<ModuleInfo> relevantModules)
            throws java.io.IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("## 需求澄清上下文").append(System.lineSeparator())
                .append("- 原始需求：").append(rawInput).append(System.lineSeparator())
                .append("- 当前全部模块：").append(moduleNames(allModules)).append(System.lineSeparator())
                .append("- 初步相关模块：").append(moduleNames(relevantModules)).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("## 相关模块 SPEC").append(System.lineSeparator());

        if (relevantModules.isEmpty()) {
            builder.append("- 未根据原始需求匹配到明确模块，请结合模块列表判断边界。").append(System.lineSeparator());
        } else {
            for (ModuleInfo module : relevantModules) {
                builder.append(System.lineSeparator())
                        .append("### 模块：").append(module.name()).append(System.lineSeparator())
                        .append(loader.readModuleSpec(projectPath, module.name())).append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator())
                .append("## 建议下一步").append(System.lineSeparator())
                .append("- 请基于以上模块边界，向用户提出 2-3 个确认问题。").append(System.lineSeparator())
                .append("- 问题应聚焦：目标模块、任务类型、验收标准、排除范围。").append(System.lineSeparator())
                .append("- 用户回答后，请生成“任务确认单”，再将任务确认单作为 start_task 的入参。");
        return builder.toString();
    }

    private String autoConfirmDraft(Path projectPath, String rawInput, List<ModuleInfo> allModules, List<ModuleInfo> relevantModules)
            throws java.io.IOException {
        List<ModuleInfo> modules = relevantModules.isEmpty() ? allModules.stream().limit(3).toList() : relevantModules;
        List<String> scope = new ArrayList<>();
        for (ModuleInfo module : modules) {
            scope.add(module.name());
        }

        StringBuilder builder = new StringBuilder();
        builder.append("## 任务确认单").append(System.lineSeparator())
                .append("- 目标模块：").append(scope.isEmpty() ? "<!-- TODO: 请确认目标模块 -->" : String.join("、", scope)).append(System.lineSeparator())
                .append("- 任务类型：<!-- TODO: 请在 bug修复 / 功能开发 / 重构 中确认 -->").append(System.lineSeparator())
                .append("- 具体目标：").append(rawInput).append(System.lineSeparator())
                .append("- 影响范围：<!-- TODO: 请根据相关类、服务、页面或接口补充 -->").append(System.lineSeparator())
                .append("- 验收标准：<!-- TODO: 请补充完成的判断依据 -->").append(System.lineSeparator())
                .append("- 排除范围：<!-- TODO: 请补充本次不处理的内容 -->").append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("## 相关模块 SPEC").append(System.lineSeparator());

        if (modules.isEmpty()) {
            builder.append("- 未匹配到模块，请先使用 list_modules 确认模块边界。").append(System.lineSeparator());
        } else {
            for (ModuleInfo module : modules) {
                builder.append(System.lineSeparator())
                        .append("### 模块：").append(module.name()).append(System.lineSeparator())
                        .append(loader.readModuleSpec(projectPath, module.name())).append(System.lineSeparator());
            }
        }

        builder.append(System.lineSeparator())
                .append("以上任务确认单为自动草稿，请由模型结合用户上下文进一步确认后再调用 start_task。");
        return builder.toString();
    }

    private String moduleNames(List<ModuleInfo> modules) {
        if (modules == null || modules.isEmpty()) {
            return "无";
        }
        return String.join("、", modules.stream().map(ModuleInfo::name).toList());
    }
}
