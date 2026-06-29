package com.admin.component;

import java.util.List;
import java.util.Map;

public interface EmailMonitorSyncComponent {

    /**
     * Replaces all monitor rules for a function unit with the imported set (delete-then-insert).
     */
    void syncMonitorRules(String functionUnitId, List<Map<String, Object>> monitorRules);
}
