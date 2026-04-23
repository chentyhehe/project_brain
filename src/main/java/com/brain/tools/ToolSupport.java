package com.brain.tools;

import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ToolSupport {
    private ToolSupport() {
    }

    static McpSchema.Tool tool(String name, String title, String description, Map<String, Object> properties, List<String> required) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema("object", properties, required, false, null, null);
        return McpSchema.Tool.builder()
                .name(name)
                .title(title)
                .description(description)
                .inputSchema(schema)
                .build();
    }

    static Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    static Map<String, Object> stringArrayProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "array");
        property.put("description", description);
        property.put("items", Map.of("type", "string"));
        return property;
    }

    static McpSchema.CallToolResult textResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(false)
                .build();
    }

    static McpSchema.CallToolResult errorResult(Exception exception) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("错误：" + exception.getMessage())))
                .isError(true)
                .build();
    }

    static String requiredString(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数：" + name);
        }
        return text.trim();
    }

    static String optionalString(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(name + " 必须是字符串。");
        }
        return text.isBlank() ? null : text.trim();
    }

    static boolean optionalBoolean(Map<String, Object> args, String name, boolean defaultValue) {
        Object value = args == null ? null : args.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(name + " 必须是布尔值。");
        }
        return bool;
    }

    static Path projectPath(Map<String, Object> args) {
        String projectPath = optionalString(args, "project_path");
        return projectPath == null ? Path.of(System.getProperty("user.dir")) : Path.of(projectPath);
    }

    static List<String> stringList(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(name + " 必须是字符串数组。");
        }
        return values.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(item -> item.toString().trim())
                .toList();
    }

    static Map<String, Object> properties(Object... entries) {
        Map<String, Object> props = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            props.put((String) entries[i], entries[i + 1]);
        }
        return props;
    }
}
