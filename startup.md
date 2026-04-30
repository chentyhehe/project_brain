# project-brain Startup Guide

## 1. 这是什么

`project-brain` 是一个通过 `stdio` 启动的 MCP Server，不是 HTTP 服务。

- 入口类：`com.brain.Main`
- 打包产物：`target/project-brain-0.1.0.jar`
- 传输方式：`StdioServerTransportProvider`

它对外提供的是 MCP `tool`，不是 REST API。

---

## 2. 启动方式

### 2.1 构建

如果本机有 Maven：

```powershell
mvn package
```

构建完成后会生成：

```text
target/project-brain-0.1.0.jar
```

### 2.2 本地启动

```powershell
java -jar target/project-brain-0.1.0.jar
```

说明：

- 这是一个 `stdio` MCP Server，正常情况下不要手工在终端里直接交互。
- 一般由 Codex、Claude Code 之类的 MCP Client 通过命令拉起。

---

## 3. 配置方式

## 3.1 重要结论

当前实现没有单独的配置文件解析器。

也就是说，现在不是读：

- `application.yml`
- `application.properties`
- `.env`

当前只支持两种注入方式：

1. 环境变量
2. JVM `-D` 系统属性

代码入口见：

- [StoreConfigurationLoader.java](/F:/McpProjects/project_brain/src/main/java/com/brain/knowledge/runtime/StoreConfigurationLoader.java)

## 3.2 支持的配置项

### 基础模式

- `PROJECT_BRAIN_STORE_MODE`
- `project.brain.store.mode`

可选值：

- `local-md`
- `hybrid-chroma`

默认值：

- `local-md`

### Chroma

- `PROJECT_BRAIN_CHROMA_URL`
- `project.brain.chroma.url`
- `PROJECT_BRAIN_CHROMA_NAMESPACE`
- `project.brain.chroma.namespace`
- `PROJECT_BRAIN_CHROMA_TIMEOUT_MS`
- `project.brain.chroma.timeout-ms`

默认值：

- `namespace=project-brain`
- `timeout=3000`

### Embedding

- `PROJECT_BRAIN_EMBEDDING_PROVIDER`
- `project.brain.embedding.provider`
- `PROJECT_BRAIN_EMBEDDING_URL`
- `project.brain.embedding.url`
- `PROJECT_BRAIN_EMBEDDING_MODEL`
- `project.brain.embedding.model`
- `PROJECT_BRAIN_EMBEDDING_API_KEY`
- `project.brain.embedding.api-key`
- `PROJECT_BRAIN_EMBEDDING_TIMEOUT_MS`
- `project.brain.embedding.timeout-ms`

默认值：

- `timeout=3000`

## 3.3 hybrid-chroma 启用条件

只有以下条件同时满足，才会进入 `hybrid-chroma`：

1. `store_mode=hybrid-chroma`
2. Chroma URL 已配置
3. embedding 的 `provider/url/model` 已配置
4. Chroma 健康检查通过
5. embedding 健康检查通过

只要任一条件失败，就会自动降级为：

- `store_mode=local-md`
- `store_status=DEGRADED`

## 3.4 Windows 环境变量示例

```powershell
$env:PROJECT_BRAIN_STORE_MODE="hybrid-chroma"
$env:PROJECT_BRAIN_CHROMA_URL="http://192.168.6.93:8999"
$env:PROJECT_BRAIN_CHROMA_NAMESPACE="project-brain"
$env:PROJECT_BRAIN_CHROMA_TIMEOUT_MS="3000"

$env:PROJECT_BRAIN_EMBEDDING_PROVIDER="ollama"
$env:PROJECT_BRAIN_EMBEDDING_URL="http://127.0.0.1:11434"
$env:PROJECT_BRAIN_EMBEDDING_MODEL="nomic-embed-text"
$env:PROJECT_BRAIN_EMBEDDING_TIMEOUT_MS="3000"
```

如果 embedding 需要鉴权：

```powershell
$env:PROJECT_BRAIN_EMBEDDING_API_KEY="your-api-key"
```

## 3.5 JVM 参数示例

```powershell
java ^
  -Dproject.brain.store.mode=hybrid-chroma ^
  -Dproject.brain.chroma.url=http://192.168.6.93:8999 ^
  -Dproject.brain.chroma.namespace=project-brain ^
  -Dproject.brain.embedding.provider=ollama ^
  -Dproject.brain.embedding.url=http://127.0.0.1:11434 ^
  -Dproject.brain.embedding.model=nomic-embed-text ^
  -jar target/project-brain-0.1.0.jar
```

---

## 4. MCP Tool 调用方式

所有工具都注册在：

- [BrainServer.java](/F:/McpProjects/project_brain/src/main/java/com/brain/server/BrainServer.java)

## 4.1 日常工作流工具

### `init_project`

用途：

- 初始化 `.knowledge`
- 首次建立知识目录

### `start_task`

用途：

- 项目相关任务开始前必须先调用

最小参数：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "task_description": "修复 xxx 问题"
}
```

### `record_plan`

用途：

- 记录任务计划到 `.knowledge_local`

示例：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "plan_summary": "拆分实现步骤",
  "steps": ["先定位", "再修改", "最后验证"],
  "modules_affected": ["knowledge"]
}
```

### `record_progress`

用途：

- 记录过程进展

### `record_gotcha`

用途：

- 记录踩坑与边界条件

### `finish_task`

用途：

- 任务完成后回流稳定知识

---

## 4.2 Chroma / 灾备相关工具

### `sync_to_chroma`

用途：

- 手动把 `.knowledge` / `.knowledge_local` 中尚未入库的内容补录到 Chroma

全量补录：

```json
{
  "project_path": "F:\\McpProjects\\project_brain"
}
```

指定文件补录：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "source_paths": [
    ".knowledge_local/events/xxx.md",
    ".knowledge/tasks/xxx.md"
  ]
}
```

对应实现：

- [SyncToChromaTool.java](/F:/McpProjects/project_brain/src/main/java/com/brain/tools/SyncToChromaTool.java)

### `compact_knowledge`

用途：

- 精简 `.knowledge/tasks/*.md`

示例：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "threshold_kb": 1024,
  "force": false
}
```

### `restore_knowledge`

用途：

- 从 `.knowledge_local` 恢复 `.knowledge`

示例：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "overwrite": true,
  "include_root_agents": true
}
```

### `restore_chroma`

用途：

- 从 `.knowledge_local` 重建 Chroma

示例：

```json
{
  "project_path": "F:\\McpProjects\\project_brain"
}
```

### `rebuild_knowledge`

用途：

- 恢复 `.knowledge`
- 重建 Chroma
- 再重新 compact 主知识目录

示例：

```json
{
  "project_path": "F:\\McpProjects\\project_brain",
  "overwrite": true,
  "include_root_agents": true,
  "threshold_kb": 1024,
  "force_compact": true
}
```

---

## 5. 如何接入 Codex

## 5.1 推荐方式：CLI 添加 MCP Server

OpenAI Codex 官方文档支持：

- `codex mcp add <server-name> --env VAR=VALUE -- <stdio-command>`

项目接入示例：

```powershell
codex mcp add project-brain `
  --env PROJECT_BRAIN_STORE_MODE=hybrid-chroma `
  --env PROJECT_BRAIN_CHROMA_URL=http://192.168.6.93:8999 `
  --env PROJECT_BRAIN_CHROMA_NAMESPACE=project-brain `
  --env PROJECT_BRAIN_EMBEDDING_PROVIDER=ollama `
  --env PROJECT_BRAIN_EMBEDDING_URL=http://127.0.0.1:11434 `
  --env PROJECT_BRAIN_EMBEDDING_MODEL=nomic-embed-text `
  -- java -jar F:\McpProjects\project_brain\target\project-brain-0.1.0.jar
```

添加后可用：

- `codex mcp --help`
- Codex TUI 里的 `/mcp`

## 5.2 手动写 Codex 配置

官方配置参考支持 `mcp_servers.<id>.command`、`args`、`env`、`cwd`。

可在 `~/.codex/config.toml` 中写：

```toml
[mcp_servers.project-brain]
command = "java"
args = ["-jar", "F:\\McpProjects\\project_brain\\target\\project-brain-0.1.0.jar"]
cwd = "F:\\McpProjects\\project_brain"
startup_timeout_ms = 20000
tool_timeout_sec = 120

[mcp_servers.project-brain.env]
PROJECT_BRAIN_STORE_MODE = "hybrid-chroma"
PROJECT_BRAIN_CHROMA_URL = "http://192.168.6.93:8999"
PROJECT_BRAIN_CHROMA_NAMESPACE = "project-brain"
PROJECT_BRAIN_EMBEDDING_PROVIDER = "ollama"
PROJECT_BRAIN_EMBEDDING_URL = "http://127.0.0.1:11434"
PROJECT_BRAIN_EMBEDDING_MODEL = "nomic-embed-text"
```

## 5.3 Codex 的 skill

当前建议至少安装两个 skill：

1. `project-brain-autostart`
   - 负责在项目任务开始时优先调用 `start_task`
2. `project-brain-workflow`
   - 负责根据 `store_mode/store_status` 决定是否补录、恢复、重建

当前仓库内 skill 目录：

- [project-brain-autostart](/F:/McpProjects/project_brain/docs/chroma/project-brain-autostart/SKILL.md)
- [project-brain-workflow](/F:/McpProjects/project_brain/docs/chroma/project-brain-workflow/SKILL.md)

建议将其安装到 Codex 的 skill 目录后使用。

---

## 6. 如何接入 Claude Code

## 6.1 推荐方式：CLI 添加 stdio MCP Server

Anthropic Claude Code 官方文档支持：

- `claude mcp add --transport stdio <name> -- <command> [args...]`

项目接入示例：

```powershell
claude mcp add --transport stdio `
  --scope project `
  --env PROJECT_BRAIN_STORE_MODE=hybrid-chroma `
  --env PROJECT_BRAIN_CHROMA_URL=http://192.168.6.93:8999 `
  --env PROJECT_BRAIN_CHROMA_NAMESPACE=project-brain `
  --env PROJECT_BRAIN_EMBEDDING_PROVIDER=ollama `
  --env PROJECT_BRAIN_EMBEDDING_URL=http://127.0.0.1:11434 `
  --env PROJECT_BRAIN_EMBEDDING_MODEL=nomic-embed-text `
  project-brain `
  -- java -jar F:\McpProjects\project_brain\target\project-brain-0.1.0.jar
```

注意：

- 所有 `--transport`、`--env`、`--scope` 选项必须放在 server name 之前
- `--` 后面才是实际启动命令

## 6.2 项目级 `.mcp.json`

Claude Code 官方文档支持项目根目录 `.mcp.json`。

当前仓库已经提供了一份可直接使用的 `.mcp.json`：

- [\.mcp.json](/F:/McpProjects/project_brain/.mcp.json)

默认策略是：

- `command = java`
- `args = -jar target/project-brain-0.1.0.jar`
- `PROJECT_BRAIN_STORE_MODE = local-md`

这样做的目的是先保证“任何支持 `.mcp.json` 的客户端都能稳定拉起 server”，即使你还没配置 embedding，也不会因为错误的 `hybrid-chroma` 预设导致误解。

如果你希望项目级 `.mcp.json` 直接启用 `hybrid-chroma`，可以改成：

```json
{
  "mcpServers": {
    "project-brain": {
      "command": "java",
      "args": [
        "-jar",
        "target/project-brain-0.1.0.jar"
      ],
      "env": {
        "PROJECT_BRAIN_STORE_MODE": "hybrid-chroma",
        "PROJECT_BRAIN_CHROMA_URL": "http://192.168.6.93:8999",
        "PROJECT_BRAIN_CHROMA_NAMESPACE": "project-brain",
        "PROJECT_BRAIN_EMBEDDING_PROVIDER": "ollama",
        "PROJECT_BRAIN_EMBEDDING_URL": "http://127.0.0.1:11434",
        "PROJECT_BRAIN_EMBEDDING_MODEL": "nomic-embed-text"
      }
    }
  }
}
```

推荐做法：

1. 先使用仓库自带的 `local-md` 版本确认接入正常
2. 再把 `.mcp.json` 的 `env` 改为 `hybrid-chroma`
3. 最后验证工具返回中的 `store_mode=hybrid-chroma`

## 6.3 Claude Code 的工作流约束

Claude Code 没有和 Codex 完全同构的本地 skill 安装方式时，可以用这三层约束替代：

1. 根目录 `AGENTS.md`
2. 项目级 `.mcp.json`
3. 启动提示里明确要求“项目任务先调用 start_task”

---

## 7. 缺少配置时会发生什么

### 只配 Chroma，不配 embedding

结果：

- 不会启用 `hybrid-chroma`
- 自动退回 `local-md`

### Chroma 可用，但 embedding 探活失败

结果：

- 自动退回 `local-md`

### 都不配置

结果：

- 系统正常可用
- 仅运行在 `local-md`

---

## 8. 建议的最小落地方案

如果你现在要先跑起来，建议按这个顺序：

1. 先构建 jar
2. 先在 Codex 或 Claude Code 里接入 `project-brain`
3. 先只配 `local-md`，确认 `start_task / finish_task` 跑通
4. 再补 Chroma 配置
5. 再补 embedding 配置
6. 验证 `store_mode=hybrid-chroma`
7. 最后再使用 `sync_to_chroma`、`compact_knowledge`

---

## 9. 参考文档

- OpenAI Codex MCP 文档：https://developers.openai.com/codex/mcp
- OpenAI Codex 配置参考：https://developers.openai.com/codex/config-reference
- Claude Code MCP 文档：https://code.claude.com/docs/en/mcp
