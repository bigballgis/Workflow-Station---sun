package com.portal.component;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchAssignmentDifferencesTest {
    private static final Map<String, Object> CONTROL = Map.of("batchControlId", "grid", "bindingId", "19",
            "capabilities", Map.of("allowBatchAssign", true),
            "rowDesign", Map.of("binding", Map.of("tableName", "RenamedLines", "primaryKeyFields", List.of("businessKey", "revisionKey"))),
            "assignmentConfig", Map.of("assigneeField", "handlerCode", "roleField", "poolCode", "buField", "unitCode"));
    private static final Map<String, Object> CONTROLS = Map.of("grid", CONTROL);
    private static final Map<String, Object> ROW = Map.of("businessKey", "A", "revisionKey", 2,
            "handlerCode", "person-original", "description", "keep", "assignee", "ordinary-business-value");
    private static final Map<String, Object> BASELINE = payload(List.of(ROW));

    @Test
    void followsFrozenCompositeIdentityAndMappingRatherThanNamesOrRowOrder() {
        Map<String, Object> second = new HashMap<>(ROW); second.put("businessKey", "B");
        Map<String, Object> changed = new HashMap<>(ROW); changed.put("handlerCode", "person-new");
        assertEquals(List.of(new SubTableBatchAssignmentDifferences.Control("grid", "19")),
                SubTableBatchAssignmentDifferences.changedControls(payload(List.of(ROW, second)), CONTROLS, payload(List.of(second, changed))));
        changed = new HashMap<>(ROW); changed.put("assignee", "different-ordinary-business-value");
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of(changed))));
    }

    @Test
    void omittedStoreAndOrdinaryEditsDoNotTriggerStageQueries() {
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, Map.of()));
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, Map.of("__subTables__", Map.of())));
        Map<String, Object> changed = new HashMap<>(ROW); changed.put("description", "edited");
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of(changed))));
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of())));
    }

    @Test
    void detectsRoleBuAndClearedTargetsButNotEquivalentEmptyInactiveFields() {
        for (String field : List.of("poolCode", "unitCode", "handlerCode")) {
            Map<String, Object> changed = new HashMap<>(ROW);
            changed.put(field, field.equals("handlerCode") ? null : "new-target");
            assertEquals(1, SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of(changed))).size());
        }
        Map<String, Object> unchanged = new HashMap<>(ROW); unchanged.put("poolCode", ""); unchanged.put("unitCode", null);
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of(unchanged))));
    }

    @Test
    void newRowsWithTargetsNeedStageChecksButBlankNewDraftsDoNot() {
        Map<String, Object> empty = Map.of("businessKey", "B", "revisionKey", 2);
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of(ROW, empty))));
        Map<String, Object> assigned = new HashMap<>(empty); assigned.put("poolCode", "pool-new");
        assertEquals(1, SubTableBatchAssignmentDifferences.changedControls(BASELINE, CONTROLS, payload(List.of(ROW, assigned))).size());
    }

    @Test
    void malformedOrDuplicateCanonicalIdentityCannotHideAnAssignmentChange() {
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchAssignmentDifferences.changedControls(
                BASELINE, CONTROLS, payload(List.of(ROW, ROW))));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchAssignmentDifferences.changedControls(
                BASELINE, CONTROLS, payload(List.of(Map.of("handlerCode", "new-target")))));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchAssignmentDifferences.changedControls(
                BASELINE, CONTROLS, Map.of("__subTables__", Map.of("dw:renamedlines", "bad-rows"))));
    }

    @Test
    void controlsWithoutFrozenAssignCapabilityDoNotAcquireNewStagePolicy() {
        Map<String, Object> disabled = new HashMap<>(CONTROL);
        disabled.put("capabilities", Map.of("allowBatchAssign", false));
        disabled.remove("rowDesign"); disabled.remove("assignmentConfig");
        Map<String, Object> changed = new HashMap<>(ROW); changed.put("handlerCode", "new-target");
        assertEquals(List.of(), SubTableBatchAssignmentDifferences.changedControls(BASELINE, Map.of("grid", disabled), payload(List.of(changed))));
    }

    private static Map<String, Object> payload(List<Map<String, Object>> rows) {
        return Map.of("__subTables__", Map.of("dw:renamedlines", rows));
    }
}
