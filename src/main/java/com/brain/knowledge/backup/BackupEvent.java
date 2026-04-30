package com.brain.knowledge.backup;

public record BackupEvent(String stage, String summary, String detail, boolean success) {
}
