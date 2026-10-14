package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubTableBatchRuntimeSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessStartComponent.ActiveCatalogPin pin =
            new ProcessStartComponent.ActiveCatalogPin("catalog-1", "expense", "2026.10");

    @Test
    void returnsEmptyWhenPublishedCatalogHasNoEnabledBatchControl() {
        assertThat(SubTableBatchRuntimeSnapshot.capture(content(config(false, "ctl-1")), pin, objectMapper))
                .isEmpty();
    }

    @Test
    void ignoresLegacyNonBatchFormWithoutSourceId() {
        Map<String, Object> legacy = config(false, null);
        String data;
        try {
            data = objectMapper.writeValueAsString(legacy);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        assertThat(SubTableBatchRuntimeSnapshot.capture(
                Map.of("forms", List.of(Map.of("data", data))), pin, objectMapper)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturesOnlyStrictlyEnabledControlsAndTheirBatchDefinition() {
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config(true, "ctl-1")), pin, objectMapper)
                .orElseThrow();

        assertThat(snapshot).containsEntry("schemaVersion", 1)
                .containsEntry("catalogId", "catalog-1")
                .containsEntry("functionUnitCode", "expense");
        Map<String, Object> form = (Map<String, Object>) ((Map<String, Object>) snapshot.get("forms")).get("form-1");
        Map<String, Object> control = (Map<String, Object>) ((List<?>) form.get("controls")).get(0);
        assertThat(control).containsEntry("batchControlId", "ctl-1").containsEntry("bindingId", "101");
        assertThat((Map<String, Object>) control.get("capabilities"))
                .containsEntry("allowBatchEdit", true).containsEntry("allowBatchAdd", false);
    }

    @Test
    void rejectsEnabledControlWithoutStableIdentityOrBatchForm() {
        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(content(config(true, null)), pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchControlId");

        Map<String, Object> badConfig = config(true, "ctl-1");
        badConfig.remove("batchForms");
        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(content(badConfig), pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("has no batchForms");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsBatchAssignWithoutItsDedicatedAssignmentComponent() {
        Map<String, Object> assignConfig = config(true, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) assignConfig.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchAssign", true));

        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(content(assignConfig), pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires an miAssignment component");
    }

    @Test
    void rejectsBatchAssignWhenThePinnedBpmnHasNoMatchingAssignmentContract() throws Exception {
        Map<String, Object> assignConfig = config(true, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) assignConfig.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchAssign", true));
        assignConfig.put("batchForms", Map.of("101", Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "miAssignment")))));
        Map<String, Object> catalog = Map.of("forms", List.of(Map.of(
                "sourceId", "form-1", "data", objectMapper.writeValueAsString(assignConfig), "formType", "TASK",
                "tableBindings", List.of(Map.of("bindingId", 101, "tableName", "line_items")))));

        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(catalog, pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no matching BPMN assignment contract");
    }

    @Test
    @SuppressWarnings("unchecked")
    void freezesTheMatchingPinnedBpmnAssignmentContract() throws Exception {
        Map<String, Object> assignConfig = config(true, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) assignConfig.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchAssign", true));
        assignConfig.put("batchForms", Map.of("101", Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "miAssignment")))));
        Map<String, Object> catalog = Map.of(
                "forms", List.of(Map.of(
                        "sourceId", "form-1", "data", objectMapper.writeValueAsString(assignConfig), "formType", "TASK",
                        "tableBindings", List.of(Map.of("bindingId", 101, "tableName", "line_items")))),
                "processes", List.of(Map.of("data", bpmnFor("line_items"))));

        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(catalog, pin, objectMapper).orElseThrow();
        Map<String, Object> form = (Map<String, Object>) ((Map<String, Object>) snapshot.get("forms")).get("form-1");
        Map<String, Object> control = (Map<String, Object>) ((List<?>) form.get("controls")).get(0);
        assertThat((Map<String, Object>) control.get("assignmentConfig"))
                .containsEntry("allowUser", true)
                .containsEntry("allowRole", true)
                .containsEntry("assigneeField", "assigned_to")
                .containsEntry("roleField", "role_code")
                .containsEntry("buField", "bu_code");
    }

    @Test
    void rejectsDuplicateEnabledControlIdentityAcrossForms() {
        assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.capture(
                Map.of("forms", List.of(form("form-1", config(true, "duplicate")), form("form-2", config(true, "duplicate")))),
                pin, objectMapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate batchControlId");
    }

    @Test
    void addsSnapshotOnlyToTheLocalPersistenceCopy() {
        Map<String, Object> engineVariables = new java.util.LinkedHashMap<>(Map.of("businessValue", "keep"));

        Map<String, Object> localVariables = ProcessStartComponent.withBatchRuntimeSnapshot(
                engineVariables, content(config(true, "ctl-1")), pin);

        assertThat(engineVariables).containsOnly(Map.entry("businessValue", "keep"));
        assertThat(localVariables).containsEntry("businessValue", "keep")
                .containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializesMembershipVersionsOnlyInTheNewLocalInstanceCopy() {
        Map<String, Object> config = config(false, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) config.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchDelete", true));
        Map<String, Object> engine = Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("line_id", "L1", "name", "original"))));
        Map<String, Object> local = ProcessStartComponent.withBatchRuntimeSnapshot(engine, content(config), pin);
        Map<String, Object> table = (Map<String, Object>) local.get("__subTables__");
        Map<String, Object> row = (Map<String, Object>) ((List<?>) table.get("dw:lines")).get(0);
        assertThat(row).containsEntry("_wsRowVersion", 1);
        assertThat(engine.toString()).doesNotContain("_wsRowVersion");
    }

    @Test
    void projectsOnlyTheCurrentFormsFrozenControls() {
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config(true, "ctl-1")), pin, objectMapper)
                .orElseThrow();
        Map<String, Object> projected = SubTableBatchRuntimeSnapshot.projectForForm(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), "form-1", objectMapper);

        assertThat(projected).containsOnlyKeys("ctl-1");
        assertThat(projected.get("ctl-1")).isInstanceOf(Map.class);
        assertThat(SubTableBatchRuntimeSnapshot.projectForForm(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), "other-form", objectMapper)).isEmpty();
    }

    @Test
    void rejectsDamagedSnapshotRatherThanTreatingItAsALegacyInstance() {
        for (Object damaged : List.of("invalid", Map.of("schemaVersion", 99),
                Map.of("schemaVersion", 1, "forms", List.of()))) {
            assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.projectForForm(
                    Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, damaged), "form-1", objectMapper))
                    .isInstanceOf(com.portal.exception.PortalException.class);
        }
        assertThat(SubTableBatchRuntimeSnapshot.projectForForm(Map.of(), "form-1", objectMapper)).isEmpty();
    }

    @Test
    void rejectsMalformedCurrentFormControls() {
        for (Object controls : List.of("invalid", List.of("not-a-control"), List.of(Map.of("bindingId", "101")))) {
            Map<String, Object> snapshot = Map.of("schemaVersion", 1,
                    "forms", Map.of("form-1", Map.of("controls", controls)));
            assertThatThrownBy(() -> SubTableBatchRuntimeSnapshot.projectForForm(
                    Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), "form-1", objectMapper))
                    .isInstanceOf(com.portal.exception.PortalException.class);
        }
    }

    @Test
    void acceptsOnlyFrozenBatchEditFieldsForTheMatchingControl() {
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config(true, "ctl-1")), pin, objectMapper)
                .orElseThrow();
        SubTableBatchChange change = editChange("ctl-1", "101", "name");

        SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form-1"), List.of(change), objectMapper);

        change.getRowPatches().get(0).setFields(List.of("notInBatchForm"));
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form-1"), List.of(change), objectMapper))
                .hasMessageContaining("outside the frozen batch form");
    }

    @Test
    void rejectsBatchContextForAnotherFormOrBinding() {
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config(true, "ctl-1")), pin, objectMapper)
                .orElseThrow();
        SubTableBatchChange change = editChange("ctl-1", "102", "name");

        assertThatThrownBy(() -> SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form-1"), List.of(change), objectMapper))
                .hasMessageContaining("submitted binding");
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "other-form"), List.of(editChange("ctl-1", "101", "name")), objectMapper))
                .hasMessageContaining("not enabled");
    }

    @Test
    void rejectsAReadOnlyBatchFieldInsteadOfSilentlyDroppingIt() {
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validateFieldPermissions(
                List.of(editChange("ctl-1", "101", "name")), Map.of("101:name", "READONLY")))
                .hasMessageContaining("read-only field");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsOnlyTypedAssignIntentsForTheFrozenAssignmentContract() throws Exception {
        Map<String, Object> assignConfig = config(true, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) assignConfig.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchAssign", true));
        assignConfig.put("batchForms", Map.of("101", Map.of("schemaVersion", 1,
                "rule", List.of(Map.of("type", "miAssignment")))));
        Map<String, Object> catalog = Map.of(
                "forms", List.of(Map.of(
                        "sourceId", "form-1", "data", objectMapper.writeValueAsString(assignConfig), "formType", "TASK",
                        "tableBindings", List.of(Map.of("bindingId", 101, "tableName", "line_items")))),
                "processes", List.of(Map.of("data", bpmnFor("line_items"))));
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(catalog, pin, objectMapper).orElseThrow();
        SubTableBatchChange change = editChange("ctl-1", "101", "ignored");
        change.setOperation("ASSIGN");
        change.getRowPatches().get(0).setFields(null);
        SubTableBatchChange.AssignmentIntent intent = new SubTableBatchChange.AssignmentIntent();
        intent.setMode("PERSON");
        intent.setAssigneeId("user-1");
        change.getRowPatches().get(0).setAssignment(intent);

        SubTableBatchChangeGuard.validate(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form-1"), List.of(change), objectMapper);

        Map<String, Object> submitted = Map.of("__subTables__", Map.of("dw:line_items", List.of(Map.of(
                "line_id", "L1", "assigned_to", "user-1", "role_code", "", "bu_code", ""))));
        SubTableBatchChangeGuard.validateSubmittedAssignments(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form-1"),
                List.of(change), List.of(new SubTableBindingScope("101", "dw:line_items",
                        List.of(Map.of("line_id", "L1")), false)), submitted, objectMapper);

        Map<String, Object> forged = Map.of("__subTables__", Map.of("dw:line_items", List.of(Map.of(
                "line_id", "L1", "assigned_to", "other-user", "role_code", "", "bu_code", ""))));
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validateSubmittedAssignments(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form-1"),
                List.of(change), List.of(new SubTableBindingScope("101", "dw:line_items",
                        List.of(Map.of("line_id", "L1")), false)), forged, objectMapper))
                .hasMessageContaining("canonical fields");

        intent.setMode("ROLE");
        intent.setRoleCode("");
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form-1"),
                List.of(change), objectMapper)).hasMessageContaining("role target");
    }

    @Test
    void requiresTheDeclaredConfiguredRowToExistInTheCanonicalSubmit() {
        SubTableBatchChange change = editChange("ctl-1", "101", "name");
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validateSubmittedRows(
                List.of(change), List.of(new SubTableBindingScope("101", "dw:lines", List.of(Map.of("line_id", "L1")), false)),
                Map.of("__subTables__", Map.of("dw:lines", List.of(Map.of("line_id", "other", "name", "new"))))))
                .hasMessageContaining("does not match a submitted canonical row");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsOnlyExplicitIdentityClaimsForFrozenBatchDelete() {
        Map<String, Object> config = config(false, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) config.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowDelete", true, "allowBatchDelete", true));
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config), pin, objectMapper).orElseThrow();
        SubTableBatchChange change = editChange("ctl-1", "101", "name");
        change.setOperation("DELETE");
        change.getRowPatches().get(0).setFields(null);
        SubTableBatchChangeGuard.validate(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form-1"), List.of(change), objectMapper);

        change.getRowPatches().get(0).setFields(List.of("name"));
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form-1"),
                List.of(change), objectMapper)).hasMessageContaining("Invalid batch delete");
    }

    @Test
    @SuppressWarnings("unchecked")
    void requiresPreparedPersistentIdentityForFrozenBatchAdd() {
        Map<String, Object> config = config(false, "ctl-1");
        Map<String, Object> rule = (Map<String, Object>) ((List<?>) config.get("rule")).get(0);
        rule.put("props", Map.of("_bindingId", 101, "allowAdd", true, "allowBatchAdd", true));
        Map<String, Object> snapshot = SubTableBatchRuntimeSnapshot.capture(content(config), pin, objectMapper).orElseThrow();
        SubTableBatchChange change = editChange("ctl-1", "101", "name");
        change.setOperation("ADD");
        SubTableBatchChangeGuard.validate(Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot),
                Map.of("sourceId", "form-1"), List.of(change), objectMapper);

        change.getRowPatches().get(0).setRowKey(null);
        assertThatThrownBy(() -> SubTableBatchChangeGuard.validate(
                Map.of(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot), Map.of("sourceId", "form-1"),
                List.of(change), objectMapper)).hasMessageContaining("row identity");
    }

    private static SubTableBatchChange editChange(String controlId, String bindingId, String field) {
        SubTableBatchChange.RowPatch patch = new SubTableBatchChange.RowPatch();
        patch.setRowId("row-1");
        patch.setRowKey(Map.of("line_id", "L1"));
        patch.setFields(List.of(field));
        SubTableBatchChange change = new SubTableBatchChange();
        change.setBatchControlId(controlId);
        change.setBindingId(bindingId);
        change.setOperation("EDIT");
        change.setRowPatches(List.of(patch));
        return change;
    }

    private Map<String, Object> content(Map<String, Object> config) {
        try {
            return Map.of("forms", List.of(form("form-1", config)), "dataTables", List.of(Map.of("data",
                    objectMapper.writeValueAsString(Map.of("tableName", "lines", "fields", List.of(
                            Map.of("fieldName", "line_id", "dataType", "TEXT", "isPrimaryKey", true),
                            Map.of("fieldName", "case_ref", "isForeignKey", true, "refTableName", "cases", "refPrimaryKeyFields", List.of("case_key")),
                            Map.of("fieldName", "name", "dataType", "TEXT", "nullable", false))))), Map.of("data",
                    objectMapper.writeValueAsString(Map.of("tableName", "cases", "tableType", "MAIN", "fields", List.of(
                            Map.of("fieldName", "case_key", "dataType", "TEXT", "isPrimaryKey", true)))))));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private Map<String, Object> form(String sourceId, Map<String, Object> config) {
        try {
            return Map.of("sourceId", sourceId, "data", objectMapper.writeValueAsString(
                    Map.of("configJson", config, "fieldPermissions", Map.of())), "formType", "TASK",
                    "tableBindings", List.of(Map.of("bindingId", 101, "tableName", "lines", "bindingType", "SUB",
                            "bindingMode", "EDITABLE", "bindingLinkMode", "structuralFk", "tableId", 20,
                            "foreignKeyField", "case_ref", "fieldDefinitions", List.of(Map.of("fieldName", "unpublished_live_field"))),
                            Map.of("bindingId", 102, "tableName", "cases", "tableId", 10, "bindingType", "PRIMARY")));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, Object> config(boolean enabled, String controlId) {
        Map<String, Object> rule = new java.util.LinkedHashMap<>();
        rule.put("type", "subTable");
        rule.put("batchControlId", controlId);
        rule.put("props", Map.of("_bindingId", 101, "allowBatchEdit", enabled, "allowBatchAdd", "true"));
        return new java.util.LinkedHashMap<>(Map.of(
                "rule", List.of(rule),
                "batchForms", Map.of("101", Map.of("schemaVersion", 1, "rule", List.of(Map.of("type", "input", "field", "name"))))));
    }

    private static String bpmnFor(String tableName) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="process"><bpmn:subProcess id="mi">
                    <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows"/>
                    <bpmn:userTask id="task"><bpmn:extensionElements><custom:properties>
                      <custom:property name="subTableName" value="%s"/>
                      <custom:property name="assigneeMode" value="both"/>
                      <custom:property name="assigneeField" value="assigned_to"/>
                      <custom:property name="roleField" value="role_code"/>
                      <custom:property name="buField" value="bu_code"/>
                    </custom:properties></bpmn:extensionElements></bpmn:userTask>
                  </bpmn:subProcess></bpmn:process>
                </bpmn:definitions>
                """.formatted(tableName);
    }
}
