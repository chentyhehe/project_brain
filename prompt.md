# 项目：project-brain MCP Server - MVP 第一期

## 项目定位
这是一个 MCP Server，不调用任何 LLM。
它为 AI 工具（Claude Code、Codex 等）提供项目知识管理能力。
AI 工具通过 MCP 协议调用这些工具，自己决定何时调用。

## MCP 工具清单

### init_project(project_path, type?)
初始化项目知识库，在 project_path 下生成 .knowledge/ 目录结构。
- type 可选值：backend / frontend，不传则自动识别
- 自动识别逻辑：
  - 有 pom.xml / build.gradle / pyproject.toml / *.py / *.java → backend
  - 有 package.json 且 dependencies 含 react/vue/angular/next/nuxt → frontend
  - 有 package.json 其他情况 → backend
  - 无法判断 → 返回错误，提示手动指定 type
- 生成目录结构：

  project-root/
  ├── AGENTS.md
  └── .knowledge/
      ├── global/
      │   └── AGENTS.md
      ├── modules/
      │   └── <module_name>/
      │       ├── AGENTS.md
      │       ├── SPEC.md
      │       ├── api.md        ← 仅 backend
      │       └── DESIGN.md     ← 仅 frontend
      └── tasks/
          └── README.md

- 模块识别规则：
  - backend：src/main/java 下一级包为模块，忽略 common/util/config/base
  - frontend：src/pages 或 src/views 下一级目录为模块，忽略 components/utils/hooks/assets
- 所有 .md 文件初始化时写入占位内容（结构模板），不调用 LLM
- 返回：生成的文件列表 + 识别到的模块列表

### start_task(task_description)
任务开始时调用，返回与当前任务相关的上下文。
- 根据 task_description 匹配相关模块
  - 关键词匹配模块名
  - 读取该模块下所有 .md 文件内容
  - 读取 .knowledge/global/AGENTS.md
  - 读取根 AGENTS.md
- 返回：拼装好的上下文字符串，AI 直接用于后续对话

### finish_task(summary, decisions, gotchas, modules_affected)
任务完成时调用，执行知识回流。
- summary：本次任务做了什么
- decisions：关键决策（为什么这么做）
- gotchas：踩坑记录（可为空）
- modules_affected：影响了哪些模块（数组）
- 执行：
  - 在 .knowledge/tasks/ 下生成 <YYYYMMDD>-<summary>.md
  - 将 gotchas 追加写入对应模块的 AGENTS.md
  - 返回写入的文件路径列表

### list_modules()
返回当前项目所有模块名称及其路径，供 AI 判断任务涉及哪些模块。

### get_file(file_path)
读取 .knowledge/ 下指定文件内容，供 AI 按需精确加载。

## 工作协议（写入根 AGENTS.md 的内容模板）

```markdown
# AGENTS.md

## 项目概览
[项目名] - [类型: backend/frontend] - [技术栈]

## How to Work

### 判断任务类型
- 与当前项目代码/架构无关（闲聊、概念解释等）→ 直接回答，不调用任何工具
- 与当前项目相关 → 执行以下步骤

### 项目任务执行流程
1. 调用 start_task(task_description) 加载上下文
2. 基于返回的上下文执行任务
3. 任务完成后调用 finish_task() 回流知识

### 知识回流判断
以下情况不需要回流：
- 任务未涉及代码修改或架构决策
- 纯文档阅读、答疑

以下情况必须回流：
- 修改了业务逻辑
- 做了技术选型决策
- 遇到并解决了 bug 或踩坑
```

## 技术要求
- 语言：Java 17+
- 构建工具：Maven
- MCP 框架：mcp-sdk-java（https://github.com/modelcontextprotocol/java-sdk）
- 传输协议：stdio（标准输入输出，兼容 Claude Code 和 Codex）
- 不依赖任何 LLM SDK，不发起任何 HTTP 请求

## 项目结构
project_brain/
├── pom.xml
└── src/main/java/com/brain/
    ├── Main.java
    ├── server/
    │   └── BrainServer.java          # MCP Server 启动入口
    ├── tools/
    │   ├── InitProjectTool.java      # init_project 实现
    │   ├── StartTaskTool.java        # start_task 实现
    │   ├── FinishTaskTool.java       # finish_task 实现
    │   ├── ListModulesTool.java      # list_modules 实现
    │   └── GetFileTool.java          # get_file 实现
    ├── scanner/
    │   ├── ProjectScanner.java       # 类型识别 + 模块识别入口
    │   ├── BackendScanner.java       # Java 项目模块扫描
    │   └── FrontendScanner.java      # 前端项目模块扫描
    ├── knowledge/
    │   ├── KnowledgeWriter.java      # 生成 .knowledge/ 目录和文件
    │   ├── ContextLoader.java        # start_task 的上下文检索逻辑
    │   └── FlowbackWriter.java       # finish_task 的回流写入逻辑
    └── template/
        └── TemplateEngine.java       # 各 .md 文件的初始内容模板

## 实现顺序
请先输出整体设计方案和各类的职责说明，确认后再开始逐个实现。
顺序：
1. pom.xml + Main.java + BrainServer.java（MCP Server 跑通，能被 Claude Code 识别）
2. InitProjectTool + ProjectScanner + KnowledgeWriter（init 跑通，能生成目录结构）
3. StartTaskTool + ContextLoader（上下文加载跑通）
4. FinishTaskTool + FlowbackWriter（知识回流跑通）
5. ListModulesTool + GetFileTool（辅助工具）

## 验收标准
- 在 Claude Code 的 MCP 配置中加入此 server 后，能正常识别所有工具
- 对一个 Java 项目执行 init_project，能正确生成 .knowledge/ 结构
- 执行 start_task("修复支付超时问题")，能返回 payment 模块相关上下文
- 执行 finish_task()，能在 tasks/ 生成记录并更新模块 AGENTS.md
