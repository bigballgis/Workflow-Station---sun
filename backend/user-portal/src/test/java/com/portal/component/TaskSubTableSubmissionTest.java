package com.portal.component;

import com.portal.exception.PortalException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskSubTableSubmissionTest {
    private static final String STORE = "dw:entries";
    private static Map<String, Object> form(String pk, boolean editable) {
        List<Map<String, Object>> fields = new ArrayList<>(List.of(
                Map.of("fieldName", pk, "isPrimaryKey", true),
                Map.of("fieldName", "amount", "dataType", "INTEGER"),
                Map.of("fieldName", "selection", "dataType", "VARCHAR"),
                Map.of("fieldName", "computed_total", "isComputed", true)));
        for (String name : com.platform.common.audit.SystemAuditFields.ALL) fields.add(Map.of("fieldName", name));
        return Map.of("fieldPermissions", Map.of(), "runtimeFieldsByTable", Map.of(STORE, List.of("progress_code", "stage_label")),
                "configJson", Map.of("subForms", Map.of("17", Map.of("rule", List.of(Map.of("field", "selection", "type", "lookup"))))),
                "tableBindings", List.of(Map.of("bindingId", "17", "tableName", "entries", "bindingType", "SUB",
                        "bindingMode", editable ? "EDITABLE" : "READONLY", "primaryKeyFields", List.of(pk), "columns", fields)));
    }
    private static Map<String, Object> payload(List<Map<String, Object>> rows) { return Map.of("__subTables__", Map.of(STORE, rows)); }
    private static List<?> rows(Map<String, Object> result) { return (List<?>) ((Map<?, ?>) result.get("__subTables__")).get(STORE); }

    @ParameterizedTest @ValueSource(strings = {"entry_key", "renamed_business_key"})
    void readonlyRuntimeEchoAndThinSliceKeepTheBusinessBaseline(String pk) {
        var old = List.of(Map.<String,Object>of(pk, "A", "amount", 1), Map.<String,Object>of(pk, "B", "amount", 2));
        var input = payload(List.of(Map.of(pk, "B", "amount", 2, "progress_code", "IN_PROGRESS", "stage_label", "Investigation")));
        var baseline = new HashMap<>(payload(old));
        for (boolean batch : List.of(false, true)) {
            if (batch) baseline.put(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, Map.of("unrelated", true));
            assertEquals(old, rows(TaskSubTableSubmission.prepare(input, baseline, form(pk, false), "Tester", List.of())));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"entry_key", "renamed_business_key"})
    void actualReadonlyWritesAndDeletesStillFailWithoutBatch(String pk) {
        var baseline = payload(List.of(Map.of(pk, "A", "amount", 1)));
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(
                payload(List.of(Map.of(pk, "A", "amount", 9))), baseline, form(pk, false), "Tester", List.of()));
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(
                payload(List.of()), baseline, form(pk, false), "Tester", List.of(STORE)));
    }

    @Test void serverOwnsAuditComputedAndRuntimeValues() {
        var old = Map.<String,Object>of("entry_key", "A", "amount", 1, "created_at", "old-time", "created_by", "Creator",
                "computed_total", 8);
        var input = payload(List.of(Map.of("entry_key", "A", "amount", 2, "created_by", "forged", "updated_by", "forged",
                "progress_code", "COMPLETED", "computed_total", 999)));
        Map<?, ?> saved = (Map<?, ?>) rows(TaskSubTableSubmission.prepare(input, payload(List.of(old)), form("entry_key", true), "Tester", List.of())).get(0);
        assertEquals("Creator", saved.get("created_by"));
        assertEquals("Tester", saved.get("updated_by"));
        assertEquals("old-time", saved.get("created_at"));
        assertEquals(8, saved.get("computed_total"));
        assertFalse(saved.containsKey("progress_code"));
    }

    @Test void lookupChangesUseTheirControlContractWhileScalarFieldsKeepTypeChecks() {
        var baseline = payload(List.of(Map.of("entry_key", "A", "amount", 1)));
        var input = payload(List.of(Map.of("entry_key", "A", "amount", 2, "selection", Map.of("business_code", "X", "label", "Chosen"))));
        assertDoesNotThrow(() -> TaskSubTableSubmission.prepare(input, baseline, form("entry_key", true), "Tester", List.of()));
        assertThrows(com.platform.common.exception.BusinessException.class, () -> TaskSubTableSubmission.prepare(
                payload(List.of(Map.of("entry_key", "A", "amount", "invalid"))), baseline, form("entry_key", true), "Tester", List.of()));
    }

    @Test void missingDuplicateIdentityAndUnknownWritesFail() {
        var baseline = payload(List.of(Map.of("entry_key", "A", "amount", 1)));
        for (var records : List.of(List.<Map<String,Object>>of(Map.of("amount", 2)),
                List.<Map<String,Object>>of(Map.of("entry_key", "A"), Map.of("entry_key", "A")),
                List.<Map<String,Object>>of(Map.of("entry_key", "A", "unknown_business_value", 4)))) {
            assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(payload(records), baseline, form("entry_key", true), "Tester", List.of()));
        }
    }

    @Test void aWritableBindingDoesNotGrantWritePermissionToAnotherSliceOfTheSameTable() {
        var definition = new HashMap<>(form("entry_key", true));
        Map<String,Object> writable = (Map<String,Object>) ((List<?>) definition.get("tableBindings")).get(0);
        var readonly = new HashMap<>(writable);
        readonly.put("bindingId", "18"); readonly.put("bindingMode", "READONLY");
        definition.put("tableBindings", List.of(writable, readonly));
        var baseline = payload(List.of(Map.of("entry_key", "A", "amount", 1)));
        var input = payload(List.of(Map.of("entry_key", "A", "amount", 2)));
        var scope = new com.portal.dto.SubTableBindingScope("18", STORE, List.of(Map.of("entry_key", "A")), false);
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of(), List.of(scope)));
        scope.setBindingId("17");
        assertDoesNotThrow(() -> TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of(), List.of(scope)));
    }

    @Test void managedFieldNamesComeFromTheBpmnSubprocessConfiguration() {
        String xml = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="urn:config"><process id="p">
              <subProcess id="mi"><extensionElements><c:properties>
                <c:property name="miTaskStatusField" value="renamed_progress"/>
                <c:property name="miTaskCurrentNodeField" value="renamed_stage"/>
              </c:properties></extensionElements><multiInstanceLoopCharacteristics/>
              <userTask id="task"><extensionElements><c:properties><c:property name="subTableName" value="Entries"/></c:properties></extensionElements></userTask>
              </subProcess></process></definitions>
            """;
        assertEquals(Map.of(STORE, List.of("renamed_progress", "renamed_stage")), BpmnMiXmlSupport.runtimeFieldsByTable(xml));
    }

    @Test void overlappingWritableClaimsAreLegalAndUnchangedRowsNeedNoClaim() {
        var definition = new HashMap<>(form("entry_key", true));
        Map<String,Object> first = (Map<String,Object>) ((List<?>) definition.get("tableBindings")).get(0);
        var second = new HashMap<>(first); second.put("bindingId", "18");
        definition.put("tableBindings", List.of(first, second));
        var baseline = payload(List.of(Map.of("entry_key", "A", "amount", 1), Map.of("entry_key", "B", "amount", 3)));
        var input = payload(List.of(Map.of("entry_key", "A", "amount", 2), Map.of("entry_key", "B", "amount", 3)));
        var scopes = List.of(new com.portal.dto.SubTableBindingScope("17", STORE, List.of(Map.of("entry_key", "A")), false),
                new com.portal.dto.SubTableBindingScope("18", STORE, List.of(Map.of("entry_key", "A")), false));
        assertDoesNotThrow(() -> TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of(), scopes));
    }

    @Test void nestedChildUsesItsPlacedBindingPermissionsAndParentForeignKey() {
        var definition = new HashMap<>(form("entry_key", true));
        Map<String,Object> parent = (Map<String,Object>) ((List<?>) definition.get("tableBindings")).get(0);
        var child = new HashMap<String,Object>(Map.of("bindingId", "19", "bindingType", "SUB", "bindingMode", "EDITABLE",
                "tableName", "child_entries", "primaryKeyFields", List.of("child_key"), "columns", List.of(
                        Map.of("fieldName", "child_key", "isPrimaryKey", true), Map.of("fieldName", "note", "dataType", "TEXT"),
                        Map.of("fieldName", "parent_key", "isForeignKey", true, "refTableName", "entries", "refPrimaryKeyFields", List.of("entry_key")))));
        definition.put("tableBindings", List.of(parent, child));
        definition.put("configJson", Map.of("subForms", Map.of("17", Map.of("rule", List.of(Map.of("type", "subTable", "_bindingId", "19"))))));
        var oldChild = Map.of("dw:child_entries", List.of(Map.of("child_key", "C", "parent_key", "A", "note", "old")));
        var baseline = payload(List.of(Map.of("entry_key", "A", "amount", 1, "__subTables__", oldChild)));
        var newChild = Map.of("dw:child_entries", List.of(Map.of("child_key", "C", "parent_key", "A", "note", "new")));
        var input = payload(List.of(Map.of("entry_key", "A", "amount", 1, "__subTables__", newChild)));
        Map<?, ?> saved = (Map<?, ?>) rows(TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of())).get(0);
        assertEquals(newChild, saved.get("__subTables__"));
        child.put("bindingMode", "READONLY");
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of()));
        child.put("bindingMode", "EDITABLE");
        var foreign = payload(List.of(Map.of("entry_key", "A", "amount", 1, "__subTables__", Map.of("dw:child_entries",
                List.of(Map.of("child_key", "C", "parent_key", "B", "note", "forged"))))));
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(foreign, baseline, definition, "Tester", List.of()));
        var unplaced = payload(List.of(Map.of("entry_key", "A", "amount", 1, "__subTables__", Map.of("dw:unplaced", List.of()))));
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(unplaced, baseline, definition, "Tester", List.of()));
        var secondParent = new HashMap<>(parent); secondParent.put("bindingId", "18");
        definition.put("tableBindings", List.of(parent, secondParent, child));
        definition.put("configJson", Map.of("subForms", Map.of("18", Map.of("rule", List.of(Map.of("type", "subTable", "_bindingId", "19"))))));
        var scope = new com.portal.dto.SubTableBindingScope("18", STORE, List.of(Map.of("entry_key", "A")), false);
        assertDoesNotThrow(() -> TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of(), List.of(scope)));
        scope.setBindingId("17");
        assertDoesNotThrow(() -> TaskSubTableSubmission.prepare(baseline, baseline, definition, "Tester", List.of(), List.of(scope)));
        assertThrows(PortalException.class, () -> TaskSubTableSubmission.prepare(input, baseline, definition, "Tester", List.of(), List.of(scope)));
    }
}
