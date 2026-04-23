package com.brain.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class BackendScanner {
    private static final Set<String> IGNORED = Set.of("common", "util", "utils", "config", "base");

    public List<ModuleInfo> scanModules(Path projectRoot) throws IOException {
        Path sourceRoot = projectRoot.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        Path moduleRoot = findModuleRoot(sourceRoot);
        try (Stream<Path> stream = Files.list(moduleRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> !IGNORED.contains(path.getFileName().toString().toLowerCase()))
                    .map(path -> new ModuleInfo(path.getFileName().toString(), path))
                    .sorted(Comparator.comparing(ModuleInfo::name))
                    .toList();
        }
    }

    private Path findModuleRoot(Path sourceRoot) throws IOException {
        Path current = sourceRoot;
        while (true) {
            List<Path> childDirs = listChildDirs(current);
            boolean hasJavaFile = hasDirectJavaFile(current);
            if (childDirs.size() != 1 || hasJavaFile) {
                return current;
            }
            current = childDirs.get(0);
        }
    }

    private List<Path> listChildDirs(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList();
        }
    }

    private boolean hasDirectJavaFile(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"));
        }
    }
}
