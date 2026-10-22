package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChangeHistorySubmissionFilterTest {

    /**
     * Form id used by {@link #formDefinition}. Production always has one — all three
     * {@code queryFormDefinition} SQL statements select {@code fd.id AS form_id} — and the binding
     * contract (editability per binding, and each binding's DESIGNER primary key) is resolved from
     * it. A fixture without a form id would silently skip that resolution and test a state
     * production never reaches.
     */
    private static final long FORM_ID = 320L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ChangeHistorySubmissionFilter filter =
            new ChangeHistorySubmissionFilter(jdbcTemplate, new ObjectMapper());

    /**
     * The bindings every {@link #formDefinition} carries, as a published form does: EDITABLE
     * sub-tables that declare {@code id} and {@code row_id} as their primary key. Row identity is
     * read from that configured key, not inferred from the column's name.
     */
    private static final List<Map<String, Object>> DEFAULT_BINDINGS = List.of(
            binding("participants", "participants", "Participants"),
            binding("50030", "people", "People"),
            binding("50069", "subtable", "Sub task"));

    private static Map<String, Object> binding(String bindingId, String tableName, String displayName) {
        return Map.of(
                "bindingId", bindingId,
                "bindingType", "SUB",
                "bindingMode", "EDITABLE",
                "tableName", tableName,
                "tableDisplayName", displayName,
                "primaryKeyFields", List.of("id", "row_id"));
    }

    @Test
    void retainsSubmittedEditableBusinessFieldRegardlessOfItsName() {
        Map<String, Object> form = formDefinition(
                List.of(
                        rule("parent_id", false),
                        rule("server_total", true),
                        rule("description", false)),
                Map.of());
        Map<String, Object> submitted = Map.of(
                "parent_id", "CUSTOMER-PARENT",
                "server_total", 999,
                "description", "changed");
        Map<String, Object> enriched = Map.of(
                "parent_id", "CUSTOMER-PARENT",
                "server_total", 42,
                "description", "changed",
                "owner_link", "GENERATED-FK",
                "updated_by", "system");
        Map<String, Object> actual = filter.retainUserEditableSubmission(submitted, enriched, form);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                "parent_id", "CUSTOMER-PARENT",
                "description", "changed"));
        assertThat(actual).doesNotContainKeys("server_total", "owner_link", "updated_by");
    }

    @Test
    void platformAuditFieldsNeverEnterUserChangeHistoryEvenWhenFormRuleLooksEditable() {
        Map<String, Object> form = formDefinition(
                List.of(
                        rule("title", false),
                        rule("created_at", false),
                        rule("created_by", false),
                        rule("updated_at", false),
                        rule("updated_by", false)),
                Map.of());
        Map<String, Object> submitted = Map.of(
                "title", "hello",
                "created_at", "2020-01-01 00:00:00",
                "created_by", "forged",
                "updated_at", "2020-01-01 00:00:00",
                "updated_by", "forged");
        Map<String, Object> actual = filter.retainUserEditableSubmission(submitted, submitted, form);
        assertThat(actual).containsExactly(Map.entry("title", "hello"));
    }

    @Test
    void subTableAuditIsIntersectionOfSubmittedAndEditableFields() {
        Map<String, Object> form = formDefinition(
                List.of(rule("title", false)),
                Map.of("participants", Map.of("rule", List.of(
                        rule("participant_id", false),
                        rule("notes", false),
                        rule("owner_link", true)))));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("participants", List.of(Map.of(
                "participant_id", "BUSINESS-REFERENCE",
                "notes", "user edit",
                "owner_link", "FORGED"))));
        Map<String, Object> enriched = Map.of("__subTables__", Map.of("participants", List.of(Map.of(
                "id", "GENERATED-ROW-ID",
                "participant_id", "BUSINESS-REFERENCE",
                "notes", "user edit",
                "owner_link", "GENERATED-FK",
                "runtime_overlay", "SYSTEM"))));
        Map<String, Object> actual = filter.retainUserEditableSubmission(submitted, enriched, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) tables.get("participants");
        assertThat(rows).containsExactly(Map.of(
                "id", "GENERATED-ROW-ID",
                "participant_id", "BUSINESS-REFERENCE",
                "notes", "user edit"));
        assertThat(rows.get(0)).doesNotContainKeys("owner_link", "runtime_overlay");
    }

    @Test
    void explicitEmptySubTableIsPreservedForUserRowDeletion() {
        Map<String, Object> form = formDefinition(
                List.of(),
                Map.of("participants", Map.of("rule", List.of(rule("notes", false)))));
        Map<String, Object> actual = filter.retainUserEditableSubmission(
                Map.of("__subTables__", Map.of("participants", List.of())),
                Map.of("__subTables__", Map.of("participants", List.of())),
                form);
        assertThat(actual).containsKey("__subTables__");
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        assertThat(tables).containsEntry("participants", List.of());
    }

    @Test
    void readOnlyFormAndDisabledContainerFailClosed() {
        Map<String, Object> readOnlyForm = Map.of(
                "configJson", Map.of("rule", List.of(rule("description", false))),
                "fieldPermissions", Map.of(),
                "readOnly", true);
        assertThat(filter.retainUserEditableSubmission(
                Map.of("description", "forged"), Map.of("description", "forged"), readOnlyForm))
                .isEmpty();
        Map<String, Object> nestedForm = formDefinition(
                List.of(Map.of(
                        "disabled", true,
                        "children", List.of(rule("description", false)))),
                Map.of());
        assertThat(filter.retainUserEditableSubmission(
                Map.of("description", "forged"), Map.of("description", "forged"), nestedForm))
                .isEmpty();
    }

    @Test
    void snapshotFieldKeysIncludeReadonlyNestedConfigFieldsWhenPermissionsEmpty() {
        Map<String, Object> form = Map.of(
                "fieldPermissions", Map.of(),
                "configJson", Map.of("rule", List.of(Map.of(
                        "type", "elCard",
                        "children", List.of(Map.of(
                                "field", "case_number",
                                "readonly", true,
                                "title", "Case Number"))))),
                "readOnly", false);
        assertThat(filter.snapshotFieldKeys(form)).containsExactly("case_number");
    }

    /**
     * Regression for the Task Form read path: with a real-world sparse (deny-list) permission map,
     * the designed EDITABLE fields must still be resolvable.
     *
     * <p>{@code TaskFormComponent#getTaskFormData} used to subset the process variables by
     * {@code fieldPermissions.keySet()}. On FU {@code atm-20260623-gaevus} form 321 that map holds
     * 21 entries that are ALL READONLY, so the "subset" was exactly the read-only fields:
     * {@code case_status} / {@code card_number} were never sent to the client, and the portal
     * echoed the same gap back as {@code baselineValues}, disabling concurrent-edit detection for
     * every field a user can actually change. The field list must come from the form config.
     */
    @Test
    void snapshotFieldKeysCoverEditableFieldsWhenPermissionsAreAllReadonly() {
        Map<String, Object> form = Map.of(
                // Sparse deny-list exactly as the designer persists it: no "EDITABLE" value at all.
                "fieldPermissions", Map.of(
                        "case_number", "READONLY",
                        "row_id", "READONLY"),
                "configJson", Map.of("rule", List.of(Map.of(
                        "type", "elCard",
                        "children", List.of(
                                Map.of("field", "case_number", "readonly", true),
                                Map.of("field", "case_status"),
                                Map.of("field", "card_number"))))),
                "readOnly", false);

        assertThat(filter.snapshotFieldKeys(form))
                .contains("case_status", "card_number")
                .contains("case_number");
    }

    @Test
    void snapshotFieldKeysIgnoreCompositePermissionKeys() {
        Map<String, Object> form = Map.of(
                "fieldPermissions", Map.of(
                        "case_status", "EDITABLE",
                        "1135:assignee_id", "READONLY"),
                "configJson", Map.of("rule", List.of()),
                "readOnly", false);
        assertThat(filter.snapshotFieldKeys(form)).containsExactly("case_status");
    }

    @Test
    void bpmnProcessFormIsUsedWhenStageBindingIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter bpmnFallbackFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="atm">
                    <bpmn:userTask id="Activity_092hlui">
                      <bpmn:extensionElements><custom:properties>
                        <custom:property name="formId" value="320" />
                      </custom:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String encoded = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("process-atm")))
                .thenReturn(List.of(encoded));
        when(jdbcTemplate.queryForList(anyString(), eq(320L))).thenReturn(List.of(Map.of(
                "form_id", "320",
                "config_json", "{\"rule\":[{\"field\":\"case_number\",\"readonly\":true}]}",
                "field_permissions", "{}",
                "read_only", false)));
        com.portal.testsupport.PortalVersionTestSupport.publishedTask(bpmnFallbackFilter, jdbcTemplate,
                "process-atm", "Activity_092hlui", "320", Map.of("configJson", Map.of("rule",
                List.of(Map.of("field", "case_number", "readonly", true))), "fieldPermissions", Map.of()));
        Map<String, Object> form = bpmnFallbackFilter.resolveTaskFormDefinition(
                "process-atm", "Activity_092hlui");
        assertThat(form).isNotEmpty();
        assertThat(bpmnFallbackFilter.snapshotFieldKeys(form)).containsExactly("case_number");
    }

    @Test
    void resolvesTaskFormIdFromBpmnWhenStageBindingIsMissing() {
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="common-process">
                    <bpmn:userTask id="assignment-stage" name="Assignment">
                      <bpmn:extensionElements><custom:properties>
                        <custom:property name="formId" value="50193" />
                      </custom:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        assertThat(ChangeHistorySubmissionFilter.resolveTaskFormId(xml, "assignment-stage"))
                .isEqualTo(50193L);
        assertThat(ChangeHistorySubmissionFilter.resolveTaskFormId(xml, "another-stage")).isNull();
    }

    @Test
    void taskSubmissionUsesBpmnFormWhenStageBindingIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter bpmnFallbackFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="common-process">
                    <bpmn:userTask id="assignment-stage">
                      <bpmn:extensionElements><custom:properties>
                        <custom:property name="formId" value="50193" />
                      </custom:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String encoded = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        when(jdbcTemplate.queryForList(anyString(), eq("process-1"), eq("assignment-stage")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("process-1")))
                .thenReturn(List.of(encoded));
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && !sql.contains("WHERE form.id = ?")), eq(50193L)))
                .thenReturn(List.of(Map.of(
                        "form_id", "50193",
                        "config_json", "{\"rule\":[],\"subForms\":{\"transactions\":{\"rule\":["
                                + "{\"field\":\"assignee_id\"},{\"field\":\"assignee_display_name\",\"readonly\":true}]}}}",
                        "field_permissions", "{}",
                        "read_only", false)));
        // The binding contract: `transactions` is EDITABLE and declares `row_id` as its primary
        // key, which is what preserves that column through the readonly filter. The binding id
        // matches the slice key the submission uses, so the alias resolves.
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("transactions", List.of(Map.of(
                "row_id", "ROW-1",
                "assignee_id", "user-2",
                "assignee_display_name", "System Derived Name"))));
        com.portal.testsupport.PortalVersionTestSupport.publishedTask(bpmnFallbackFilter, jdbcTemplate,
                "process-1", "assignment-stage", "50193", Map.of("configJson", Map.of("rule", List.of(), "subForms",
                Map.of("transactions", Map.of("rule", List.of(Map.of("field", "assignee_id"),
                Map.of("field", "assignee_display_name", "readonly", true))))), "fieldPermissions", Map.of(),
                "tableBindings", List.of(Map.of("bindingId", "transactions", "bindingType", "SUB", "bindingMode", "EDITABLE",
                "tableName", "transactions", "primaryKeyFields", List.of("row_id")))));
        Map<String, Object> actual = bpmnFallbackFilter.filterTaskSubmission(
                "process-1", "assignment-stage", submitted, submitted);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) tables.get("transactions");
        assertThat(rows).containsExactly(Map.of("row_id", "ROW-1", "assignee_id", "user-2"));
    }


    @Test
    void bindingModesExcludeReadOnlyPrimaryAndSubTableValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter bindingModeFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        Map<String, Object> form = Map.of(
                "formId", "50018",
                "tableBindings", List.of(
                        bindingRow(50068L, "PRIMARY", "READONLY", "main", "Main"),
                        bindingRow(50030L, "SUB", "EDITABLE", "people", "People"),
                        bindingRow(50069L, "SUB", "READONLY", "participants", "Participants")),
                "configJson", Map.of(
                        "rule", List.of(rule("lookup", false)),
                        "subForms", Map.of(
                                "50030", Map.of("rule", List.of(rule("age", false))),
                                "50069", Map.of("rule", List.of(rule("name", false))))),
                "fieldPermissions", Map.of());
        Map<String, Object> submitted = new java.util.LinkedHashMap<>();
        submitted.put("lookup", null);
        submitted.put("__subTables__", Map.of(
                "50030", List.of(Map.of("id", "person-1", "age", 1)),
                "50069", List.of(Map.of("id_idw", "participant-1", "name", "12"))));
        Map<String, Object> actual = bindingModeFilter.retainUserEditableSubmission(
                submitted, submitted, form);
        assertThat(actual).doesNotContainKey("lookup");
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        assertThat(tables).containsOnlyKeys("people");
    }

    @Test
    void canonicalDwStoreKeyIsAuditedAsThePhysicalSubTable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter storeKeyFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "1135", Map.of("rule", List.of(
                        rule("card_number", false),
                        rule("assignee_id", false))))));
        form.put("formId", "320");
        form.put("tableBindings", List.of(bindingRow(
                        1135L, "SUB", "EDITABLE", "ATM_Transaction", "Transaction")));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of(
                "dw:atm_transaction", List.of(Map.of(
                        "row_id", "ATM-DC-PW-TRANS-000039",
                        "card_number", "1",
                        "assignee_id", "user-1"))));
        Map<String, Object> actual = storeKeyFilter.retainUserEditableSubmission(
                submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        assertThat(tables).containsOnlyKeys("ATM_Transaction");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) tables.get("ATM_Transaction");
        assertThat(rows).containsExactly(Map.of(
                "row_id", "ATM-DC-PW-TRANS-000039",
                "card_number", "1",
                "assignee_id", "user-1"));
    }

    @Test
    void nestedLinkChildSubTablesAreAuditedAsTheirPhysicalTable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter nestedFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "1144", Map.of("rule", List.of(rule("card_number", false))),
                "1149", Map.of("rule", List.of(
                        rule("correspondence_channel", false),
                        rule("correspondence_type", false))))));
        form.put("formId", "321");
        form.put("tableBindings", List.of(
                        bindingRow(1144L, "SUB", "EDITABLE", "ATM_Transaction", "Transaction", "row_id"),
                        bindingRow(1149L, "SUB", "EDITABLE", "atm_correspondence", "ATM Correspondence", "row_id")));
        Map<String, Object> correspondenceRow = new java.util.LinkedHashMap<>();
        correspondenceRow.put("row_id", "eefe5939-de38-4355-8aee-ca3fdd764278");
        correspondenceRow.put("correspondence_channel", "Email");
        correspondenceRow.put("correspondence_type", "Customer Notification");
        Map<String, Object> nested = new java.util.LinkedHashMap<>();
        nested.put("dw:atm correspondence", List.of(correspondenceRow));
        nested.put("dw:atm_correspondence", List.of(correspondenceRow));
        Map<String, Object> transactionRow = new java.util.LinkedHashMap<>();
        transactionRow.put("row_id", "ATM-DC-PW-TRANS-000040");
        transactionRow.put("card_number", "1");
        transactionRow.put("__subTables__", nested);
        Map<String, Object> submitted = Map.of("__subTables__", Map.of(
                "dw:atm_transaction", List.of(transactionRow)));
        Map<String, Object> actual = nestedFilter.retainUserEditableSubmission(
                submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        assertThat(tables).containsOnlyKeys("ATM_Transaction", "atm_correspondence");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> correspondenceRows =
                (List<Map<String, Object>>) tables.get("atm_correspondence");
        assertThat(correspondenceRows).containsExactly(Map.of(
                "row_id", "eefe5939-de38-4355-8aee-ca3fdd764278",
                "correspondence_channel", "Email",
                "correspondence_type", "Customer Notification"));
    }

    @Test
    void nestedLinkChildWinsOverTopLevelShadowCopyWithNewUuid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter nestedFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "1144", Map.of("rule", List.of(rule("card_number", false))),
                "1149", Map.of("rule", List.of(
                        rule("correspondence_channel", false),
                        rule("correspondence_type", false))))));
        form.put("formId", "321");
        form.put("tableBindings", List.of(
                        bindingRow(1144L, "SUB", "EDITABLE", "ATM_Transaction", "Transaction", "row_id"),
                        bindingRow(1149L, "SUB", "EDITABLE", "atm_correspondence", "ATM Correspondence", "row_id")));
        Map<String, Object> nestedRow = new java.util.LinkedHashMap<>();
        nestedRow.put("row_id", "nested-live");
        nestedRow.put("correspondence_channel", "Letter");
        nestedRow.put("correspondence_type", "Customer Notification");
        Map<String, Object> transactionRow = new java.util.LinkedHashMap<>();
        transactionRow.put("row_id", "ATM-DC-PW-TRANS-000041");
        transactionRow.put("card_number", "1");
        transactionRow.put("__subTables__", Map.of("dw:atm_correspondence", List.of(nestedRow)));
        Map<String, Object> topLevelShadow = new java.util.LinkedHashMap<>();
        topLevelShadow.put("row_id", "nested-live");
        topLevelShadow.put("correspondence_channel", "Email");
        topLevelShadow.put("correspondence_type", "Customer Notification");
        Map<String, Object> submitted = Map.of("__subTables__", Map.of(
                "dw:atm_correspondence", List.of(topLevelShadow),
                "dw:atm_transaction", List.of(transactionRow)));
        Map<String, Object> actual = nestedFilter.retainUserEditableSubmission(
                submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> correspondenceRows =
                (List<Map<String, Object>>) tables.get("atm_correspondence");
        assertThat(correspondenceRows).containsExactly(Map.of(
                "row_id", "nested-live",
                "correspondence_channel", "Letter",
                "correspondence_type", "Customer Notification"));
    }

    @Test
    void twoDistinctNestedRowsAreNotCollapsedToOne() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter nestedFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "1144", Map.of("rule", List.of(rule("card_number", false))),
                "1149", Map.of("rule", List.of(
                        rule("correspondence_channel", false),
                        rule("correspondence_type", false))))));
        form.put("formId", "321");
        form.put("tableBindings", List.of(
                        bindingRow(1144L, "SUB", "EDITABLE", "ATM_Transaction", "Transaction", "row_id"),
                        bindingRow(1149L, "SUB", "EDITABLE", "atm_correspondence", "ATM Correspondence", "row_id")));
        Map<String, Object> first = new java.util.LinkedHashMap<>();
        first.put("row_id", "corr-1");
        first.put("correspondence_channel", "Email");
        first.put("correspondence_type", "Customer Notification");
        Map<String, Object> second = new java.util.LinkedHashMap<>();
        second.put("row_id", "corr-2");
        second.put("correspondence_channel", "Letter");
        second.put("correspondence_type", "Complaint");
        Map<String, Object> transactionRow = new java.util.LinkedHashMap<>();
        transactionRow.put("row_id", "ATM-DC-PW-TRANS-000042");
        transactionRow.put("card_number", "1");
        transactionRow.put("__subTables__", Map.of("dw:atm_correspondence", List.of(first, second)));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of(
                "dw:atm_transaction", List.of(transactionRow)));
        Map<String, Object> actual = nestedFilter.retainUserEditableSubmission(
                submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> correspondenceRows =
                (List<Map<String, Object>>) tables.get("atm_correspondence");
        assertThat(correspondenceRows).containsExactly(
                Map.of("row_id", "corr-1", "correspondence_channel", "Email",
                        "correspondence_type", "Customer Notification"),
                Map.of("row_id", "corr-2", "correspondence_channel", "Letter",
                        "correspondence_type", "Complaint"));
    }

    @Test
    void lookupFieldsRecordConfiguredDisplayValueNotRowJson() {
        Map<String, Object> lookupRule = new java.util.LinkedHashMap<>();
        lookupRule.put("field", "correspondence_channel");
        lookupRule.put("readonly", false);
        lookupRule.put("lookupConfig", Map.of("selectedDisplayField", "standardizations"));
        Map<String, Object> form = formDefinition(
                List.of(),
                Map.of("participants", Map.of("rule", List.of(lookupRule))));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("participants", List.of(Map.of(
                "row_id", "corr-1",
                "correspondence_channel", Map.of(
                        "id", "hmdc-corr-ch-email",
                        "standardizations", "Email",
                        "created_by", "system")))));
        Map<String, Object> actual = filter.retainUserEditableSubmission(submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) tables.get("participants");
        assertThat(rows.get(0).get("correspondence_channel")).isEqualTo("Email");
    }

    @Test
    void topLevelLookupBaselineUsesConfiguredDisplayColumnNotObjectJson() {
        Map<String, Object> lookupRule = new java.util.LinkedHashMap<>();
        lookupRule.put("field", "item_status");
        lookupRule.put("readonly", false);
        lookupRule.put("lookupConfig", Map.of("selectedDisplayField", "status_name"));
        Map<String, Object> form = formDefinition(List.of(lookupRule), Map.of());
        Map<String, Object> stored = Map.of(
                "item_status", Map.of(
                        "id", "st-open",
                        "status_name", "Open",
                        "created_by", "system"));
        Map<String, Object> projected = filter.projectAuditBaseline(stored, form);
        assertThat(projected.get("item_status")).isEqualTo("Open");
        Map<String, Object> submitted = Map.of("item_status", "Open");
        Map<String, Object> current = filter.retainUserEditableSubmission(submitted, submitted, form);
        assertThat(current.get("item_status")).isEqualTo(projected.get("item_status"));
    }

    @Test
    void textualSiblingAliasesUseOnePhysicalHistoryTableName() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter aliasFilter = new ChangeHistorySubmissionFilter(jdbcTemplate, new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "50066", Map.of("rule", List.of(rule("name", false))))));
        form.put("formId", "60002");
        form.put("tableBindings", List.of(bindingRow(
                        50066L, "SUB", "EDITABLE", "subtable", "Participants")));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of(
                "participants", List.of(Map.of("id_idw", "ROW-1", "name", "12"))));
        Map<String, Object> actual = aliasFilter.retainUserEditableSubmission(
                submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        assertThat(tables).containsOnlyKeys("subtable");
    }

    @Test
    void enrichmentRowsAreMatchedByIdentityInsteadOfArrayPosition() {
        Map<String, Object> form = formDefinition(List.of(), Map.of(
                "participants", Map.of("rule", List.of(rule("name", false)))));
        Map<String, Object> submitted = Map.of("__subTables__", Map.of("participants", List.of(
                Map.of("row_id", "ROW-2", "name", "Second"),
                Map.of("row_id", "ROW-1", "name", "First"))));
        Map<String, Object> enriched = Map.of("__subTables__", Map.of("participants", List.of(
                Map.of("row_id", "ROW-1", "id", "PK-1", "name", "First"),
                Map.of("row_id", "ROW-2", "id", "PK-2", "name", "Second"))));
        Map<String, Object> actual = filter.retainUserEditableSubmission(submitted, enriched, form);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> tables = (Map<String, List<Map<String, Object>>>) actual
                .get("__subTables__");
        assertThat(tables.get("participants")).containsExactly(
                Map.of("row_id", "ROW-2", "id", "PK-2", "name", "Second"),
                Map.of("row_id", "ROW-1", "id", "PK-1", "name", "First"));
    }

    @Test
    void businessRowIdentityWinsWhenRowsAlsoContainDatabasePrimaryKeys() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter aliasFilter = new ChangeHistorySubmissionFilter(jdbcTemplate, new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "50066", Map.of("rule", List.of(rule("name", false))))));
        form.put("formId", "60003");
        form.put("tableBindings", List.of(bindingRow(
                        50066L, "SUB", "EDITABLE", "subtable", "Participants")));
        Map<String, Object> aliases = new java.util.LinkedHashMap<>();
        aliases.put("subtable", List.of(Map.of(
                "id", "PK-1", "row_id", "ROW-1", "name", "12")));
        aliases.put("SUBTABLE", List.of(Map.of("row_id", "ROW-1", "name", "12")));
        Map<String, Object> submitted = Map.of("__subTables__", aliases);
        Map<String, Object> actual = aliasFilter.retainUserEditableSubmission(submitted, submitted, form);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> tables = (Map<String, List<Map<String, Object>>>) actual
                .get("__subTables__");
        assertThat(tables.get("subtable")).containsExactly(
                Map.of("id", "PK-1", "row_id", "ROW-1", "name", "12"));
    }

    @Test
    void compositeKeyFieldPermissionDropsOnlyThatSubTableFieldNotSameNamedMainTableField() {
        // "name" is READONLY only for sub-table binding "participants" (composite key);
        // the main-table's own bare "name" field must stay editable — this is exactly the
        // collision the composite-key format exists to prevent.
        Map<String, Object> form = Map.of(
                "formId", String.valueOf(FORM_ID),
                "configJson", Map.of(
                        "rule", List.of(rule("name", false)),
                        "subForms", Map.of("participants", Map.of("rule", List.of(
                                rule("name", false), rule("notes", false))))),
                "fieldPermissions", Map.of("participants:name", "READONLY"),
                "tableBindings", DEFAULT_BINDINGS);
        Map<String, Object> submitted = Map.of(
                "name", "Main Table Name",
                "__subTables__", Map.of("participants", List.of(Map.of(
                        "id", "ROW-1", "name", "Participant Name", "notes", "hello"))));
        Map<String, Object> actual = filter.retainUserEditableSubmission(submitted, submitted, form);
        assertThat(actual).containsEntry("name", "Main Table Name");
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) actual.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) tables.get("participants");
        assertThat(rows).containsExactly(Map.of("id", "ROW-1", "notes", "hello"));
        assertThat(rows.get(0)).doesNotContainKey("name");
    }

    @Test
    void resolveSubFormFieldPermissionsByBindingReturnsOnlyReadonlyCompositeKeys() {
        // Deny-list: EDITABLE composite keys carry no restriction and are excluded from the
        // result — only explicit READONLY entries are returned, keyed by bindingId. A field never
        // mentioned at all (e.g. "assignee" here) is editable by default and never appears either.
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter resolverFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        when(jdbcTemplate.queryForList(anyString(), eq("process-9"), eq("participants-stage")))
                .thenReturn(List.of(Map.of(
                        "form_id", "70001",
                        "config_json", "{}",
                        "field_permissions",
                        "{\"bu_code\":\"READONLY\",\"50544:bu_code\":\"READONLY\",\"50544:name\":\"EDITABLE\"}",
                        "read_only", false)));
        com.portal.testsupport.PortalVersionTestSupport.publishedTask(resolverFilter, jdbcTemplate,
                "process-9", "participants-stage", "70001", Map.of("configJson", Map.of(), "fieldPermissions", Map.of("bu_code", "READONLY", "50544:bu_code", "READONLY", "50544:name", "EDITABLE")));
        Map<String, java.util.Set<String>> firstResult = resolverFilter
                .resolveSubFormFieldPermissionsByBinding("process-9", "participants-stage");
        assertThat(firstResult).containsOnlyKeys("50544");
        assertThat(firstResult.get("50544")).containsExactly("bu_code");
    }

    @Test
    void resolveSubFormFieldPermissionsByBindingIsEmptyWhenNoCompositeKeysConfigured() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter resolverFilter = new ChangeHistorySubmissionFilter(jdbcTemplate,
                new ObjectMapper());
        when(jdbcTemplate.queryForList(anyString(), eq("process-9"), eq("participants-stage")))
                .thenReturn(List.of(Map.of(
                        "form_id", "70001",
                        "config_json", "{}",
                        "field_permissions", "{\"name\":\"READONLY\"}",
                        "read_only", false)));
        com.portal.testsupport.PortalVersionTestSupport.publishedTask(resolverFilter, jdbcTemplate,
                "process-9", "participants-stage", "70001", Map.of("configJson", Map.of(), "fieldPermissions", Map.of("name", "READONLY")));
        Map<String, java.util.Set<String>> result = resolverFilter
                .resolveSubFormFieldPermissionsByBinding("process-9", "participants-stage");
        assertThat(result).isEmpty();
    }

    @Test
    void aliasSlicesWithDifferentRowIdsKeepOnlyTheHighestPrioritySlice() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter aliasFilter = new ChangeHistorySubmissionFilter(jdbcTemplate, new ObjectMapper());
        Map<String, Object> form = new java.util.LinkedHashMap<>(formDefinition(List.of(), Map.of(
                "1301", Map.of("rule", List.of(rule("channel", false))))));
        form.put("formId", "341");
        form.put("tableBindings", List.of(bindingRow(
                        1301L, "SUB", "EDITABLE", "acq_correspondence", "ACQ Correspondence")));
        Map<String, Object> aliases = new java.util.LinkedHashMap<>();
        aliases.put("1301", List.of(Map.of("row_id", "uuid-A", "channel", "Email")));
        aliases.put("ACQ Correspondence", List.of(Map.of("row_id", "uuid-B", "channel", "Email")));
        aliases.put("acq correspondence", List.of(Map.of("row_id", "uuid-C", "channel", "Email")));
        Map<String, Object> actual = aliasFilter.retainUserEditableSubmission(
                Map.of("__subTables__", aliases), Map.of("__subTables__", aliases), form);
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> tables =
                (Map<String, List<Map<String, Object>>>) actual.get("__subTables__");
        assertThat(tables).containsOnlyKeys("acq_correspondence");
        assertThat(tables.get("acq_correspondence")).containsExactly(
                Map.of("row_id", "uuid-A", "channel", "Email"));
    }

    @Test
    void processStartAuditUsesTheStartFormOfTheRequestsRelease() {
        ChangeHistorySubmissionFilter processFilter = new ChangeHistorySubmissionFilter(
                mock(JdbcTemplate.class), new ObjectMapper());
        var snapshots = mock(com.platform.common.functionunit.CatalogSnapshotReader.class);
        org.springframework.test.util.ReflectionTestUtils.setField(processFilter, "versionSnapshots", snapshots);
        // The release's start form (task scene), with its published bindings: the MI participants
        // table declares `id_idw` as its primary key, which is why that column survives filtering.
        when(snapshots.processForm("release-1", false)).thenReturn(Map.of(
                "formId", "50193",
                "configJson", Map.of(
                        "rule", List.of(Map.of("field", "I"), Map.of("field", "id", "readonly", true),
                                Map.of("field", "lookup")),
                        "subForms", Map.of(
                                "50627", Map.of("rule", List.of(Map.of("field", "name"), Map.of("field", "assignee"))),
                                "50553", Map.of("rule", List.of(Map.of("field", "file"))))),
                "fieldPermissions", Map.of(),
                "tableBindings", List.of(
                        bindingRow(50549L, "PRIMARY", "EDITABLE", "main", "Meeting"),
                        bindingRow(50627L, "SUB", "EDITABLE", "subtable", "Participants", "id_idw"),
                        bindingRow(50553L, "SUB", "EDITABLE", "attachment", "Attachment"))));
        Map<String, Object> submitted = new java.util.LinkedHashMap<>();
        submitted.put("I", "1");
        submitted.put("id", "Meeting-000004");
        submitted.put("__request_id", "1_Meeting-000004");
        submitted.put("lookup", Map.of("username", "admin"));
        submitted.put("created_by", "system");
        submitted.put("__subTables__", Map.of(
                "50627", List.of(Map.of(
                        "id_idw", "Test-000004",
                        "name", "1",
                        "assignee", "liam",
                        "task_status", "IN_PROGRESS")),
                "50553", List.of(Map.of("id", "file-1", "file", "/upload/a.jpg"))));
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(submitted);
        enriched.put("functionUnitId", "release-1");
        Map<String, Object> actual = processFilter.filterProcessSubmission(
                "fu-20260422-23tfag", submitted, enriched);
        assertThat(actual).containsEntry("I", "1").containsKey("lookup")
                .doesNotContainKeys("id", "__request_id", "created_by");
        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> tables =
                (Map<String, List<Map<String, Object>>>) actual.get("__subTables__");
        assertThat(tables).containsOnlyKeys("subtable", "attachment");
        assertThat(tables.get("subtable")).containsExactly(Map.of(
                "id_idw", "Test-000004", "name", "1", "assignee", "liam"));
        assertThat(tables.get("attachment")).containsExactly(Map.of("id", "file-1", "file", "/upload/a.jpg"));
    }

    @Test
    void processStartAuditWithoutAPinnedReleaseIsRefusedRatherThanReadFromTheDesigner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter processFilter = new ChangeHistorySubmissionFilter(jdbc, new ObjectMapper());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processFilter.filterProcessSubmission(
                        "fu-20260422-23tfag", Map.of("I", "1"), Map.of("I", "1")))
                .isInstanceOf(com.platform.common.functionunit.FunctionUnitVersionException.class);
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void sameRowIdentifierInDifferentTablesIsNotDeduplicated() {
        Map<String, List<Map<String, Object>>> normalized = ChangeHistoryComponent
                .normalizeSubTableRowsByHistoryName(Map.of(
                        "alpha", List.of(Map.of("id", "1", "name", "Alpha")),
                        "beta", List.of(Map.of("id", "1", "name", "Beta"))));
        assertThat(normalized.get("alpha")).hasSize(1);
        assertThat(normalized.get("beta")).hasSize(1);
    }

    private static Map<String, Object> formDefinition(List<Map<String, Object>> rules,
            Map<String, Object> subForms) {
        return Map.of(
                "configJson", Map.of("rule", rules, "subForms", subForms),
                "fieldPermissions", Map.of(),
                "formId", String.valueOf(FORM_ID),
                "tableBindings", DEFAULT_BINDINGS);
    }

    private static Map<String, Object> rule(String field, boolean readonly) {
        return Map.of("field", field, "readonly", readonly);
    }

    private static Map<String, Object> bindingRow(long id, String type, String mode,
            String tableName, String displayName) {
        return bindingRow(id, type, mode, tableName, displayName, "id", "row_id");
    }

    /**
     * @param primaryKeyFields every column marked {@code isPrimaryKey} on this
     *                         table. Composite keys require all of them filled;
     *                         a fixture that only stamps {@code row_id} must pass
     *                         {@code "row_id"} alone, not {@code "id","row_id"}.
     */
    private static Map<String, Object> bindingRow(long id, String type, String mode,
            String tableName, String displayName, String... primaryKeyFields) {
        return Map.of(
                "bindingId", String.valueOf(id),
                "bindingType", type,
                "bindingMode", mode,
                "tableName", tableName,
                "tableDisplayName", displayName,
                "primaryKeyFields", List.of(primaryKeyFields));
    }
}
