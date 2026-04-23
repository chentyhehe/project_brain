package com.brain.scanner;

public enum ProjectType {
    BACKEND("backend"),
    FRONTEND("frontend");

    private final String value;

    ProjectType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String displayName() {
        return switch (this) {
            case BACKEND -> "后端（backend）";
            case FRONTEND -> "前端（frontend）";
        };
    }

    public static ProjectType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "backend" -> BACKEND;
            case "frontend" -> FRONTEND;
            default -> throw new IllegalArgumentException("type 只能是 backend 或 frontend");
        };
    }
}
