package com.brain.scanner;

import java.nio.file.Path;
import java.util.List;

public record ProjectScanResult(
        Path projectRoot,
        ProjectType type,
        String projectName,
        String techStack,
        List<ModuleInfo> modules
) {
}
