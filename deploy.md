# Project Brain MCP Server 部署与使用说明

Project Brain 是一个基于 Java 17 的 stdio MCP Server。它不调用任何 LLM SDK，也不发送 HTTP 请求，只通过 MCP 协议为 Codex、Claude Code 等 AI 工具提供本地项目知识管理能力。

## 环境要求

- Java 17 或更高版本
- Maven 3.8 或更高版本

检查命令：

```powershell
java -version
mvn -version
```

## 构建

在本项目根目录执行：

```powershell
mvn clean package
```

构建完成后，可运行 jar 位于：

```text
target/project-brain-0.1.0.jar
```

## 手动启动

```powershell
java -jar target/project-brain-0.1.0.jar
```

这是 stdio MCP Server，直接运行时通常会看起来“卡住”，这是正常现象。实际使用时应由 MCP 客户端启动并通过标准输入输出通信。

## 作为 MCP Server 接入 Claude Code

在 Claude Code 的 MCP 配置中加入：

```json
{
  "mcpServers": {
    "project-brain": {
      "command": "java",
      "args": [
        "-jar",
        "F:/McpProjects/project_brain/target/project-brain-0.1.0.jar"
      ]
    }
  }
}
```

保存配置后重启 Claude Code。连接成功后，应能看到以下工具：

- `init_project`
- `init_project_llm`
- `start_task`
- `finish_task`
- `list_modules`
- `get_file`

如果你的 jar 路径不同，请把示例中的 `F:/McpProjects/project_brain/target/project-brain-0.1.0.jar` 替换成实际路径。Windows 路径建议使用 `/`，例如 `F:/xxx/yyy.jar`。

## 作为 MCP Server 接入 Codex

Codex 的 MCP 配置通常写在 Codex 配置文件中。加入一个 stdio server 配置：

```toml
[mcp_servers.project-brain]
command = "java"
args = [
  "-jar",
  "F:/McpProjects/project_brain/target/project-brain-0.1.0.jar"
]
```

重启 Codex 后，Project Brain 会作为 MCP Server 被加载。之后 Codex 可以在项目任务中调用：

- `init_project` 初始化项目知识库
- `init_project_llm` 生成交给当前 AI 助手执行的初始化 prompt
- `start_task` 加载任务相关上下文
- `finish_task` 回流任务知识
- `list_modules` 查看已识别模块
- `get_file` 精确读取 `.knowledge/` 文件

如果你的 Codex 环境使用 JSON 格式 MCP 配置，也可以使用与 Claude Code 相同的结构：

```json
{
  "mcpServers": {
    "project-brain": {
      "command": "java",
      "args": [
        "-jar",
        "F:/McpProjects/project_brain/target/project-brain-0.1.0.jar"
      ]
    }
  }
}
```

## 推荐使用流程

第一次接入某个项目时，先初始化知识库：

```json
{
  "project_path": "F:/path/to/your/project"
}
```

如果项目已经初始化过，但希望用新版静态分析结果重新生成知识文件，可以显式覆盖：

```json
{
  "project_path": "F:/path/to/your/project",
  "overwrite": true
}
```

如果希望获得更接近“直接给大模型投喂初始化提示词”的效果，可以调用 `init_project_llm`：

```json
{
  "project_path": "F:/path/to/your/project",
  "overwrite": true
}
```

该工具会扫描项目基础信息，并返回一份完整中文 prompt。Project Brain MCP Server 本身不会调用大模型；请让当前 Codex、Claude Code 或其他 AI 助手继续执行该 prompt，完成深度归纳和文件写入。

初始化后，目标项目根目录会生成 `AGENTS.md`。该文件会明确告诉 Codex、Claude Code 等 AI 助手：除 `init_project` 通常由用户手动调用外，项目相关任务应在开始时调用 `start_task`，必要时调用 `list_modules` / `get_file`，任务完成且产生可复用知识时调用 `finish_task`。

注意：MCP Server 本身不能主动调用工具；“自动判断何时调用”是由接入它的 AI 助手根据 `AGENTS.md` 工作协议完成的。

如果无法自动识别项目类型，可以手动指定：

```json
{
  "project_path": "F:/path/to/your/project",
  "type": "backend"
}
```

开始处理项目任务前，加载上下文：

```json
{
  "project_path": "F:/path/to/your/project",
  "task_description": "修复支付超时问题"
}
```

任务完成后，回流知识：

```json
{
  "project_path": "F:/path/to/your/project",
  "summary": "修复支付超时问题",
  "decisions": ["将支付超时判断集中到 payment 模块，避免调用方重复处理"],
  "gotchas": "支付网关返回 pending 时不能立即判定失败，需要等待回调或轮询结果。",
  "modules_affected": ["payment"]
}
```

查看当前项目模块：

```json
{
  "project_path": "F:/path/to/your/project"
}
```

读取指定知识文件：

```json
{
  "project_path": "F:/path/to/your/project",
  "file_path": ".knowledge/modules/payment/AGENTS.md"
}
```

## 工具说明

### init_project

初始化项目知识库，在目标项目下生成：

```text
AGENTS.md
.knowledge/
  global/AGENTS.md
  modules/<module_name>/
  tasks/README.md
```

已存在的知识文件不会被覆盖。

### init_project_llm

生成一份面向大模型的中文初始化 prompt，并把 MCP 静态扫描得到的项目路径、项目类型、技术栈、模块列表、全局约束线索补充进去。

该 prompt 会根据项目类型调整扫描重点：

- Java 后端：Controller、Service、Mapper/Repository、Entity、Config、Exception。
- Python 项目：FastAPI/Flask/Django 的 router、view、service、model、schema、settings。
- 前端项目：pages/views/routes、components、stores、hooks/composables、api client、状态管理和交互状态。

该工具不直接调用任何 LLM，也不发送 HTTP 请求。它适合在需要更强归纳能力时使用：由 MCP Server 负责准备 prompt，由当前 AI 客户端负责继续阅读代码、归纳和写文件。

### start_task

根据任务描述匹配相关模块，读取：

- 根目录 `AGENTS.md`
- `.knowledge/global/AGENTS.md`
- 命中模块下的所有 `.md` 文件

返回拼装好的上下文字符串，供 AI 工具继续执行任务。

### finish_task

任务完成后写入知识回流：

- 在 `.knowledge/tasks/` 下生成任务记录
- 将 `gotchas` 追加到受影响模块的 `AGENTS.md`

### list_modules

列出 `.knowledge/modules/` 下所有模块名称和路径。

### get_file

读取 `.knowledge/` 下指定文件内容。该工具只允许读取 `.knowledge/` 内文件，防止越权读取项目其他文件。

## 注意事项

- Project Brain 不会主动扫描并修改业务代码。
- Project Brain 不会调用 LLM。
- Project Brain 不会发送 HTTP 请求。
- `init_project` 默认只创建缺失文件，不覆盖已有知识。
- 如果 MCP 客户端没有展示工具，先确认 jar 路径、Java 命令和 MCP 配置格式是否正确。
