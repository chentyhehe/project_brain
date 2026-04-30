package com.brain.knowledge;

import com.brain.knowledge.backup.KnowledgeLocalWriter;
import com.brain.scanner.ModuleInfo;
import com.brain.scanner.ProjectScanResult;
import com.brain.scanner.ProjectType;
import com.brain.template.TemplateEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class KnowledgeWriter {
    private final TemplateEngine templates = new TemplateEngine();
    private final StaticKnowledgeAnalyzer analyzer = new StaticKnowledgeAnalyzer();
    private final KnowledgeLocalWriter localWriter = new KnowledgeLocalWriter();

    public List<Path> initialize(ProjectScanResult project) throws IOException {
        return initialize(project, false);
    }

    public List<Path> initialize(ProjectScanResult project, boolean overwrite) throws IOException {
        List<Path> written = new ArrayList<>();
        StaticKnowledgeAnalyzer.ProjectKnowledge analysis = analyzer.analyze(project);
        Path root = project.projectRoot();
        Path knowledgeRoot = root.resolve(".knowledge");
        Files.createDirectories(knowledgeRoot.resolve("global"));
        Files.createDirectories(knowledgeRoot.resolve("modules"));
        Files.createDirectories(knowledgeRoot.resolve("tasks"));

        write(root.resolve("AGENTS.md"), templates.rootAgents(project, analysis), written, overwrite);
        write(knowledgeRoot.resolve("global/AGENTS.md"), templates.globalAgents(project, analysis), written, overwrite);
        write(knowledgeRoot.resolve("tasks/README.md"), templates.tasksReadme(), written, overwrite);

        for (ModuleInfo module : project.modules()) {
            StaticKnowledgeAnalyzer.ModuleKnowledge moduleKnowledge = analysis.module(module.name());
            Path moduleRoot = knowledgeRoot.resolve("modules").resolve(module.name());
            Files.createDirectories(moduleRoot);
            write(moduleRoot.resolve("AGENTS.md"), templates.moduleAgents(module.name(), project.type(), moduleKnowledge), written, overwrite);
            write(moduleRoot.resolve("SPEC.md"), templates.moduleSpec(module.name(), moduleKnowledge), written, overwrite);
            if (project.type() == ProjectType.BACKEND) {
                write(moduleRoot.resolve("api.md"), templates.api(module.name(), moduleKnowledge), written, overwrite);
            } else {
                write(moduleRoot.resolve("DESIGN.md"), templates.design(module.name()), written, overwrite);
            }
        }

        written.addAll(localWriter.snapshotKnowledge(root));
        return written;
    }

    private void write(Path path, String content, List<Path> written, boolean overwrite) throws IOException {
        if (Files.exists(path) && !overwrite) {
            return;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        written.add(path.toAbsolutePath().normalize());
    }
}
