package com.portal.component;

import com.portal.exception.PortalException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** These are the same server-owned workspace keys stripped from client input at Process Start. */
final class SubTableBatchWorkspaceContext {
    private SubTableBatchWorkspaceContext() { }
    static void validate(Map<String, Object> baseline, Map<String, Object> incoming) {
        if (baseline == null || !baseline.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)) return;
        for (String key : List.of("activeBusinessUnitId", "activeRoleId")) {
            if (incoming.containsKey(key) && (!baseline.containsKey(key) || !Objects.equals(baseline.get(key), incoming.get(key)))) {
                throw new PortalException("403", "Instance workspace context cannot be changed by form data");
            }
        }
    }
}
