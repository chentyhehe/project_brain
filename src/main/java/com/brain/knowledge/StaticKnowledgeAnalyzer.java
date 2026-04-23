package com.brain.knowledge;

import com.brain.scanner.ModuleInfo;
import com.brain.scanner.ProjectScanResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class StaticKnowledgeAnalyzer {
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "<dependency>\\s*<groupId>(.*?)</groupId>\\s*<artifactId>(.*?)</artifactId>(?:\\s*<version>(.*?)</version>)?",
            Pattern.DOTALL);
    private static final Pattern CLASS_MAPPING_PATTERN = Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");
    private static final Pattern METHOD_MAPPING_PATTERN = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?[\\s\\S]*?(?:public|protected|private)\\s+([\\w<>?,\\s]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("@(?:TableName|Table)\\s*\\((?:name\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);

    public ProjectKnowledge analyze(ProjectScanResult project) throws IOException {
        List<String> techStack = readTechStack(project.projectRoot());
        List<ModuleKnowledge> modules = new ArrayList<>();
        for (ModuleInfo module : project.modules()) {
            modules.add(analyzeModule(project.projectRoot(), module));
        }
        return new ProjectKnowledge(techStack, analyzeGlobalConstraints(project.projectRoot()), analyzeGlobalCodeStyle(project.projectRoot()), modules);
    }

    private List<String> readTechStack(Path root) throws IOException {
        Path pom = root.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return List.of("未发现 pom.xml，无法从 Maven 配置提取技术栈。");
        }
        String content = Files.readString(pom, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        readXmlValue(content, "groupId").ifPresent(value -> lines.add("groupId: " + value));
        readXmlValue(content, "artifactId").ifPresent(value -> lines.add("artifactId: " + value));
        readXmlValue(content, "version").ifPresent(value -> lines.add("version: " + value));
        readXmlValue(content, "maven.compiler.release").ifPresent(value -> lines.add("Java: " + value));
        readXmlValue(content, "maven.compiler.source").ifPresent(value -> lines.add("Java source: " + value));
        Matcher matcher = DEPENDENCY_PATTERN.matcher(content);
        while (matcher.find()) {
            String version = matcher.group(3) == null ? "<!-- TODO: pom.xml 中未直接声明版本，可能由 BOM 或父 POM 管理 -->" : matcher.group(3).trim();
            lines.add("依赖: " + matcher.group(1).trim() + ":" + matcher.group(2).trim() + ":" + version);
        }
        return lines.isEmpty() ? List.of("<!-- TODO: 未能从 pom.xml 提取技术栈信息 -->") : lines;
    }

    private java.util.Optional<String> readXmlValue(String content, String tag) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(tag) + ">([^<]+)</" + Pattern.quote(tag) + ">").matcher(content);
        return matcher.find() ? java.util.Optional.of(matcher.group(1).trim()) : java.util.Optional.empty();
    }

    private List<String> analyzeGlobalConstraints(Path root) throws IOException {
        List<String> constraints = new ArrayList<>();
        List<JavaFile> files = javaFiles(root.resolve("src/main/java"));
        addIfPresent(constraints, files, "统一异常处理", "@ControllerAdvice", "发现 @ControllerAdvice。");
        addIfPresent(constraints, files, "事务管理", "@Transactional", "发现 @Transactional，存在事务边界。");
        addIfPresent(constraints, files, "参数校验", "@Valid", "发现 @Valid，存在参数校验。");
        addIfPresent(constraints, files, "参数校验", "@Validated", "发现 @Validated，存在参数校验。");
        addIfPresent(constraints, files, "统一响应封装", "Result", "发现 Result 命名，可能存在统一响应封装。");
        addIfPresent(constraints, files, "统一响应封装", "Response", "发现 Response 命名，可能存在统一响应封装。");
        return constraints.isEmpty() ? List.of("<!-- TODO: 未从代码中发现可验证的全局约束 -->") : distinct(constraints);
    }

    private List<String> analyzeGlobalCodeStyle(Path root) throws IOException {
        List<String> style = new ArrayList<>();
        List<JavaFile> files = javaFiles(root.resolve("src/main/java"));
        addIfPresent(style, files, "命名", "Controller", "Controller 类使用 *Controller 命名。");
        addIfPresent(style, files, "命名", "Service", "Service 类使用 *Service 命名。");
        addIfPresent(style, files, "命名", "Mapper", "Mapper 类使用 *Mapper 命名。");
        addIfPresent(style, files, "日志", "LoggerFactory", "日志使用 LoggerFactory。");
        addIfPresent(style, files, "日志", "@Slf4j", "日志使用 @Slf4j。");
        return style.isEmpty() ? List.of("<!-- TODO: 未从代码中发现可验证的全局编码规范 -->") : distinct(style);
    }

    private ModuleKnowledge analyzeModule(Path projectRoot, ModuleInfo module) throws IOException {
        List<JavaFile> files = javaFiles(module.path());
        List<Path> controllers = byNameOrAnnotation(files, "Controller", "@RestController", "@Controller");
        List<Path> services = byNameOrAnnotation(files, "Service", "@Service");
        List<Path> mappers = byNameOrAnnotation(files, "Mapper", "@Mapper");
        List<Path> entities = byNameOrAnnotation(files, "Entity", "@Entity", "@TableName");
        return new ModuleKnowledge(
                module.name(),
                module.path(),
                relativize(projectRoot, controllers),
                relativize(projectRoot, services),
                relativize(projectRoot, mappers),
                databaseSchema(projectRoot, files, entities),
                moduleConstraints(files),
                moduleCodeStyle(files),
                apiEndpoints(projectRoot, files),
                responsibilities(controllers),
                businessRules(projectRoot, files),
                stateTransitions(files),
                relatedModules(module.name(), files),
                LocalDate.now() + " 初始化：模块创建"
        );
    }

    private List<ApiEndpoint> apiEndpoints(Path projectRoot, List<JavaFile> files) {
        List<ApiEndpoint> endpoints = new ArrayList<>();
        for (JavaFile file : files) {
            if (!file.content.contains("@RestController") && !file.content.contains("@Controller")) {
                continue;
            }
            String basePath = extractPath(CLASS_MAPPING_PATTERN.matcher(file.content));
            Matcher matcher = METHOD_MAPPING_PATTERN.matcher(file.content);
            while (matcher.find()) {
                String annotation = matcher.group(1);
                String mappingArgs = matcher.group(2) == null ? "" : matcher.group(2);
                String method = httpMethod(annotation, mappingArgs);
                String path = joinPath(basePath, extractPath(mappingArgs));
                endpoints.add(new ApiEndpoint(
                        relativize(projectRoot, file.path),
                        method,
                        path.isBlank() ? "<!-- TODO: 未识别到接口路径 -->" : path,
                        matcher.group(4),
                        normalizeType(matcher.group(3)),
                        normalizeParams(matcher.group(5))
                ));
            }
        }
        return endpoints;
    }

    private String extractPath(Matcher matcher) {
        return matcher.find() ? extractPath(matcher.group(1)) : "";
    }

    private String extractPath(String args) {
        Matcher value = Pattern.compile("(?:value\\s*=\\s*)?\"([^\"]+)\"").matcher(args);
        return value.find() ? value.group(1) : "";
    }

    private String httpMethod(String annotation, String args) {
        return switch (annotation) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "DeleteMapping" -> "DELETE";
            case "PatchMapping" -> "PATCH";
            default -> {
                Matcher matcher = Pattern.compile("RequestMethod\\.(GET|POST|PUT|DELETE|PATCH)").matcher(args);
                yield matcher.find() ? matcher.group(1) : "<!-- TODO: 未识别到 HTTP 方法 -->";
            }
        };
    }

    private String joinPath(String base, String path) {
        if (base.isBlank()) {
            return path;
        }
        if (path.isBlank()) {
            return base;
        }
        return (base + "/" + path).replaceAll("/+", "/");
    }

    private List<String> databaseSchema(Path projectRoot, List<JavaFile> files, List<Path> entityPaths) {
        List<String> schema = new ArrayList<>();
        for (JavaFile file : files) {
            Matcher matcher = TABLE_NAME_PATTERN.matcher(file.content);
            if (matcher.find()) {
                schema.add(relativize(projectRoot, file.path)
                        + " -> 表名: " + matcher.group(1));
            }
        }
        for (Path entity : entityPaths) {
            schema.add(relativize(projectRoot, entity) + " -> <!-- TODO: 请人工确认对应表名 -->");
        }
        return schema.isEmpty() ? List.of("<!-- TODO: 未发现实体类或表名注解 -->") : distinct(schema);
    }

    private List<String> moduleConstraints(List<JavaFile> files) {
        List<String> constraints = new ArrayList<>();
        addIfPresent(constraints, files, "事务", "@Transactional", "发现 @Transactional，相关方法或类存在事务边界。");
        addIfPresent(constraints, files, "校验", "@Valid", "发现 @Valid，存在参数校验。");
        addIfPresent(constraints, files, "校验", "@Validated", "发现 @Validated，存在参数校验。");
        addIfPresent(constraints, files, "异常", "throw new", "发现显式抛出异常，具体业务含义需结合代码确认。");
        return constraints.isEmpty() ? List.of("<!-- TODO: 未发现可验证的模块级硬约束 -->") : distinct(constraints);
    }

    private List<String> moduleCodeStyle(List<JavaFile> files) {
        List<String> style = new ArrayList<>();
        addIfPresent(style, files, "控制器", "@RestController", "Controller 使用 @RestController。");
        addIfPresent(style, files, "服务", "@Service", "Service 使用 @Service。");
        addIfPresent(style, files, "持久层", "@Mapper", "Mapper 使用 @Mapper。");
        addIfPresent(style, files, "依赖注入", "@Autowired", "依赖注入使用 @Autowired。");
        addIfPresent(style, files, "依赖注入", " final ", "存在 final 字段，可能使用构造器注入。");
        return style.isEmpty() ? List.of("<!-- TODO: 未发现可验证的模块代码风格 -->") : distinct(style);
    }

    private List<String> responsibilities(List<Path> controllers) {
        if (controllers.isEmpty()) {
            return List.of("<!-- TODO: 未发现 Controller，无法从入口类推断模块职责 -->");
        }
        return controllers.stream().map(path -> path.getFileName().toString().replace(".java", "") + " 暴露模块入口。").toList();
    }

    private List<String> businessRules(Path projectRoot, List<JavaFile> files) {
        List<String> rules = new ArrayList<>();
        for (JavaFile file : files) {
            if (!file.path.getFileName().toString().contains("Service")) {
                continue;
            }
            String[] lines = file.content.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("if ") || line.startsWith("if(") || line.contains(" throw new ")) {
                    rules.add(relativize(projectRoot, file.path) + ":" + (i + 1) + " `" + line + "`");
                }
            }
        }
        return rules.isEmpty() ? List.of("<!-- TODO: 未从 Service 层发现可提取的条件判断或校验逻辑 -->") : rules;
    }

    private List<String> stateTransitions(List<JavaFile> files) {
        List<String> states = new ArrayList<>();
        for (JavaFile file : files) {
            Matcher matcher = Pattern.compile("\\b(status|state|Status|State)\\b").matcher(file.content);
            if (matcher.find()) {
                states.add(file.path.getFileName() + " 中出现状态字段或状态变量，流转路径需人工确认。");
            }
        }
        return states.isEmpty() ? List.of("<!-- TODO: 未发现状态字段或状态流转代码 -->") : distinct(states);
    }

    private List<String> relatedModules(String currentModule, List<JavaFile> files) {
        Set<String> related = new LinkedHashSet<>();
        for (JavaFile file : files) {
            Matcher matcher = IMPORT_PATTERN.matcher(file.content);
            while (matcher.find()) {
                String imported = matcher.group(1);
                if (imported.contains("." + currentModule + ".")) {
                    continue;
                }
                related.add(imported);
            }
        }
        return related.isEmpty() ? List.of("<!-- TODO: 未从 import 关系发现关联模块 -->") : related.stream().limit(30).toList();
    }

    private List<Path> byNameOrAnnotation(List<JavaFile> files, String namePart, String... annotations) {
        List<Path> matched = new ArrayList<>();
        for (JavaFile file : files) {
            String filename = file.path.getFileName().toString();
            if (filename.contains(namePart)) {
                matched.add(file.path);
                continue;
            }
            for (String annotation : annotations) {
                if (file.content.contains(annotation)) {
                    matched.add(file.path);
                    break;
                }
            }
        }
        return matched.stream().distinct().sorted(Comparator.comparing(Path::toString)).toList();
    }

    private List<JavaFile> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            List<JavaFile> files = new ArrayList<>();
            for (Path path : paths) {
                files.add(new JavaFile(path, Files.readString(path, StandardCharsets.UTF_8)));
            }
            return files;
        }
    }

    private void addIfPresent(List<String> target, List<JavaFile> files, String category, String token, String message) {
        for (JavaFile file : files) {
            if (file.content.contains(token) || file.path.getFileName().toString().contains(token)) {
                target.add(category + "：" + message);
                return;
            }
        }
    }

    private List<String> relativize(Path root, List<Path> paths) {
        return paths.stream().map(path -> relativize(root, path)).toList();
    }

    private String relativize(Path root, Path path) {
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        } catch (IllegalArgumentException exception) {
            return path.toString();
        }
    }

    private String normalizeType(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeParams(String value) {
        if (value == null || value.isBlank()) {
            return "无";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private record JavaFile(Path path, String content) {
    }

    public record ProjectKnowledge(
            List<String> techStack,
            List<String> globalConstraints,
            List<String> globalCodeStyle,
            List<ModuleKnowledge> modules
    ) {
        public ModuleKnowledge module(String name) {
            return modules.stream()
                    .filter(module -> module.name().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    public record ModuleKnowledge(
            String name,
            Path path,
            List<String> controllers,
            List<String> services,
            List<String> mappers,
            List<String> databaseSchema,
            List<String> hardConstraints,
            List<String> codeStyle,
            List<ApiEndpoint> apiEndpoints,
            List<String> responsibilities,
            List<String> businessRules,
            List<String> stateTransitions,
            List<String> relatedModules,
            String changeLog
    ) {
    }

    public record ApiEndpoint(
            String sourceFile,
            String method,
            String path,
            String javaMethod,
            String returnType,
            String parameters
    ) {
    }
}
