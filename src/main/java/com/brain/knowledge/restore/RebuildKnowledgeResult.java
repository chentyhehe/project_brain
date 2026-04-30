package com.brain.knowledge.restore;

import com.brain.knowledge.compact.CompactKnowledgeResult;

public record RebuildKnowledgeResult(
        RestoreKnowledgeResult restoreKnowledgeResult,
        RestoreChromaResult restoreChromaResult,
        CompactKnowledgeResult compactKnowledgeResult) {
}
