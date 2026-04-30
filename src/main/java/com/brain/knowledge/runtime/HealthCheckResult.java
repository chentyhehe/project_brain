package com.brain.knowledge.runtime;

import com.brain.knowledge.store.DegradeReason;

import java.time.Instant;

public record HealthCheckResult(
        boolean healthy,
        DegradeReason degradeReason,
        String message,
        Instant checkedAt) {
    public static HealthCheckResult healthy(String message) {
        return new HealthCheckResult(true, DegradeReason.NONE, message, Instant.now());
    }

    public static HealthCheckResult degraded(DegradeReason reason, String message) {
        return new HealthCheckResult(false, reason, message, Instant.now());
    }
}
