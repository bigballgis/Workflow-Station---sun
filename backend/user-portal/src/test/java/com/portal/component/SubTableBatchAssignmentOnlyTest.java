package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchAssignmentOnlyTest {
    private boolean declareDisplayColumn;
    private final Map<String, Object> original = new LinkedHashMap<>(Map.of("key", "one", "person", "old-person",
            "pool", "", "unit", "", "choice", Map.of("key", "synthetic-choice")));

    @Test
    void roleSwitchValidatesTargetsAndPreservesLookup() {
        var row = roleRow();
        assertDoesNotThrow(() -> validate(row, List.of(roleChange()), true));
        assertEquals(Map.of("key", "synthetic-choice"), row.get("choice"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"readonly", "disabled", "wrong-control", "wrong-key", "partial-key", "duplicate", "nested", "null-removal", "mixed"})
    void declarationCannotWidenTheRowPermissionOrValueContract(String mutation) {
        var row = roleRow();
        var changes = new ArrayList<>(List.of(roleChange()));
        boolean enabled = true;
        switch (mutation) {
            case "readonly" -> { row.put("choice", Map.of("key", "forged")); }
            case "disabled" -> enabled = false;
            case "wrong-control" -> changes.get(0).setBatchControlId("other-grid");
            case "wrong-key" -> changes.get(0).getRowPatches().get(0).setRowKey(Map.of("key", "other"));
            case "partial-key" -> changes.get(0).getRowPatches().get(0).setRowKey(Map.of("extra", "one"));
            case "duplicate" -> changes.add(roleChange());
            case "nested" -> row.put("choice", Map.of("key", "forged"));
            case "null-removal" -> { original.put("extra", null); }
            case "mixed" -> row.put("note", "business edit");
            default -> throw new AssertionError(mutation);
        }
        boolean capability = enabled;
        assertThrows(RuntimeException.class, () -> validate(row, changes, capability));
    }

    @Test
    void unchangedMalformedPersonIsNotEmptyInRoleMode() {
        original.put("person", Map.of("id", "malformed-person"));
        var row = roleRow();
        row.put("person", original.get("person"));
        assertThrows(RuntimeException.class, () -> validate(row, List.of(roleChange()), true));
    }

    @Test
    void noOpAssignmentCannotCarryAnUnknownField() {
        var row = new LinkedHashMap<>(original);
        row.put("_arbitrary", "must-not-write");
        var change = roleChange();
        var intent = new SubTableBatchChange.AssignmentIntent();
        intent.setMode("PERSON"); intent.setAssigneeId("old-person");
        change.getRowPatches().get(0).setAssignment(intent);
        assertThrows(com.portal.exception.PortalException.class, () -> validate(row, List.of(change), true));
    }

    @Test
    void personPickerDisplayMetadataDoesNotBecomeABusinessFieldChange() {
        var row = new LinkedHashMap<>(original);
        row.put("person", "new-person");
        row.put("assignee_display_name", "New person display label");
        var change = roleChange();
        var intent = new SubTableBatchChange.AssignmentIntent();
        intent.setMode("PERSON"); intent.setAssigneeId("new-person");
        change.getRowPatches().get(0).setAssignment(intent);
        assertDoesNotThrow(() -> validate(row, List.of(change), true));
        row.put("_arbitrary", "not display metadata");
        assertThrows(com.portal.exception.PortalException.class, () -> validate(row, List.of(change), true));
        row.remove("_arbitrary");
        declareDisplayColumn = true;
        assertThrows(com.platform.common.exception.BusinessException.class, () -> validate(row, List.of(change), true));
    }

    private Map<String, Object> roleRow() {
        var row = new LinkedHashMap<>(original);
        row.put("person", null); row.put("pool", "synthetic-role"); row.put("unit", "synthetic-unit");
        return row;
    }

    private SubTableBatchChange roleChange() {
        var intent = new SubTableBatchChange.AssignmentIntent();
        intent.setMode("ROLE"); intent.setRoleCode("synthetic-role"); intent.setBusinessUnitCode("synthetic-unit");
        var patch = new SubTableBatchChange.RowPatch();
        patch.setRowId("one"); patch.setRowKey(Map.of("key", "one")); patch.setFields(List.of("pool", "unit", "person")); patch.setAssignment(intent);
        var change = new SubTableBatchChange();
        change.setBatchControlId("grid"); change.setBindingId("101"); change.setOperation("ASSIGN"); change.setRowPatches(List.of(patch));
        return change;
    }

    private void validate(Map<String, Object> row, List<SubTableBatchChange> changes, boolean enabled) {
        var fields = new ArrayList<>(List.of("key", "person", "pool", "unit", "choice", "note").stream()
                .map(name -> Map.<String, Object>of("fieldName", name, "dataType", "VARCHAR", "nullable", true)).toList());
        if (declareDisplayColumn) fields.add(Map.of("fieldName", "assignee_display_name", "dataType", "VARCHAR", "nullable", true));
        var control = Map.of("batchControlId", "grid", "bindingId", "101", "capabilities", Map.of("allowBatchAssign", enabled),
                "originalCrud", Map.of("allowEdit", true), "assignmentConfig", Map.of("assigneeField", "person", "roleField", "pool",
                        "buField", "unit", "allowUser", true, "allowRole", true, "taskDefinitionKey", "Target"),
                "rowDesign", Map.of("binding", Map.of("tableName", "renamed_lines", "primaryKeyFields", List.of("key")), "fields", fields));
        var variables = Map.<String, Object>of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("schemaVersion", 1,
                "forms", Map.of("form", Map.of("controls", List.of(control)))),
                "__subTables__", Map.of("dw:renamed_lines", List.of(original)));
        var form = Map.<String, Object>of("sourceId", "form", "fieldPermissions", Map.of("101:choice", "READONLY"),
                "tableBindings", List.of(Map.of("tableName", "renamed_lines", "bindingMode", "EDITABLE")));
        SubTableBatchTaskPermissions.validate(variables, form,
                Map.of("__subTables__", Map.of("dw:renamed_lines", List.of(row))), new ObjectMapper(), changes);
    }
}
