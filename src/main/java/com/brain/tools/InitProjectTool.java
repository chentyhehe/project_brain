package com.brain.tools;

import com.brain.knowledge.StaticKnowledgeAnalyzer;
import com.brain.scanner.ModuleInfo;
import com.brain.scanner.ProjectScanResult;
import com.brain.scanner.ProjectScanner;
import com.brain.scanner.ProjectType;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class InitProjectTool implements BrainTool {
    private final ProjectScanner scanner = new ProjectScanner();
    private final StaticKnowledgeAnalyzer analyzer = new StaticKnowledgeAnalyzer();

    @Override
    public McpSchema.Tool tool() {
        Map<String, Object> props = ToolSupport.properties(
                "project_path", ToolSupport.stringProperty("项目根目录的绝对路径或相对路径。"),
                "type", Map.of(
                        "type", "string",
                        "description", "可选的项目类型，无法自动识别时手动指定。",
                        "enum", List.of("backend", "frontend")),
                "overwrite", Map.of(
                        "type", "boolean",
                        "description", "是否要求大模型覆盖已存在的知识文件。默认 false。")
        );
        return ToolSupport.tool("init_project", "生成大模型初始化提示词",
                "扫描项目基础信息，生成一份可交给当前 AI 助手执行的中文知识库初始化 prompt。该工具本身不调用大模型。",
                props,
                List.of("project_path"));
    }

    @Override
    public McpSchema.CallToolResult call(Map<String, Object> arguments) {
        try {
            Path projectPath = Path.of(ToolSupport.requiredString(arguments, "project_path"));
            ProjectType requestedType = ProjectType.from(ToolSupport.optionalString(arguments, "type"));
            boolean overwrite = ToolSupport.optionalBoolean(arguments, "overwrite", false);
            ProjectScanResult project = scanner.scan(projectPath, requestedType);
            StaticKnowledgeAnalyzer.ProjectKnowledge knowledge = analyzer.analyze(project);
            return ToolSupport.textResult(buildPrompt(project, knowledge, overwrite));
        } catch (Exception exception) {
            return ToolSupport.errorResult(exception);
        }
    }

    private String buildPrompt(ProjectScanResult project, StaticKnowledgeAnalyzer.ProjectKnowledge knowledge, boolean overwrite) {
        return """
                请作为当前项目的代码分析助手，扫描项目结构并初始化该项目知识库。
                输出和写入内容请优先使用中文。

                重要边界：
                - 只写能从现有代码、配置、注释、类型签名、方法签名、路由定义、依赖关系中直接验证的内容。
                - 不要猜测；无法确定的内容使用 `<!-- TODO -->` 标注。
                - 如果文件已存在，overwrite=%s；当 overwrite=false 时，不要覆盖已有人工维护内容，只补充缺失文件或缺失章节。
                - 最后列出创建或更新了哪些文件。

                # AGENTS.md 使用中文作为首选项进行输出

                ## 项目概览
                - 项目名称：%s
                - 项目类型：%s
                - 主要技术栈：%s
                - 知识库路径：.knowledge/

                ## 知识库结构
                ```text
                .knowledge/
                ├── global/
                │   └── AGENTS.md        ← 全局规范、架构决策
                ├── modules/
                │   └── <module>/
                │       ├── AGENTS.md    ← 模块技术约束、踩坑
                │       ├── SPEC.md      ← 模块业务逻辑
                │       ├── api.md       ← 接口文档（backend）
                │       └── DESIGN.md    ← 页面设计（frontend）
                └── tasks/
                    └── README.md        ← 任务回流使用说明
                ```

                ---

                ## How to Work

                ### 第一步：判断任务类型

                每次收到用户输入，首先判断：

                **无需进入项目流程的情况**（直接回答，不调用任何工具）：
                - 闲聊、问候（你好、你是谁、谢谢等）
                - 与当前项目无关的概念解释或通用问题
                - 用户明确说“随便问问”、“不用管项目”

                **需要进入项目流程的情况**：
                - 涉及当前项目的功能开发、bug 修复、重构
                - 询问当前项目的某个模块、接口、业务逻辑
                - 需要对项目代码做出决策或建议

                ---

                ### 第二步：项目任务执行流程

                判断为项目任务后，严格按以下顺序执行：

                **1. 加载上下文**
                调用 start_task(task_description)
                - task_description 用一句话概括用户的意图
                - 示例：“修复支付模块超时问题”、“新增用户导出 Excel 功能”
                - 拿到返回的上下文后，仔细阅读再开始工作

                **2. 执行任务**
                - 基于上下文中的规范和约束来实现
                - 如发现上下文不足，调用 get_file(file_path) 按需补充
                - 遇到模块不确定时，调用 list_modules() 确认范围

                **3. 完成后判断是否需要回流**

                需要回流的情况（调用 finish_task）：
                - 修改了业务逻辑或数据结构
                - 做了技术选型或架构决策
                - 遇到并解决了 bug 或踩坑
                - 新增了接口或修改了接口契约

                不需要回流的情况：
                - 只是解释代码，没有实际修改
                - 纯粹的文档阅读
                - 任务未完成或用户放弃

                **4. 执行知识回流**
                调用 finish_task(summary, decisions, gotchas, modules_affected)

                参数填写规范：
                - summary：一句话描述做了什么，如“修复了支付模块在高并发下的超时问题”
                - decisions：关键决策和原因，如“选择乐观锁而非悲观锁，原因是读多写少”
                - gotchas：踩坑记录，没有则传空字符串
                - modules_affected：实际修改涉及的模块名数组

                ⚠️ finish_task 是任务的最后一步，完成后才算整个流程结束。

                ---

                ### 流程速查
                ```text
                用户输入
                  ├─ 非项目任务：直接回答
                  └─ 项目任务：
                       1. start_task(task_description)
                       2. 阅读上下文并执行任务
                       3. 必要时 get_file(file_path) / list_modules()
                       4. 判断是否需要回流
                       5. 如需回流，finish_task(summary, decisions, gotchas, modules_affected)
                ```

                ---

                ## MCP 静态扫描结果

                ### 项目基础信息
                - 项目路径：%s
                - 项目名称：%s
                - 项目类型：%s
                - 初步技术栈：%s

                ### 技术栈和依赖
                %s

                ### 全局约束线索
                %s

                ### 模块列表
                %s

                ### 当前项目类型的扫描重点
                %s

                ---

                ## 初始化执行要求

                1. 读取项目根目录，识别技术栈、版本、模块划分和全局规律。
                2. 填写根目录 `AGENTS.md`，必须包含：
                   - 项目概览
                   - 知识库结构
                   - How to Work
                   - MCP 静态扫描得到的项目事实和模块地图
                3. 填写 `.knowledge/global/AGENTS.md`，记录全局编码规范、公共基础设施、架构约束和可验证决策。
                4. 为每个识别到的模块创建或更新 `.knowledge/modules/<module>/AGENTS.md`，记录：
                   - Entry Points
                   - Data Model / Database Schema
                   - Hard Constraints
                   - Code Style
                   - Pitfalls
                   - Change Log
                5. 为每个模块创建或更新 `.knowledge/modules/<module>/SPEC.md`，记录：
                   - 模块职责
                   - 业务规则
                   - 状态流转
                   - 关联模块
                   - Change Log
                6. 后端模块创建或更新 `api.md`；前端模块创建或更新 `DESIGN.md`。
                7. 填写 `.knowledge/tasks/README.md`，说明任务回流文件由 `finish_task` 写入。
                8. 输出最终汇总：列出创建或更新的文件，并标注仍需人工补充的 TODO。
                """.formatted(
                overwrite,
                project.projectName(),
                typeLabel(project.type()),
                project.techStack(),
                project.projectRoot(),
                project.projectName(),
                typeLabel(project.type()),
                project.techStack(),
                markdownList(knowledge.techStack()),
                markdownList(knowledge.globalConstraints()),
                moduleList(project),
                projectSpecificGuidance(project)
        );
    }

    private String projectSpecificGuidance(ProjectScanResult project) {
        Path root = project.projectRoot();
        if (project.type() == ProjectType.FRONTEND) {
            return """
                    - 这是前端项目，优先分析 package.json、src/pages、src/views、src/routes、src/router、src/components、src/stores、src/api、hooks/composables。
                    - 模块按页面、视图、业务 feature 或 route 分组，不要强行使用后端 Controller/Service/Mapper 术语。
                    - 重点提取路由、页面职责、组件层级、状态管理、API 调用、表单校验、权限控制、加载/错误/空状态。
                    - 前端模块通常生成 AGENTS.md、SPEC.md、DESIGN.md；只有项目中存在本地 API route 或 server action 时才生成 api.md。
                    """;
        }
        if (has(root, "pyproject.toml") || has(root, "requirements.txt") || has(root, "setup.py")) {
            return """
                    - 这是 Python 后端或 Python 工程，优先分析 pyproject.toml、requirements.txt、setup.py、src、app、tests。
                    - 如果是 FastAPI，重点读取 APIRouter、app.get/post/put/delete、Depends、Pydantic BaseModel。
                    - 如果是 Flask，重点读取 Blueprint、app.route、request/response、schemas/models/services。
                    - 如果是 Django，重点读取 urls.py、views.py、models.py、serializers.py、settings.py。
                    - 模块按 Python package 或业务目录划分，入口包括 router/view/endpoint/service/repository/model/schema。
                    """;
        }
        if (has(root, "pom.xml") || has(root, "*.java")) {
            return """
                    - 这是 Java 后端项目，优先分析 pom.xml/build.gradle、src/main/java、src/main/resources。
                    - 重点读取 Controller、Service、Mapper/Repository、Entity/DO/DTO/VO、Config、Exception、Interceptor。
                    - 从 Spring MVC 注解、事务注解、校验注解、统一响应类、全局异常处理类中提取约束；如果项目不是 Spring 服务，应明确记录实际入口。
                    - 后端模块通常生成 AGENTS.md、SPEC.md、api.md。
                    """;
        }
        return """
                - 未能明确细分技术栈，请先读取根目录依赖和入口文件，再根据实际框架选择扫描策略。
                - 不要套用 Java 后端结构；按项目真实目录和入口组织知识库。
                """;
    }

    private boolean has(Path root, String file) {
        return Files.exists(root.resolve(file));
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

    private String moduleList(ProjectScanResult project) {
        if (project.modules().isEmpty()) {
            return "- <!-- TODO: MCP 静态扫描未识别到模块 -->";
        }
        StringBuilder builder = new StringBuilder();
        for (ModuleInfo module : project.modules()) {
            builder.append("- ").append(module.name()).append("：").append(module.path()).append(System.lineSeparator());
        }
        return builder.toString().stripTrailing();
    }

    private String typeLabel(ProjectType type) {
        return switch (type) {
            case BACKEND -> "backend";
            case FRONTEND -> "frontend";
        };
    }
}
