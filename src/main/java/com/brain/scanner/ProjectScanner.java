package com.brain.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ProjectScanner {
    private static final Pattern FRONTEND_FRAMEWORK =
            Pattern.compile("\"(react|vue|angular|next|nuxt)\"\\s*:", Pattern.CASE_INSENSITIVE);

    private final BackendScanner backendScanner = new BackendScanner();
    private final FrontendScanner frontendScanner = new FrontendScanner();

    public ProjectScanResult scan(Path projectRoot, ProjectType requestedType) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("project_path 必须是已存在的目录：" + root);
        }
        ProjectType type = requestedType == null ? detectType(root) : requestedType;
        List<ModuleInfo> modules = switch (type) {
            case BACKEND -> backendScanner.scanModules(root);
            case FRONTEND -> frontendScanner.scanModules(root);
        };
        return new ProjectScanResult(root, type, root.getFileName().toString(), detectTechStack(root, type), modules);
    }

    private ProjectType detectType(Path root) throws IOException {
        if (Files.exists(root.resolve("pom.xml"))
                || Files.exists(root.resolve("build.gradle"))
                || Files.exists(root.resolve("build.gradle.kts"))
                || Files.exists(root.resolve("pyproject.toml"))
                || containsSourceFile(root, ".py")
                || containsSourceFile(root, ".java")) {
            return ProjectType.BACKEND;
        }

        Path packageJson = root.resolve("package.json");
        if (Files.exists(packageJson)) {
            String content = Files.readString(packageJson);
            return FRONTEND_FRAMEWORK.matcher(content).find() ? ProjectType.FRONTEND : ProjectType.BACKEND;
        }

        throw new IllegalArgumentException("无法自动识别项目类型，请手动指定 type 为 backend 或 frontend。");
    }

    private boolean containsSourceFile(Path root, String suffix) throws IOException {
        try (Stream<Path> stream = Files.walk(root, 4)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.endsWith(suffix));
        }
    }

    private String detectTechStack(Path root, ProjectType type) {
        if (type == ProjectType.BACKEND) {
            if (Files.exists(root.resolve("pom.xml"))) {
                return "Java / Maven";
            }
            if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
                return "Java / Gradle";
            }
            if (Files.exists(root.resolve("pyproject.toml"))) {
                return "Python";
            }
            return "后端项目";
        }
        if (Files.exists(root.resolve("package.json"))) {
            return "前端 / Node.js";
        }
        return "前端项目";
    }
}
