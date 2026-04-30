package com.brain.server;

import com.brain.knowledge.backup.BackupEvent;
import com.brain.knowledge.backup.ContextSnapshot;
import com.brain.knowledge.backup.KnowledgeLocalWriter;
import com.brain.knowledge.backup.TaskContextSummary;
import com.brain.knowledge.ingest.SyncToChromaResult;
import com.brain.knowledge.ingest.SyncToChromaService;
import com.brain.knowledge.runtime.DefaultKnowledgeRuntimeCoordinator;
import com.brain.knowledge.runtime.KnowledgeRuntimeCoordinator;
import com.brain.knowledge.runtime.RuntimeResolution;
import com.brain.tools.BrainTool;
import com.brain.tools.ClarifyTaskTool;
import com.brain.tools.CompactKnowledgeTool;
import com.brain.tools.FinishTaskTool;
import com.brain.tools.GetFileTool;
import com.brain.tools.InitProjectTool;
import com.brain.tools.ListModulesTool;
import com.brain.tools.RecordGotchaTool;
import com.brain.tools.RecordPlanTool;
import com.brain.tools.RecordProgressTool;
import com.brain.tools.RebuildKnowledgeTool;
import com.brain.tools.RestoreChromaTool;
import com.brain.tools.RestoreKnowledgeTool;
import com.brain.tools.StartTaskTool;
import com.brain.tools.SyncToChromaTool;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BrainServer {
    private final KnowledgeLocalWriter localWriter = new KnowledgeLocalWriter();
    private final KnowledgeRuntimeCoordinator runtimeCoordinator = new DefaultKnowledgeRuntimeCoordinator();
    private final SyncToChromaService syncService = new SyncToChromaService();
    private final Map<Path, RuntimeResolution> lastResolutions = new HashMap<>();
    private final List<BrainTool> tools = List.of(
            new InitProjectTool(),
            new ClarifyTaskTool(),
            new StartTaskTool(),
            new RecordPlanTool(),
            new RecordProgressTool(),
            new RecordGotchaTool(),
            new SyncToChromaTool(),
            new CompactKnowledgeTool(),
            new RestoreKnowledgeTool(),
            new RestoreChromaTool(),
            new RebuildKnowledgeTool(),
            new FinishTaskTool(),
            new ListModulesTool(),
            new GetFileTool()
    );

    public void start() {
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        SyncSpecification<?> server = McpServer.sync(transportProvider)
                .serverInfo("project-brain", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .instructions("""
                        Project Brain 提供项目知识管理工具。
                        它不会调用大模型，也不会访问外部 HTTP 服务。
                        每个项目通常先调用一次 init_project。
                        需求模糊时可调用 clarify_task 生成任务澄清上下文。
                        过程沉淀可调用 record_plan、record_progress、record_gotcha。
                        网络恢复或首次接入 Chroma 后可调用 sync_to_chroma 执行历史补录。
                        需要控制主知识目录体积时可调用 compact_knowledge 对允许范围内的大文本做安全精简。
                        知识目录损坏或数据库切换时，可调用 restore_knowledge、restore_chroma、rebuild_knowledge 执行恢复与重建。
                        项目任务开始前调用 start_task，任务完成并形成稳定知识后调用 finish_task。
                        每次工具返回会附带 store_mode、store_status、degrade_reason 等运行时存储状态。
                        """);

        runtimeCoordinator.resolve(Path.of(System.getProperty("user.dir")));

        for (BrainTool tool : tools) {
            server.toolCall(tool.tool(), (exchange, request) -> {
                Map<String, Object> arguments = request.arguments();
                Path projectPath = resolveProjectPath(arguments);
                RuntimeResolution resolution = runtimeCoordinator.resolve(projectPath);
                McpSchema.CallToolResult result = attachRuntimeMetadata(tool.call(arguments), resolution);
                mirrorLocalKnowledge(tool.tool().name(), projectPath, arguments, result, resolution);
                triggerSyncOnRecovery(tool.tool().name(), projectPath, resolution);
                return result;
            });
        }

        server.build();
    }

    private McpSchema.CallToolResult attachRuntimeMetadata(
            McpSchema.CallToolResult result,
            RuntimeResolution resolution) {
        List<McpSchema.Content> content = new ArrayList<>();
        if (result.content() != null) {
            content.addAll(result.content());
        }
        content.add(new McpSchema.TextContent(runtimeSummary(resolution)));

        Map<String, Object> meta = new LinkedHashMap<>();
        if (result.meta() != null) {
            meta.putAll(result.meta());
        }
        meta.put("store_mode", resolution.storeMode().configValue());
        meta.put("store_status", resolution.storeStatus().name());
        meta.put("degrade_reason", resolution.degradeReason().name());
        meta.put("chroma_enabled", resolution.chromaEnabled());
        meta.put("backup_enabled", resolution.backupEnabled());

        Object structuredContent = mergeStructuredContent(result.structuredContent(), resolution);
        return McpSchema.CallToolResult.builder()
                .content(content)
                .isError(Boolean.TRUE.equals(result.isError()))
                .meta(meta)
                .structuredContent(structuredContent)
                .build();
    }

    private void mirrorLocalKnowledge(
            String toolName,
            Path projectPath,
            Map<String, Object> arguments,
            McpSchema.CallToolResult result,
            RuntimeResolution resolution) {
        try {
            localWriter.snapshotKnowledge(projectPath);
            localWriter.appendEvent(projectPath, new BackupEvent(
                    toolName,
                    summarizeToolCall(toolName, arguments),
                    "isError=" + result.isError()
                            + ", storeMode=" + resolution.storeMode().configValue()
                            + ", storeStatus=" + resolution.storeStatus().name()
                            + ", degradeReason=" + resolution.degradeReason().name(),
                    !result.isError()));
            writeContextSnapshotIfNeeded(projectPath, toolName, arguments);
        } catch (Exception ignored) {
            // Local backup must not break the main MCP call path.
        }
    }

    private void triggerSyncOnRecovery(String toolName, Path projectPath, RuntimeResolution resolution) {
        RuntimeResolution previous = lastResolutions.put(projectPath, resolution);
        if ("sync_to_chroma".equals(toolName) || previous == null) {
            return;
        }
        if (!"DEGRADED".equals(previous.storeStatus().name()) || !"AVAILABLE".equals(resolution.storeStatus().name())) {
            return;
        }
        try {
            SyncToChromaResult result = syncService.syncProject(projectPath);
            localWriter.appendEvent(projectPath, new BackupEvent(
                    "sync_to_chroma_auto",
                    "runtime recovered",
                    "syncedFiles=" + result.syncedFiles()
                            + ", pendingFiles=" + result.pendingFiles()
                            + ", errors=" + result.errors(),
                    result.errors().isEmpty()));
        } catch (Exception ignored) {
            // Automatic recovery sync should never break the main tool path.
        }
    }

    private void writeContextSnapshotIfNeeded(Path projectPath, String toolName, Map<String, Object> arguments)
            throws java.io.IOException {
        if ("start_task".equals(toolName)) {
            String taskDescription = stringArg(arguments, "task_description", "");
            localWriter.writeTaskContextSummary(projectPath, new TaskContextSummary(
                    "start_task",
                    taskDescription,
                    List.of(taskDescription),
                    List.of("首期仅写结构化摘要，不保存完整模型上下文")));
            localWriter.writeContextSnapshot(projectPath, new ContextSnapshot(
                    "start_task",
                    stringArg(arguments, "task_description", "task"),
                    taskDescription));
            return;
        }
        if ("finish_task".equals(toolName)) {
            localWriter.writeTaskContextSummary(projectPath, new TaskContextSummary(
                    "finish_task",
                    stringArg(arguments, "summary", "finish"),
                    List.of(stringArg(arguments, "summary", "")),
                    List.of(stringArg(arguments, "gotchas", ""))));
            localWriter.writeContextSnapshot(projectPath, new ContextSnapshot(
                    "finish_task",
                    stringArg(arguments, "summary", "finish"),
                    "summary=" + stringArg(arguments, "summary", "")
                            + System.lineSeparator()
                            + "gotchas=" + stringArg(arguments, "gotchas", "")));
        }
    }

    private String summarizeToolCall(String toolName, Map<String, Object> arguments) {
        if ("start_task".equals(toolName)) {
            return stringArg(arguments, "task_description", toolName);
        }
        if ("finish_task".equals(toolName)) {
            return stringArg(arguments, "summary", toolName);
        }
        return toolName;
    }

    private String stringArg(Map<String, Object> arguments, String key, String fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return fallback;
    }

    private String runtimeSummary(RuntimeResolution resolution) {
        return """
                ## store_runtime
                - store_mode: %s
                - store_status: %s
                - degrade_reason: %s
                - chroma_enabled: %s
                - backup_enabled: %s
                """.formatted(
                resolution.storeMode().configValue(),
                resolution.storeStatus().name(),
                resolution.degradeReason().name(),
                resolution.chromaEnabled(),
                resolution.backupEnabled());
    }

    private Object mergeStructuredContent(Object existing, RuntimeResolution resolution) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("store_mode", resolution.storeMode().configValue());
        runtime.put("store_status", resolution.storeStatus().name());
        runtime.put("degrade_reason", resolution.degradeReason().name());
        runtime.put("chroma_enabled", resolution.chromaEnabled());
        runtime.put("backup_enabled", resolution.backupEnabled());
        if (existing instanceof Map<?, ?> existingMap) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                if (entry.getKey() != null) {
                    merged.put(entry.getKey().toString(), entry.getValue());
                }
            }
            merged.putAll(runtime);
            return merged;
        }
        if (existing != null) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("result", existing);
            wrapped.putAll(runtime);
            return wrapped;
        }
        return runtime;
    }

    private Path resolveProjectPath(Map<String, Object> arguments) {
        Object value = arguments == null ? null : arguments.get("project_path");
        if (value instanceof String text && !text.isBlank()) {
            return Path.of(text.trim());
        }
        return Path.of(System.getProperty("user.dir"));
    }
}
