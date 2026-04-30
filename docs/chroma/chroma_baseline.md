# Chroma 改造基线笔记

## 目标

- 记录当前 `project_brain` 在知识读写上的现状职责边界。
- 为 `docs/chroma/chroma_plan.md` 的 Phase 1 提供可直接引用的基线。

## 当前核心类职责

### `ContextLoader`

- 负责从项目根目录读取根 `AGENTS.md`、`.knowledge/global/AGENTS.md` 和匹配模块下的 Markdown 文件。
- 负责 `ensureInitialized`，要求项目已存在 `.knowledge`。
- 负责列出模块、按任务描述匹配模块、读取模块 `SPEC.md`。
- 现状特点：它是“读取入口 + 模块匹配 + 初始化校验”的组合体。

### `FlowbackWriter`

- 负责写入 `.knowledge/tasks/*.md` 任务记录。
- 负责在 `gotchas` 非空时，把踩坑追加到对应模块 `AGENTS.md`。
- 负责基于日期和摘要生成任务文件名，并处理重名。
- 现状特点：它是“任务完成后的本地回流写入器”。

### `KnowledgeWriter`

- 负责初始化 `.knowledge` 目录结构。
- 负责写根 `AGENTS.md`、全局知识、模块知识、任务 README。
- 负责结合 `StaticKnowledgeAnalyzer` 结果生成初始化文件内容。
- 现状特点：它是“初始化写入器”，不参与日常任务读写。

## 当前边界问题

### 1. 读写能力没有统一抽象

- `ContextLoader` 负责读。
- `FlowbackWriter` 负责任务结束写。
- `KnowledgeWriter` 负责初始化写。
- 三者都直接面向文件系统，没有统一协调器。

### 2. 没有运行模式概念

- 当前只有本地 Markdown 路径。
- 没有 `local-md` / `hybrid-chroma` 的模式分发层。

### 3. 没有灾备层

- 当前 `.knowledge` 既是主知识目录，也是唯一落盘位置。
- 还没有 `.knowledge_local` 这一层。

### 4. 没有过程型结构化沉淀

- 当前只有 `start_task` 的读取和 `finish_task` 的结果回流。
- 缺少 `record_plan`、`record_progress`、`record_gotcha`。

## 对后续抽象的直接启发

- `ContextLoader` 更适合被吸收到“主知识读取层”。
- `FlowbackWriter` 更适合被吸收到“主知识写入层 + 灾备同步层”。
- `KnowledgeWriter` 继续保留为“初始化器”，但要补 `.knowledge_local` 初始化能力。
- 后续需要新增一个运行时协调器，统一决定：
- 当前模式是什么
- 是否可写 Chroma
- 是否需要写 `.knowledge_local`
- 是否允许补录或精简
