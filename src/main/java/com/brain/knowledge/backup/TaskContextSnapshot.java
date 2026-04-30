package com.brain.knowledge.backup;

public record TaskContextSnapshot(
        String sourceTool,
        String category,
        String summary,
        String content) {
}
