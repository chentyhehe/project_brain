package com.brain.knowledge.backup;

import java.util.List;

public record TaskContextSummary(
        String sourceTool,
        String summary,
        List<String> keyPoints,
        List<String> constraints) {
}
