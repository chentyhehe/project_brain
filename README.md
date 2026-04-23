# project-brain

project-brain is a Java-based MCP Server for project knowledge management. It helps AI assistants read, organize, and maintain project knowledge through local `.knowledge/` files.

## Features

- Provides MCP tools for project initialization, task context loading, module listing, knowledge file reading, and task finish flowback.
- Uses local Markdown files as the project knowledge base.
- Runs over stdio as an MCP server.
- Does not call an LLM by itself; it generates prompts and structured context for the current AI assistant to use.

## Tech Stack

- Java 17
- Maven
- MCP Java SDK

## Project Structure

```text
src/main/java/com/brain/
+-- Main.java                 # Application entry point
+-- knowledge/                # Knowledge loading, analysis, and flowback
+-- scanner/                  # Project type and module scanning
+-- server/                   # MCP server bootstrap and tool registration
+-- template/                 # Markdown template generation
+-- tools/                    # MCP tool implementations
```

Knowledge files are stored under:

```text
.knowledge/
+-- global/
+-- modules/
+-- tasks/
```

## Build

```bash
mvn clean package
```

The shaded executable jar is generated under `target/`.

## Run

```bash
java -jar target/project-brain-0.1.0.jar
```

The server communicates through stdio and is intended to be launched by an MCP-compatible client.

## MCP Tools

- `init_project`: scans the project and returns a Chinese initialization prompt for the current AI assistant.
- `start_task`: loads local knowledge context and returns a task context analysis prompt.
- `finish_task`: writes a basic task record and returns a knowledge flowback prompt.
- `list_modules`: lists initialized knowledge modules.
- `get_file`: reads files under `.knowledge/`.

## Notes

- The MCP server itself is static: it reads and writes local files, scans source structure, and generates prompts.
- LLM reasoning is performed by the AI assistant consuming those prompts, not by this Java process.
- After changing tools or registration code, restart the MCP server so clients can refresh the tool list.
