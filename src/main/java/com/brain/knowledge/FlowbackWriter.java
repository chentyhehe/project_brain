package com.brain.knowledge;

import com.brain.template.TemplateEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FlowbackWriter {
    private final TemplateEngine templates = new TemplateEngine();

    public List<Path> write(Path projectRoot, String summary, List<String> decisions, String gotchas, List<String> modulesAffected)
            throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path knowledge = root.resolve(".knowledge");
        if (!Files.isDirectory(knowledge)) {
            throw new IllegalStateException("知识库尚未初始化，请先调用 init_project。");
        }

        List<Path> written = new ArrayList<>();
        Path tasksRoot = knowledge.resolve("tasks");
        Files.createDirectories(tasksRoot);
        Path taskFile = uniqueTaskFile(tasksRoot, summary);
        Files.writeString(taskFile, templates.taskRecord(summary, decisions, gotchas, modulesAffected), StandardCharsets.UTF_8);
        written.add(taskFile.toAbsolutePath().normalize());

        if (gotchas != null && !gotchas.isBlank()) {
            for (String module : modulesAffected == null ? List.<String>of() : modulesAffected) {
                if (module == null || module.isBlank()) {
                    continue;
                }
                Path agents = knowledge.resolve("modules").resolve(module.trim()).resolve("AGENTS.md");
                if (Files.isRegularFile(agents)) {
                    String addition = System.lineSeparator()
                            + "### " + LocalDate.now() + " - " + summary.trim()
                            + System.lineSeparator()
                            + gotchas.trim()
                            + System.lineSeparator();
                    Files.writeString(agents, addition, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
                    written.add(agents.toAbsolutePath().normalize());
                }
            }
        }

        return written;
    }

    private Path uniqueTaskFile(Path tasksRoot, String summary) {
        String base = LocalDate.now() + "-" + safeSlug(summary);
        Path candidate = tasksRoot.resolve(base + ".md");
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = tasksRoot.resolve(base + "-" + counter + ".md");
            counter++;
        }
        return candidate;
    }

    private String safeSlug(String summary) {
        String value = summary == null || summary.isBlank() ? "任务" : summary.trim();
        value = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (value.isBlank()) {
            return "任务";
        }
        return value.length() > 60 ? value.substring(0, 60).replaceAll("-+$", "") : value;
    }
}
