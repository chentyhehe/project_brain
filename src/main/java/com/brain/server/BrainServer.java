package com.brain.server;

import com.brain.tools.BrainTool;
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
                        Project Brain 鎻愪緵椤圭洰鐭ヨ瘑绠＄悊宸ュ叿銆?                        瀹冧笉浼氳皟鐢?LLM锛屼篃涓嶄細璁块棶澶栭儴 HTTP 鏈嶅姟銆?                        姣忎釜椤圭洰鍏堣皟鐢ㄤ竴娆?init_project锛涘鐞嗛」鐩换鍔″墠璋冪敤 start_task锛?                        瀹屾垚鏈夋剰涔夌殑浠ｇ爜淇敼鎴栨灦鏋勫喅绛栧悗璋冪敤 finish_task銆?                        """);

        for (BrainTool tool : tools) {
            server.toolCall(tool.tool(), (exchange, request) -> tool.call(request.arguments()));
        }

        server.build();
    }
}
