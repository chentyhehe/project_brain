---
name: project-brain-workflow
description: 约束 project-brain MCP 的标准调用顺序、降级判断与恢复流程。处理当前仓库内与代码、架构、模块、配置、调试、方案设计相关的任务时使用；当需要先调用 start_task、根据 store_mode/store_status 判断是否补录到 Chroma、执行 compact_knowledge、restore_knowledge、restore_chroma 或 rebuild_knowledge 时使用。
---

# Project Brain Workflow

## Overview

遵循项目根 `AGENTS.md` 中的 project-brain 工作协议，始终先建立项目知识上下文，再执行代码任务，最后只在形成稳定知识时回流。

这个 skill 只约束调用顺序和判断标准，不替代对源码的阅读，也不承载底层存储实现细节。

## Workflow

### 1. Start With `start_task`

- 只要任务与当前项目代码、架构、模块、配置、调试、排障、方案设计有关，优先调用 `start_task`
- `task_description` 默认直接使用用户原始请求；如果用户先前已经澄清边界，则改用整理后的任务确认单
- 若返回提示知识库未初始化，再考虑 `init_project`

### 2. Read Runtime Storage State

每次工具返回后，都读取运行时存储状态：

- `store_mode`
- `store_status`
- `degrade_reason`
- `chroma_enabled`
- `backup_enabled`

处理规则：

- `store_mode=local-md` 且 `store_status=AVAILABLE`：按本地知识目录正常工作
- `store_status=DEGRADED`：不要假设 Chroma 或 embedding 可用，避免把向量能力当成前提
- `degrade_reason` 为 `MISSING_EMBEDDING_CONFIG`、`EMBEDDING_UNHEALTHY`、`CHROMA_UNHEALTHY` 或 `NETWORK_TIMEOUT` 时，继续完成主任务，但把补录、恢复、重建视为显式动作

### 3. Execute The Main Task

- 优先阅读 `start_task` 返回的知识上下文，再结合源码完成用户任务
- 若需要记录过程沉淀，使用：
  - `record_plan`
  - `record_progress`
  - `record_gotcha`
- 如需确认模块范围，可调用 `list_modules`
- 如需精确查看知识文件，可调用 `get_file`

## Recovery And Chroma Actions

### Use `sync_to_chroma` When

- 当前任务前期在 `local-md` 或 `DEGRADED` 状态下进行了沉淀
- Chroma / embedding 恢复健康后，需要把历史 `.knowledge` 或 `.knowledge_local` 内容补录入库
- 需要显式补录指定文件或执行全量历史补录

### Use `compact_knowledge` When

- 任务记录已经安全入库
- 需要压缩 `.knowledge/tasks/*.md` 的大文本
- 可以接受主知识目录变成“摘要 + archive_ref + backup_ref”形式

### Use `restore_knowledge` When

- `.knowledge` 被误删、误改或被错误精简
- 需要从 `.knowledge_local` 恢复主知识目录

### Use `restore_chroma` When

- Chroma 被清空、切库或命名空间重建
- 需要从 `.knowledge_local` 重新构建向量库

### Use `rebuild_knowledge` When

- 需要同时恢复 `.knowledge`、重建 Chroma，并重新生成精简后的主知识目录
- 这是最重的恢复动作，优先用于灾难恢复或大规模整理

## Finish Rules

- 只有在形成稳定、可复用的项目知识时才调用 `finish_task`
- 仅阅读、解释、临时排查且没有新结论时，不调用 `finish_task`
- `finish_task` 的 `summary`、`decisions`、`gotchas`、`modules_affected` 一律写中文

## Guardrails

- 不把 `.knowledge_local` 当作日常读取主来源；它默认只用于灾备、补录与重建
- 不因为 Chroma 不可用就停止主任务；降级到 `local-md` 后继续完成需求
- 不把完整模型上下文原样入库；优先沉淀结构化摘要
- 不跳过 `start_task` 直接做项目任务，除非用户明确要求不要读项目知识库
