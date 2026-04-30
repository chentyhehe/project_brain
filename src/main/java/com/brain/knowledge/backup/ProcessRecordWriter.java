package com.brain.knowledge.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ProcessRecordWriter {
    private final KnowledgeLocalWriter localWriter = new KnowledgeLocalWriter();

    public void recordPlan(Path projectRoot, String planSummary, List<String> steps, List<String> modulesAffected)
            throws IOException {
        localWriter.appendEvent(projectRoot, new BackupEvent(
                "record_plan",
                planSummary,
                formatModules(modulesAffected) + System.lineSeparator() + formatSteps(steps),
                true));
        localWriter.writeTaskContextSummary(projectRoot, new TaskContextSummary(
                "record_plan",
                planSummary,
                steps,
                modulesAffected == null ? List.of() : modulesAffected));
        localWriter.writeContextSnapshot(projectRoot, new ContextSnapshot(
                "record_plan",
                planSummary,
                formatSteps(steps)));
    }

    public void recordProgress(Path projectRoot, String summary, String details, List<String> modulesAffected)
            throws IOException {
        localWriter.appendEvent(projectRoot, new BackupEvent(
                "record_progress",
                summary,
                formatModules(modulesAffected) + System.lineSeparator() + formatDetails(details),
                true));
    }

    public void recordGotcha(Path projectRoot, String title, String details, List<String> modulesAffected)
            throws IOException {
        localWriter.appendEvent(projectRoot, new BackupEvent(
                "record_gotcha",
                title,
                formatModules(modulesAffected) + System.lineSeparator() + formatDetails(details),
                true));
    }

    private String formatModules(List<String> modulesAffected) {
        if (modulesAffected == null || modulesAffected.isEmpty()) {
            return "modules=[]";
        }
        return "modules=" + modulesAffected;
    }

    private String formatSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            return "steps=[]";
        }
        StringBuilder builder = new StringBuilder("steps:");
        for (String step : steps) {
            builder.append(System.lineSeparator()).append("- ").append(step);
        }
        return builder.toString();
    }

    private String formatDetails(String details) {
        if (details == null || details.isBlank()) {
            return "details=";
        }
        return "details=" + details.trim();
    }
}
