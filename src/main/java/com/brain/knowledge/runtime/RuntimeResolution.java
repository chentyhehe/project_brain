package com.brain.knowledge.runtime;

import com.brain.knowledge.store.DegradeReason;
import com.brain.knowledge.store.StoreMode;
import com.brain.knowledge.store.StoreStatus;

public record RuntimeResolution(
        StoreMode storeMode,
        StoreStatus storeStatus,
        DegradeReason degradeReason,
        boolean chromaEnabled,
        boolean backupEnabled) {
}
