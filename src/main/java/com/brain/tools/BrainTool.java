package com.brain.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

public interface BrainTool {
    McpSchema.Tool tool();

    McpSchema.CallToolResult call(Map<String, Object> arguments);
}
