package com.brain.knowledge;

import com.brain.scanner.ModuleInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class ContextLoader {
    private static final Map<String, List<String>> MODULE_ALIASES = Map.ofEntries(
            Map.entry("payment", List.of("支付", "付款", "收款")),
            Map.entry("order", List.of("订单", "下单")),
            Map.entry("user", List.of("用户", "会员")),
            Map.entry("auth", List.of("登录", "认证", "鉴权", "权限")),
            Map.entry("account", List.of("账户", "账号")),
            Map.entry("product", List.of("商品", "产品")),
            Map.entry("cart", List.of("购物车")),
            Map.entry("inventory", List.of("库存")),
            Map.entry("notification", List.of("通知", "消息")),
            Map.entry("report", List.of("报表", "报告"))
    );

    public String load(Path projectRoot, String taskDescription) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        ensureInitialized(root);

        List<ModuleInfo> modules = listModules(root);
        List<ModuleInfo> matched = matchModules(modules, taskDescription);

        StringBuilder context = new StringBuilder();
        appendFile(context, root.resolve("AGENTS.md"));
        appendFile(context, root.resolve(".knowledge/global/AGENTS.md"));

        if (matched.isEmpty()) {
            context.append(System.lineSeparator())
                    .append("## 匹配模块").append(System.lineSeparator())
                    .append("未根据任务描述匹配到模块。当前可用模块：")
                    .append(modules.stream().map(ModuleInfo::name).toList())
                    .append(System.lineSeparator());
            return context.toString();
        }

        context.append(System.lineSeparator()).append("## 匹配模块").append(System.lineSeparator());
        for (ModuleInfo module : matched) {
            context.append("- ").append(module.name()).append(System.lineSeparator());
        }

        for (ModuleInfo module : matched) {
            Path moduleRoot = root.resolve(".knowledge/modules").resolve(module.name());
            try (Stream<Path> stream = Files.list(moduleRoot)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                for (Path file : files) {
                    appendFile(context, file);
                }
            }
        }
        return context.toString();
    }

    public List<ModuleInfo> listModules(Path projectRoot) throws IOException {
        Path modulesRoot = projectRoot.toAbsolutePath().normalize().resolve(".knowledge/modules");
        ensureInitialized(projectRoot);
        if (!Files.isDirectory(modulesRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(modulesRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> new ModuleInfo(path.getFileName().toString(), path.toAbsolutePath().normalize()))
                    .sorted(Comparator.comparing(ModuleInfo::name))
                    .toList();
        }
    }

    public List<ModuleInfo> findRelevantModules(Path projectRoot, String taskDescription) throws IOException {
        return matchModules(listModules(projectRoot.toAbsolutePath().normalize()), taskDescription);
    }

    public String readModuleSpec(Path projectRoot, String moduleName) throws IOException {
        Path spec = projectRoot.toAbsolutePath().normalize()
                .resolve(".knowledge/modules")
                .resolve(moduleName)
                .resolve("SPEC.md");
        if (!Files.isRegularFile(spec)) {
            return "未找到 SPEC.md。";
        }
        return Files.readString(spec, StandardCharsets.UTF_8);
    }

    private List<ModuleInfo> matchModules(List<ModuleInfo> modules, String taskDescription) {
        String text = normalize(taskDescription);
        List<ModuleInfo> matched = new ArrayList<>();
        for (ModuleInfo module : modules) {
            String name = normalize(module.name());
            String spaced = normalize(module.name().replace('-', ' ').replace('_', ' '));
            if (text.contains(name) || text.contains(spaced) || aliasesMatch(module.name(), taskDescription)) {
                matched.add(module);
            }
        }
        return matched;
    }

    private boolean aliasesMatch(String moduleName, String taskDescription) {
        String normalizedName = normalize(moduleName);
        List<String> aliases = MODULE_ALIASES.getOrDefault(normalizedName, List.of());
        for (String alias : aliases) {
            if (taskDescription != null && taskDescription.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private void appendFile(StringBuilder context, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        context.append(System.lineSeparator())
                .append("## 文件：").append(file.toAbsolutePath().normalize()).append(System.lineSeparator())
                .append(Files.readString(file, StandardCharsets.UTF_8))
                .append(System.lineSeparator());
    }

    private void ensureInitialized(Path projectRoot) {
        Path knowledge = projectRoot.toAbsolutePath().normalize().resolve(".knowledge");
        if (!Files.isDirectory(knowledge)) {
            throw new IllegalStateException("知识库尚未初始化，请先调用 init_project。");
        }
    }
}
