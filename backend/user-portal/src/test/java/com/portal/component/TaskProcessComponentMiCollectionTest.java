package com.portal.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskProcessComponent MI collection row merge")
class TaskProcessComponentMiCollectionTest {

    @Test
    @DisplayName("mergeMiCollectionRowPreferIncoming overwrites assignee from later slice")
    void mergeMiCollectionRowPreferIncoming_overwritesAssignee() {
        Map<String, Object> stale = new LinkedHashMap<>();
        stale.put("id_idw", "Test-000063");
        stale.put("assignee", Map.of("id", "user-dev", "display_name", "Developer Tester"));

        Map<String, Object> fresh = new LinkedHashMap<>();
        fresh.put("id_idw", "Test-000063");
        fresh.put("assignee", Map.of("id", "user-e2e-sunqiang", "display_name", "孙强"));

        Map<String, Object> merged = MiSubTableVariableSupport.mergeMiCollectionRowPreferIncoming(stale, fresh);
        assertEquals("user-e2e-sunqiang", MiSubTableVariableSupport.normalizeMiAssigneeText(merged.get("assignee")));
    }

    @Test
    @DisplayName("normalizeMiAssigneeText extracts user id from snapshot map")
    void normalizeMiAssigneeText_fromMap() {
        Object raw = Map.of("id", "user-e2e-sunqiang", "username", "e2e_sunqiang");
        assertEquals("user-e2e-sunqiang", MiSubTableVariableSupport.normalizeMiAssigneeText(raw));
    }

    @Test
    @DisplayName("parseNumericSubTableSliceKey orders binding 66 after 64")
    void parseNumericSubTableSliceKey_numericOrder() {
        assertTrue(MiSubTableVariableSupport.parseNumericSubTableSliceKey("64")
                < MiSubTableVariableSupport.parseNumericSubTableSliceKey("66"));
    }
}
