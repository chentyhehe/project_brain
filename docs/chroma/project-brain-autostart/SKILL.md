---
name: project-brain-autostart
description: 在接入了 project-brain MCP 的仓库中，自动约束项目任务优先调用 `start_task`，并在任务结束时按规则决定是否调用 `finish_task`。当请求涉及当前项目代码、架构、模块、配置、调试、排障、重构或方案设计时使用；当需要避免跳过知识上下文直接动手改项目时使用。
---

# Project Brain Autostart

## Overview

把 `project-brain` MCP 变成项目任务的默认起点，避免在项目相关请求里直接跳过知识上下文。

这个 skill 只负责“先调什么、什么时候该回流”，不负责 Chroma 存储细节；更细的 Chroma 处理交给 `project-brain-workflow`。

## Workflow Decision Tree

### 1. Decide Whether The Task Is Project-Related

直接回答、不调用 MCP 的情况：

- 闲聊
- 翻译
- 通用概念解释
- 与当前仓库代码、架构、模块、配置无关的问题
- 用户明确要求不要读取项目知识库

必须进入 project-brain MCP 流程的情况：

- 阅读或修改当前项目代码
- 解释当前项目架构或模块职责
- 调试、排障、重构、方案设计
- 分析当前仓库的接口、配置、知识沉淀、Chroma 工作流

### 2. Start With `start_task`

- 对所有项目相关任务，优先调用 `start_task`
- `task_description` 默认直接使用用户原始请求
- 如果用户先澄清过边界，再用整理后的确认单作为描述
- 不因为知识库内容少就停止，继续读源码完成任务

### 3. Use Supporting Tools Only When Needed

- 需要计划沉淀时调用 `record_plan`
- 需要记录阶段进展时调用 `record_progress`
- 需要记录坑点时调用 `record_gotcha`
- 需要查看模块列表时调用 `list_modules`
- 需要精确查看 `.knowledge` 文件时调用 `get_file`

### 4. Finish Only When Knowledge Is Stable

以下情况调用 `finish_task`：

- 修改了业务逻辑、接口行为、数据结构或配置
- 做了技术选型、架构调整或重要实现决策
- 解决了缺陷、兼容性问题、构建问题或部署问题
- 形成了稳定、可复用的坑点或约束

以下情况不调用 `finish_task`：

- 只做了阅读和解释
- 只是临时排查，没有稳定结论
- 没有修改代码，也没有新增可复用知识

## Guardrails

- 不在项目任务里跳过 `start_task`
- 不把 `.knowledge_local` 当日常主读源
- 不把无效尝试、猜测和临时过程回流到知识库
- 需要处理 `store_mode/store_status` 时，继续使用 `project-brain-workflow`
