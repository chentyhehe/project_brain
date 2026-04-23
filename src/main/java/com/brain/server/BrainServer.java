package com.brain.server;

import com.brain.tools.BrainTool;
import com.brain.tools.ClarifyTaskTool;
import com.brain.tools.FinishTaskTool;
import com.brain.tools.GetFileTool;
import com.brain.tools.InitProjectTool;
import com.brain.tools.ListModulesTool;
import com.brain.tools.StartTaskTool;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

public final class BrainServer {
    private final List<BrainTool> tools = List.of(
            new InitProjectTool(),
            new ClarifyTaskTool(),
            new StartTaskTool(),
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
                        项目任务开始前调用 start_task，任务完成并形成稳定知识后调用 finish_task。
                        """);

        for (BrainTool tool : tools) {
            server.toolCall(tool.tool(), (exchange, request) -> tool.call(request.arguments()));
        }

        server.build();
    }
}
