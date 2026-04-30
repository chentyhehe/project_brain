package com.brain.template;

import com.brain.knowledge.StaticKnowledgeAnalyzer;
import com.brain.scanner.ProjectScanResult;
import com.brain.scanner.ProjectType;

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
                - project-brain 默认维护 `.knowledge_local` 灾备副本，日常读取仍以 `.knowledge` 为主。
                - 当启用 `hybrid-chroma` 时，只有在 Chroma 和 embedding 同时健康时才允许进入向量增强模式。
                - `sync_to_chroma`、`compact_knowledge`、`restore_knowledge`、`restore_chroma`、`rebuild_knowledge` 分别负责补录、精简、恢复与重建，不要混用职责。

                ## Module Map
                %s

                ## How to Work
                <!-- TODO: 由维护者补充与业务域相关的固定工作方式 -->

                ## Project Brain MCP 工作协议
                - 项目相关任务开始前优先调用 `start_task`。
                - 如需记录过程计划、进展、踩坑，使用 `record_plan`、`record_progress`、`record_gotcha`。
                - 如需历史补录到 Chroma，使用 `sync_to_chroma`。
                - 如需控制主知识目录体积，使用 `compact_knowledge`。
                - 如需从灾备副本恢复或重建，使用 `restore_knowledge`、`restore_chroma`、`rebuild_knowledge`。
                - 所有工具返回都应关注 `store_mode`、`store_status`、`degrade_reason`。
                """.formatted(
                project.projectName(),
                project.type().displayName(),
                project.techStack(),
                markdownList(knowledge == null ? List.of(project.techStack()) : knowledge.techStack()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充全局约束 -->") : knowledge.globalConstraints()),
                moduleMap(knowledge));
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

                ## 公共基础设施清单
                %s

                ## 存储与恢复约定
                - `.knowledge` 保存当前生效规则和精简后的主知识。
                - `.knowledge_local` 保存完整灾备副本，默认不参与日常读取。
                - Chroma 保存事件、摘要和向量索引，依赖健康检查门控。
                """.formatted(
                project.projectName(),
                project.type().displayName(),
                project.techStack(),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充全局编码规范 -->") : knowledge.globalCodeStyle()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充公共基础设施 -->") : knowledge.globalConstraints()));
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

                ### Service / Core Classes
                %s

                ### Mapper
                %s

                ## Data Model / Database Schema
                %s

                ## Hard Constraints
                %s

                ## Code Style
                %s

                ## Pitfalls
                - 如本模块沉淀了过程事件或任务记录，补录与恢复时要同时考虑 `.knowledge_local`、manifest 与 Chroma 状态。

                ## Change Log
                - %s 初始化：模块创建

                ## 项目类型
                %s
                """.formatted(
                moduleName,
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现 Controller。本模块可能不是 HTTP 接口模块。 -->") : knowledge.controllers()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充核心类或服务类 -->") : knowledge.services()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现 Mapper -->") : knowledge.mappers()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 未发现数据库实体或表结构 -->") : knowledge.databaseSchema()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充模块约束 -->") : knowledge.hardConstraints()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充代码风格 -->") : knowledge.codeStyle()),
                LocalDateTime.now().toLocalDate(),
                type.displayName());
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
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充模块职责 -->") : knowledge.responsibilities()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充业务规则 -->") : knowledge.businessRules()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充状态流转 -->") : knowledge.stateTransitions()),
                markdownList(knowledge == null ? List.of("<!-- TODO: 待补充关联模块 -->") : knowledge.relatedModules()),
                knowledge == null ? LocalDateTime.now().toLocalDate() + " 初始化：模块创建" : knowledge.changeLog());
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
                <!-- TODO: 待补充外部系统、事件、队列、定时任务等集成点 -->
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

                该目录用于保存 `finish_task()` 写入的已完成任务记录。
                当启用 Chroma + compact 流程后，主任务文件可能被精简为“摘要 + archive_ref + backup_ref”结构。
                `.knowledge_local/` 用于保存同步的本地灾备副本，默认不参与日常读取，只用于恢复、补录和重建。
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
                emptyToPlaceholder(summary),
                LocalDateTime.now(),
                emptyToPlaceholder(summary),
                listOrPlaceholder(decisions),
                emptyToPlaceholder(gotchas),
                listOrPlaceholder(modulesAffected));
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
