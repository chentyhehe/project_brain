package com.brain.template;

import com.brain.scanner.ProjectScanResult;
import com.brain.scanner.ProjectType;
import com.brain.knowledge.StaticKnowledgeAnalyzer;

import java.time.LocalDateTime;
import java.util.List;

public final class TemplateEngine {
    public String rootAgents(ProjectScanResult project) {
        return rootAgents(project, null);
    }

    public String rootAgents(ProjectScanResult project, StaticKnowledgeAnalyzer.ProjectKnowledge knowledge) {
        return """
                # AGENTS.md

                ## 项目概览
                %s - 类型: %s - 技术栈: %s

                ## Tech Stack
                %s

                ## Global Constraints
                %s

                ## Module Map
                %s

                ## How to Work
                <!-- TODO: 由维护者填写固定工作方式。 -->

                ## Project Brain MCP 工作协议

                你正在协作的项目接入了 project-brain MCP Server。该 MCP Server 不会主动运行，也不会调用任何大模型；是否调用工具由当前 AI 助手自行判断。

                除非用户明确要求不要使用 MCP，否则处理本项目相关任务时，必须遵循本文件定义的流程。

                ### 判断任务类型
                收到用户请求后，先判断是否与当前项目有关。

                以下情况不调用 project-brain 工具，直接回答：
                - 闲聊、翻译、通用概念解释。
                - 与当前项目代码、架构、业务、模块、配置、部署无关的问题。
                - 用户明确要求不要读取项目知识库。

                以下情况必须调用 project-brain 工具：
                - 需要修改、阅读、解释、调试当前项目代码。
                - 需要理解当前项目架构、模块职责、业务规则。
                - 需要做技术方案、重构方案、接口设计或排障。
                - 用户提到某个业务问题、模块问题、缺陷、性能问题或需求变更。

                ### 标准执行流程
                对所有项目相关任务，按以下顺序执行：

                1. 先调用 start_task
                   - 参数 task_description 使用用户原始任务描述。
                   - 如果 MCP 客户端需要 project_path，传入当前项目根目录。
                   - 调用后阅读返回的根 AGENTS、全局知识和相关模块知识。

                2. 再执行用户任务
                   - 基于 start_task 返回的上下文理解项目约定。
                   - 如上下文不足，可调用 list_modules 查看模块。
                   - 如需要精确读取某个知识文件，可调用 get_file。
                   - 不要因为知识库内容为空就停止工作，应继续阅读代码并完成任务。

                3. 最后判断是否调用 finish_task
                   - 如果满足“必须回流”的条件，任务完成后调用 finish_task。
                   - 如果满足“不需要回流”的条件，不调用 finish_task。

                ### clarify_task 触发规则
                以下情况需要先调用 clarify_task，再决定是否执行 start_task：
                - 需求描述少于 20 字且包含模糊词，例如“优化”“改一下”“处理一下”“看看”。
                - 任务可能涉及多个模块，但用户没有说明清晰边界。
                - 用户需求可能与模块 SPEC.md 中已有业务规则冲突。

                以下情况不需要调用 clarify_task，可直接进入 start_task：
                - 需求已经具体，例如明确的缺陷、明确的字段、明确的方法、明确的接口。
                - 用户明确表示“直接做”“不用确认”。

                clarify_task 返回模块列表和相关 SPEC.md 内容，供模型向用户提出 2-3 个确认问题。
                用户确认后，应先整理成“任务确认单”，再将任务确认单作为 start_task 的入参。

                ### start_task 调用规则
                每个项目相关任务开始时，默认调用一次 start_task。

                适合调用 start_task 的例子：
                - “修复支付超时问题”
                - “解释 user 模块的登录流程”
                - “帮我实现订单导出”
                - “看看这个接口为什么返回 500”
                - “重构前端商品详情页”

                不适合调用 start_task 的例子：
                - “Java 中 HashMap 的原理是什么”
                - “帮我翻译这句话”
                - “今天星期几”

                ### list_modules 调用规则
                以下情况调用 list_modules：
                - start_task 没有匹配到模块，但任务明显和项目有关。
                - 需要确认当前知识库中有哪些模块。
                - finish_task 前不确定 modules_affected 应填写哪些模块。

                ### get_file 调用规则
                以下情况调用 get_file：
                - start_task 返回的上下文提示需要查看某个具体知识文件。
                - 用户明确要求查看 `.knowledge/` 下某个文件。
                - 需要精确读取某个模块的 AGENTS.md、SPEC.md、api.md 或 DESIGN.md。

                get_file 只用于读取 `.knowledge/` 下的知识文件，不用于读取业务源码。

                ### finish_task 调用规则
                finish_task 用于任务结束后的知识回流。只有在完成任务后再调用，不要在任务开始或任务中途调用。

                以下情况不需要回流：
                - 没有修改代码。
                - 只是阅读、解释、答疑，没有形成新的项目知识。
                - 只是运行命令、查看日志，且没有得到可复用经验。
                - 任务失败或中止，没有形成稳定结论。

                以下情况必须回流：
                - 修改了业务逻辑、接口行为、数据结构或配置。
                - 新增、删除或重构了模块。
                - 做了技术选型、架构调整或重要实现决策。
                - 遇到并解决了缺陷、兼容性问题、构建问题或部署问题。
                - 发现了以后必须注意的坑、边界条件或项目约定。

                ### finish_task 参数填写规则
                调用 finish_task 时，按以下方式填写：

                - summary：用一句中文总结本次任务做了什么。
                - decisions：填写关键决策数组，说明为什么这么做；没有则传空数组。
                - gotchas：填写踩坑、边界条件、排障经验；没有则传空字符串。
                - modules_affected：填写受影响模块名称数组。优先使用 start_task 或 list_modules 返回的模块名。

                示例：

                ```json
                {
                  "summary": "修复支付超时状态判断错误",
                  "decisions": ["将超时判断集中在 payment 模块，避免调用方重复实现"],
                  "gotchas": "支付网关返回 pending 不代表失败，需要等待回调或轮询最终状态。",
                  "modules_affected": ["payment"]
                }
                ```

                ### 工具失败时的处理
                - 如果 start_task 提示知识库未初始化，告知用户需要先执行 init_project。
                - 如果模块未匹配到，但任务仍与项目相关，调用 list_modules 辅助判断。
                - 如果知识文件为空或只有占位内容，继续阅读项目源码完成任务。
                - 不要因为 MCP 工具返回空上下文就放弃处理用户任务。

                ### 重要约束
                - init_project 通常只需要用户或维护者手动调用一次。
                - AI 助手不要在每个任务里重复调用 init_project，除非用户明确要求重新初始化。
                - 不要把临时过程、猜测和无效尝试写入知识库。
                - 回流内容必须是中文，简洁、稳定、可复用。
                - 知识库是辅助上下文，不替代对实际代码的阅读和验证。
                """.formatted(
                project.projectName(),
                project.type().displayName(),
                project.techStack(),
                markdownList(knowledge == null ? List.of(project.techStack()) : knowledge.techStack()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未执行静态分析 -->") : knowledge.globalConstraints()),
                moduleMap(knowledge)
        );
    }

    public String globalAgents(ProjectScanResult project) {
        return globalAgents(project, null);
    }

    public String globalAgents(ProjectScanResult project, StaticKnowledgeAnalyzer.ProjectKnowledge knowledge) {
        return """
                # 全局项目知识

                ## 项目信息
                - 项目名称: %s
                - 项目类型: %s
                - 技术栈: %s

                ## 全局编码规范
                %s

                ## 公共基类和工具类清单
                %s
                """.formatted(
                project.projectName(),
                project.type().displayName(),
                project.techStack(),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未执行静态分析 -->") : knowledge.globalCodeStyle()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未执行静态分析 -->") : knowledge.globalConstraints())
        );
    }

    public String moduleAgents(String moduleName, ProjectType type) {
        return moduleAgents(moduleName, type, null);
    }

    public String moduleAgents(String moduleName, ProjectType type, StaticKnowledgeAnalyzer.ModuleKnowledge knowledge) {
        return """
                # 模块：%s

                ## Entry Points
                ### Controller
                %s

                ### Service
                %s

                ### Mapper
                %s

                ## Database Schema
                %s

                ## Hard Constraints
                %s

                ## Code Style
                %s

                ## Pitfalls

                ## Change Log

                ## 项目类型
                %s
                """.formatted(
                moduleName,
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现 Controller -->") : knowledge.controllers()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现 Service -->") : knowledge.services()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现 Mapper -->") : knowledge.mappers()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现数据库实体或表名 -->") : knowledge.databaseSchema()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现模块约束 -->") : knowledge.hardConstraints()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现代码风格 -->") : knowledge.codeStyle()),
                type.displayName()
        );
    }

    public String moduleSpec(String moduleName) {
        return moduleSpec(moduleName, null);
    }

    public String moduleSpec(String moduleName, StaticKnowledgeAnalyzer.ModuleKnowledge knowledge) {
        return """
                # 规格说明：%s

                ## 模块职责
                %s

                ## 业务规则
                %s

                ## 状态流转
                %s

                ## 关联模块
                %s

                ## Change Log
                - %s
                """.formatted(
                moduleName,
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现可验证的模块职责 -->") : knowledge.responsibilities()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现可验证的业务规则 -->") : knowledge.businessRules()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现状态流转 -->") : knowledge.stateTransitions()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现关联模块 -->") : knowledge.relatedModules()),
                knowledge == null ? LocalDateTime.now().toLocalDate() + " 初始化：模块创建" : knowledge.changeLog()
        );
    }

    public String api(String moduleName) {
        return api(moduleName, null);
    }

    public String api(String moduleName, StaticKnowledgeAnalyzer.ModuleKnowledge knowledge) {
        return """
                # 接口说明：%s

                ## 接口列表
                %s

                ## 集成点
                <!-- TODO: 未从 Controller 方法签名中识别外部系统、事件、队列或定时任务。 -->
                """.formatted(moduleName, apiTable(knowledge));
    }

    public String design(String moduleName) {
        return """
                # 设计说明：%s

                ## 页面与视图
                待补充：记录页面、视图和用户交互。

                ## 状态与体验规则
                待补充：记录本地状态、加载状态、权限和边界情况。
                """.formatted(moduleName);
    }

    public String tasksReadme() {
        return """
                # 任务记录

                该目录用于保存 finish_task() 写入的已完成任务记录。
                """;
    }

    public String taskRecord(String summary, List<String> decisions, String gotchas, List<String> modulesAffected) {
        return """
                # %s

                ## 时间
                %s

                ## 任务摘要
                %s

                ## 关键决策
                %s

                ## 踩坑记录
                %s

                ## 影响模块
                %s
                """.formatted(
                summary,
                LocalDateTime.now(),
                emptyToPlaceholder(summary),
                listOrPlaceholder(decisions),
                emptyToPlaceholder(gotchas),
                listOrPlaceholder(modulesAffected)
        );
    }

    private String listOrPlaceholder(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            if (item != null && !item.isBlank()) {
                builder.append("- ").append(item.trim()).append(System.lineSeparator());
            }
        }
        return builder.isEmpty() ? "无" : builder.toString().stripTrailing();
    }

    private String emptyToPlaceholder(String value) {
        return value == null || value.isBlank() ? "无" : value.trim();
    }

    private String markdownList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "- <!-- TODO -->";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : items) {
            builder.append("- ").append(item).append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }

    private String moduleMap(StaticKnowledgeAnalyzer.ProjectKnowledge knowledge) {
        if (knowledge == null || knowledge.modules().isEmpty()) {
            return "- <!-- TODO: 未识别到模块 -->";
        }
        StringBuilder builder = new StringBuilder();
        for (StaticKnowledgeAnalyzer.ModuleKnowledge module : knowledge.modules()) {
            builder.append("- ").append(module.name()).append("：").append(module.path()).append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }

    private String apiTable(StaticKnowledgeAnalyzer.ModuleKnowledge knowledge) {
        if (knowledge == null || knowledge.apiEndpoints().isEmpty()) {
            return "<!-- TODO: 未发现 Controller 接口 -->";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("| 来源 | HTTP 方法 | Path | Java 方法 | 入参 | 出参 |").append(System.lineSeparator());
        builder.append("| --- | --- | --- | --- | --- | --- |").append(System.lineSeparator());
        for (StaticKnowledgeAnalyzer.ApiEndpoint endpoint : knowledge.apiEndpoints()) {
            builder.append("| ")
                    .append(endpoint.sourceFile()).append(" | ")
                    .append(endpoint.method()).append(" | ")
                    .append(endpoint.path()).append(" | ")
                    .append(endpoint.javaMethod()).append(" | ")
                    .append(endpoint.parameters().replace("|", "\\|")).append(" | ")
                    .append(endpoint.returnType().replace("|", "\\|")).append(" |")
                    .append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }
}
