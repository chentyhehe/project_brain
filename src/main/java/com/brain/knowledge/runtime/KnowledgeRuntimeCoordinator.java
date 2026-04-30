package com.brain.knowledge.runtime;

import java.nio.file.Path;

public interface KnowledgeRuntimeCoordinator {
    RuntimeResolution resolve(Path projectRoot);

    default RuntimeResolution resolveForWrite(Path projectRoot) {
        return resolve(projectRoot);
    }
}
