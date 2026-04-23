# project-brain

project-brain 是一个用于项目知识管理的 Java MCP Server。它通过本地 `.knowledge/` Markdown 文件帮助 AI 助手读取、组织和沉淀项目知识。

## 功能特性

- 提供项目初始化、任务上下文加载、模块列表、知识文件读取和任务回流等 MCP 工具。
- 使用本地 Markdown 文件作为项目知识库。
- 通过 stdio 方式作为 MCP Server 运行。
- 服务本身不直接调用大模型；它负责静态扫描、读写本地文件，并生成给当前 AI 助手使用的提示词和上下文。

## 技术栈

- Java 17
- Maven
- MCP Java SDK

## 项目结构

```text
src/main/java/com/brain/
+-- Main.java                 # 应用入口
+-- knowledge/                # 知识加载、分析和回流
+-- scanner/                  # 项目类型和模块扫描
+-- server/                   # MCP Server 启动和工具注册
+-- template/                 # Markdown 模板生成
+-- tools/                    # MCP 工具实现
```

知识库文件位于：

```text
.knowledge/
+-- global/
+-- modules/
+-- tasks/
```

## 构建

```bash
mvn clean package
```

构建完成后，可执行的 shaded jar 会生成在 `target/` 目录下。

## 运行

```bash
java -jar target/project-brain-0.1.0.jar
```

该服务通过 stdio 通信，通常由支持 MCP 的客户端启动。

## MCP 工具

- `init_project`：扫描项目并返回给当前 AI 助手执行的中文初始化提示词。
- `start_task`：加载本地知识上下文，并返回任务上下文关联分析提示词。
- `finish_task`：写入基础任务记录，并返回知识回流沉淀提示词。
- `list_modules`：列出已初始化的知识模块。
- `get_file`：读取 `.knowledge/` 目录下的知识文件。

## 注意事项

- MCP Server 本身是静态的：只负责读取文件、写入文件、扫描源码结构和生成提示词。
- 大模型推理由消费这些提示词的 AI 助手完成，不由 Java 进程直接执行。
- 修改工具或注册逻辑后，需要重启 MCP Server，客户端才能刷新工具列表。