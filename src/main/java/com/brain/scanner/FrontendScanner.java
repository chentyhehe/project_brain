package com.brain.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class FrontendScanner {
    private static final Set<String> IGNORED = Set.of("components", "utils", "hooks", "assets");

    public List<ModuleInfo> scanModules(Path projectRoot) throws IOException {
        Path pages = projectRoot.resolve("src/pages");
        Path views = projectRoot.resolve("src/views");
        Path moduleRoot = Files.isDirectory(pages) ? pages : views;
        if (!Files.isDirectory(moduleRoot)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(moduleRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> !IGNORED.contains(path.getFileName().toString().toLowerCase()))
                    .map(path -> new ModuleInfo(path.getFileName().toString(), path))
                    .sorted(Comparator.comparing(ModuleInfo::name))
                    .toList();
        }
    }
}
