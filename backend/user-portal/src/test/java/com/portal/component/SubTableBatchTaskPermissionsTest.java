package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchTaskPermissionsTest {
    private static void ordinaryPolicy(Map<String, Object> variables, Map<String, Object> form, Map<String, Object> input) {
        Map<?, ?> forms = (Map<?, ?>) ((Map<?, ?>) variables.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)).get("forms");
        Map<?, ?> control = (Map<?, ?>) ((List<?>) ((Map<?, ?>) forms.get("form")).get("controls")).get(0);
        Map<?, ?> design = (Map<?, ?>) control.get("rowDesign");
        List<?> fields = (List<?>) design.get("fields");
        List<Map<String, Object>> columns = new java.util.ArrayList<>();
        columns.add(Map.of("fieldName", "entry_key", "isPrimaryKey", true));
        for (Object field : fields) columns.add((Map<String, Object>) field);
        // Some tests omit nullable field metadata entirely; they exercise permissions only.
        Map<String, Object> enriched = new HashMap<>(form);
        enriched.put("tableBindings", List.of(Map.of("bindingId", "101", "bindingType", "SUB", "bindingMode", "EDITABLE",
                "tableName", "lines", "primaryKeyFields", List.of("entry_key"), "columns", columns)));
        TaskSubTableSubmission.prepare(input, variables, enriched, "Tester", List.of());
    }
    @Test
    void batchCapabilityElsewhereDoesNotApplyBatchPermissionsToInvestigation() {
        Map<String, Object> baseline = Map.of("dw:lines", List.of(Map.of("entry_key", "A", "amount", 1)));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD,
                Map.of("schemaVersion", 1, "forms", Map.of("assignment", Map.of("controls", List.of()))), "__subTables__", baseline);
        Map<String, Object> form = Map.of("sourceId", "investigation", "tableBindings",
                List.of(Map.of("tableName", "lines", "bindingMode", "READONLY")), "fieldPermissions", Map.of());
        assertDoesNotThrow(() -> SubTableBatchTaskPermissions.validate(variables, form,
                Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("entry_key", "A", "amount", 1,
                        "engine_state", "IN_PROGRESS")))), new ObjectMapper()));
    }

    @Test
    void ordinaryAddAcceptsDialogGeneratedAuditValuesOnBatchEnabledTask() {
        assertAuditSaveAllowed(List.of(), Map.of("entry_key", "B", "amount", 2,
                "created_at", "2026-10-23 10:00:00", "created_by", "Tester",
                "updated_at", "2026-10-23 10:00:00", "updated_by", "Tester"));
    }

    @Test
    void ordinaryEditAfterAssignmentAcceptsRefreshedAuditValues() {
        assertAuditSaveAllowed(List.of(Map.of("entry_key", "A", "amount", 1, "assigned_to", "person",
                        "created_at", "2026-10-22 10:00:00", "created_by", "Creator",
                        "updated_at", "2026-10-22 10:00:00", "updated_by", "Creator")),
                Map.of("entry_key", "A", "amount", 2, "assigned_to", "person",
                        "created_at", "2026-10-22 10:00:00", "created_by", "Creator",
                        "updated_at", "2026-10-23 10:00:00", "updated_by", "Tester"));
    }

    private void assertAuditSaveAllowed(List<Map<String, Object>> originals, Map<String, Object> row) {
        List<Map<String, Object>> fields = new java.util.ArrayList<>(List.of(
                Map.of("fieldName", "amount", "dataType", "INTEGER"),
                Map.of("fieldName", "assigned_to", "dataType", "VARCHAR")));
        Map<String, String> permissions = new HashMap<>();
        for (String name : com.platform.common.audit.SystemAuditFields.ALL) {
            fields.add(Map.of("fieldName", name, "dataType", name.endsWith("_at") ? "TIMESTAMP" : "VARCHAR"));
            permissions.put("101:" + name, "READONLY");
        }
        Map<String, Object> control = Map.of("batchControlId", "grid", "bindingId", "101",
                "assignmentConfig", Map.of("assigneeField", "assigned_to"),
                "capabilities", Map.of("allowBatchAssign", true), "rowDesign", Map.of(
                        "binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("entry_key")), "fields", fields));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                        "forms", Map.of("form", Map.of("controls", List.of(control)))),
                "__subTables__", Map.of("dw:lines", originals));
        Map<String, Object> form = Map.of("sourceId", "form", "fieldPermissions", permissions,
                "tableBindings", List.of(Map.of("tableName", "lines", "bindingMode", "EDITABLE")));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("dw:lines", List.of(row)));
        assertDoesNotThrow(() -> ordinaryPolicy(variables, form, submitted));
        permissions.put("101:amount", "READONLY");
        assertThrows(com.portal.exception.PortalException.class,
                () -> ordinaryPolicy(variables, form, submitted));
    }

    @Test
    void scopeVersionBumpsDoNotTurnUntouchedHistoricValuesIntoBusinessEdits() {
        Map<String, Object> control = Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", Map.of(
                "binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("entry_key")),
                "fields", List.of(Map.of("fieldName", "amount", "dataType", "INTEGER", "nullable", false))));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form", Map.of("controls", List.of(control)))),
                "__subTables__", Map.of("dw:lines", List.of(
                        Map.of("entry_key", "A", "amount", 1, "_wsRowVersion", 1),
                        Map.of("entry_key", "B", "amount", "historic-invalid", "_wsRowVersion", 1))));
        Map<String, Object> form = Map.of("sourceId", "form", "fieldPermissions", Map.of(),
                "tableBindings", List.of(Map.of("tableName", "lines", "bindingMode", "EDITABLE")));
        assertDoesNotThrow(() -> ordinaryPolicy(variables, form,
                Map.of("__subTables__", Map.of("dw:lines", List.of(
                        Map.of("entry_key", "A", "amount", 0, "_wsRowVersion", 2),
                        Map.of("entry_key", "B", "amount", "historic-invalid", "_wsRowVersion", 2))))));
    }

    @Test
    void ordinaryEditsDoNotInheritFrozenBatchTypeRules() {
        Map<String, Object> original = Map.of("entry_key", "A", "amount", 1, "ordinary_note", "old");
        Map<String, Object> control = Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", Map.of(
                "binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("entry_key")),
                "fields", List.of(Map.of("fieldName", "amount", "dataType", "INTEGER", "nullable", false),
                        Map.of("fieldName", "ordinary_note", "dataType", "TEXT"))));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form", Map.of("controls", List.of(control)))),
                "__subTables__", Map.of("dw:lines", List.of(original)));
        Map<String, Object> form = Map.of("sourceId", "form", "fieldPermissions", Map.of(),
                "tableBindings", List.of(Map.of("tableName", "lines", "bindingMode", "EDITABLE")));
        assertDoesNotThrow(() -> ordinaryPolicy(
                variables, form, Map.of("__subTables__", Map.of("dw:lines", List.of(
                        Map.of("entry_key", "A", "amount", "invalid", "ordinary_note", "new"))))));
        assertDoesNotThrow(() -> ordinaryPolicy(variables, form,
                Map.of("__subTables__", Map.of("dw:lines", List.of(
                        Map.of("entry_key", "A", "amount", 0, "ordinary_note", "new"))))));
    }

    @Test
    void legacyInstancesKeepTheirOriginalPermissionAndMergePath() {
        Map<String, Object> form = Map.of("fieldPermissions", Map.of(), "publishedFieldPermissions", Map.of("101:field", "READONLY"));
        assertSame(form, SubTableBatchTaskPermissions.activate(Map.of(), form));
        Map<String, Object> inbound = new HashMap<>(Map.of("__subTables__", "legacy-value"));
        SubTableBatchTaskPermissions.preserveOmittedStores(Map.of(), inbound);
        assertEquals("legacy-value", inbound.get("__subTables__"));
        SubTableBatchTaskPermissions.validate(Map.of(), form, inbound, new ObjectMapper());
    }

    @Test
    void enhancedInstancesRequireActualPublishedPermissionAuthority() {
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of());
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchTaskPermissions.activate(variables,
                Map.of("fieldPermissions", Map.of())));
        Map<String, Object> active = SubTableBatchTaskPermissions.activate(variables,
                Map.of("fieldPermissions", Map.of(), "publishedFieldPermissions", Map.of("101:field", "READONLY")));
        assertEquals(Map.of("101:field", "READONLY"), active.get("fieldPermissions"));
    }

    @Test
    void partialSavesKeepOmittedTablesAndRejectMalformedRows() {
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of(),
                "__subTables__", Map.of("dw:one", List.of(Map.of("key", "one")), "dw:two", List.of(Map.of("key", "two"))));
        Map<String, Object> inbound = new HashMap<>(Map.of("__subTables__", Map.of("dw:one", List.of())));
        SubTableBatchTaskPermissions.preserveOmittedStores(variables, inbound);
        assertEquals(List.of(Map.of("key", "two")), ((Map<?, ?>) inbound.get("__subTables__")).get("dw:two"));
        assertThrows(com.portal.exception.PortalException.class, () -> SubTableBatchTaskPermissions.preserveOmittedStores(
                variables, new HashMap<>(Map.of("__subTables__", Map.of("dw:one", List.of("invalid-row"))))));
    }

    @Test
    void editOnlyCannotBypassCurrentReadonlyPermissionByOmittingBatchIntent() {
        Map<String, Object> control = new HashMap<>(Map.of("batchControlId", "grid", "bindingId", "101", "rowDesign", Map.of(
                "binding", Map.of("tableName", "lines", "primaryKeyFields", List.of("entry_key")),
                "fields", List.of(Map.of("fieldName", "amount")))));
        Map<String, Object> variables = Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form", Map.of("controls", List.of(control)))),
                "__subTables__", Map.of("dw:lines", List.of(Map.of("entry_key", "A", "amount", 1))));
        Map<String, Object> form = Map.of("sourceId", "form", "fieldPermissions", Map.of("101:amount", "READONLY"),
                "tableBindings", List.of(Map.of("tableName", "lines", "bindingMode", "EDITABLE")));
        ordinaryPolicy(variables, form,
                Map.of("__subTables__", variables.get("__subTables__")));
        assertThrows(com.portal.exception.PortalException.class, () -> ordinaryPolicy(variables,
                form, Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("entry_key", "A", "amount", 2))))));
        control.remove("rowDesign");
        assertDoesNotThrow(() -> SubTableBatchTaskPermissions.validate(variables,
                form, Map.of("__subTables__", variables.get("__subTables__")), new ObjectMapper()));
    }
}
