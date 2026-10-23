package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubTableBatchLookupAssignmentTest {
    @ParameterizedTest
    @ValueSource(strings = {"entry_key", "renamed_reference"})
    void assignmentSavesWithoutRevalidatingUntouchedLookupObjects(String pk) throws Exception {
        var h = fixture(pk);
        var before = rows(h.read());
        save(h, payload(h), List.of(assignment(pk)));
        var after = rows(h.read());
        assertEquals("synthetic-target", after.get(0).get("caption"));
        assertEquals(before.get(1), after.get(1));
        for (int i = 0; i < 5; i++) assertEquals(before.get(0).get("lookup_" + i), after.get(0).get("lookup_" + i));
        assertEquals(1, h.writes.get());
    }

    @Test
    void completeValidatesTheSameLookupAssignmentPayload() throws Exception {
        var h = fixture("entry_key");
        var before = h.read();
        assertDoesNotThrow(() -> h.component.validateSubTableBatchPayload("review", "synthetic-process",
                List.of(assignment(h.pk)), scopes(h.pk), payload(h)));
        assertEquals(before, h.read());
    }

    @Test
    void ordinaryAssignmentDoesNotRequireABatchDeclaration() throws Exception {
        var h = fixture("entry_key");
        // Ordinary writes resolve their binding from the pinned form, independently of batch overlays.
        var publishedDesign = org.mockito.Mockito.mock(SubTableWriteDesign.class);
        org.mockito.Mockito.when(publishedDesign.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(SubTableBatchScopeDesign.overlay(Map.of(), h.read(), List.of(assignment(h.pk))));
        ReflectionTestUtils.setField(h.component, "subTableWriteIsolation", new SubTableWriteIsolation(
                new MiSubTaskSubTableRowMerger(new org.springframework.jdbc.core.JdbcTemplate()),
                new SubTableBindingScopeGuard(new org.springframework.jdbc.core.JdbcTemplate()), publishedDesign));
        var before = h.read();
        save(h, payload(h), null);
        var after = rows(h.read());
        assertEquals("synthetic-target", after.get(0).get("caption"));
        assertEquals(rows(before).get(1), after.get(1));
        for (int i = 0; i < 5; i++) assertEquals(rows(before).get(0).get("lookup_" + i), after.get(0).get("lookup_" + i));
        assertEquals(1, h.writes.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nested", "unknown", "removed", "other-row", "mismatch", "oversized", "invalid-type", "version"})
    void assignmentCannotGrantAnExemptionToOtherMutations(String mutation) throws Exception {
        var h = fixture("entry_key");
        var before = h.read();
        var submitted = payload(h);
        var row = rows(submitted).get(0);
        switch (mutation) {
            case "nested" -> row.put("lookup_0", Map.of("row_id", "changed", "label", "changed"));
            case "unknown" -> row.put("_arbitrary", "must-not-write");
            case "removed" -> row.remove("lookup_0");
            case "other-row" -> rows(submitted).get(1).put("caption", "undeclared-change");
            case "mismatch" -> row.put("caption", "another-target");
            case "oversized" -> row.put("caption", "x".repeat(65));
            case "invalid-type" -> row.put("caption", Map.of("id", "synthetic-target"));
            case "version" -> row.put("_wsRowVersion", 0);
            default -> throw new AssertionError(mutation);
        }
        assertThrows(RuntimeException.class, () -> save(h, submitted, List.of(assignment(h.pk))));
        assertEquals(before, h.read());
        assertEquals(0, h.writes.get());
    }

    @Test
    void startedTargetStillRejectsPureAssignment() throws Exception {
        var h = fixture("entry_key");
        h.enableAssignmentContract(true);
        var before = h.read();
        var failure = assertThrows(com.portal.exception.PortalException.class,
                () -> save(h, payload(h), List.of(assignment(h.pk))));
        assertTrue(failure.getMessage().contains("already started"));
        assertEquals(before, h.read());
    }

    @SuppressWarnings("unchecked")
    private static BatchTaskFormTransactionHarness fixture(String pk) throws Exception {
        var h = new BatchTaskFormTransactionHarness(pk, false);
        h.enableAssignmentContract(false);
        var vars = h.read();
        var snapshot = (Map<?, ?>) vars.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        var form = (Map<?, ?>) ((Map<?, ?>) snapshot.get("forms")).get("form-one");
        var control = (Map<?, ?>) ((List<?>) form.get("controls")).get(0);
        var design = (Map<String, Object>) control.get("rowDesign");
        var fields = new ArrayList<>((List<Map<String, Object>>) design.get("fields"));
        fields.stream().filter(field -> "caption".equals(field.get("fieldName"))).findFirst().orElseThrow().put("length", 64);
        for (int i = 0; i < 5; i++) {
            String name = "lookup_" + i;
            fields.add(Map.of("fieldName", name, "dataType", "VARCHAR", "nullable", false));
            for (var row : rows(vars)) row.put(name, Map.of("row_id", "synthetic-choice", "label", "Synthetic choice"));
        }
        design.put("fields", fields);
        h.persistence.update("UPDATE batch_tx_instance SET variables=? WHERE id=?", h.mapper.writeValueAsString(vars), "synthetic-process");
        var loader = (TaskFormDefinitionLoader) ReflectionTestUtils.getField(h.component, "formDefinitionLoader");
        var published = new java.util.HashMap<>(loader.fetchTaskFormByStageId("review", "synthetic-process", null));
        var permissions = new java.util.HashMap<String,Object>(Map.of("101:caption", "EDITABLE"));
        for (int i = 0; i < 5; i++) permissions.put("101:lookup_" + i, "READONLY");
        published.put("publishedFieldPermissions", permissions);
        published.put("fieldPermissions", permissions);
        org.mockito.Mockito.when(loader.fetchTaskFormByStageId(org.mockito.ArgumentMatchers.eq("review"),
                org.mockito.ArgumentMatchers.eq("synthetic-process"), org.mockito.ArgumentMatchers.any())).thenReturn(published);
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(h.persistence.getDataSource());
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(100) PRIMARY KEY, status VARCHAR(20), deleted BOOLEAN)");
        jdbc.update("INSERT INTO sys_users VALUES(?, ?, ?)", "synthetic-target", "ACTIVE", false);
        ReflectionTestUtils.setField(h.component, "jdbcTemplate", jdbc);
        return h;
    }

    private static Map<String, Object> payload(BatchTaskFormTransactionHarness h) throws Exception {
        var vars = h.read();
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("caseKey", vars.get("caseKey"));
        payload.put("__subTables__", vars.get("__subTables__"));
        rows(payload).get(0).put("caption", "synthetic-target");
        return payload;
    }

    private static SubTableBatchChange assignment(String pk) {
        var intent = new SubTableBatchChange.AssignmentIntent();
        intent.setMode("PERSON"); intent.setAssigneeId("synthetic-target");
        var patch = new SubTableBatchChange.RowPatch();
        patch.setRowId("one"); patch.setRowKey(Map.of(pk, "one")); patch.setFields(List.of("caption")); patch.setAssignment(intent);
        var change = new SubTableBatchChange();
        change.setBindingId("101"); change.setBatchControlId("grid"); change.setOperation("ASSIGN"); change.setRowPatches(List.of(patch));
        return change;
    }

    private static List<SubTableBindingScope> scopes(String pk) {
        return List.of(new SubTableBindingScope("101", "dw:lines", List.of(Map.of(pk, "one")), false));
    }

    private static void save(BatchTaskFormTransactionHarness h, Map<String, Object> payload, List<SubTableBatchChange> changes) {
        h.component.submitTaskForm("synthetic-task", "synthetic-user", payload, null, List.of(), scopes(h.pk), changes);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> vars) {
        return (List<Map<String, Object>>) ((Map<?, ?>) vars.get("__subTables__")).get("dw:lines");
    }
}
