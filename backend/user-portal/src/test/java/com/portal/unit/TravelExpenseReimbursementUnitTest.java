package com.portal.unit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Travel Expense Reimbursement function unit SQL scripts.
 * Reads SQL files and README from disk and validates their content.
 *
 * Covers tasks 9.1 through 9.6.
 */
class TravelExpenseReimbursementUnitTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path SCRIPTS_DIR = resolveScriptsDir();

    private static String script00;
    private static String script01;
    private static String script02;
    private static String script03;
    private static String readme;

    private static Path resolveScriptsDir() {
        Path candidate = Paths.get("deploy/init-scripts/14-travel-expense-reimbursement");
        if (candidate.toFile().exists()) return candidate;
        candidate = Paths.get("../../deploy/init-scripts/14-travel-expense-reimbursement");
        if (candidate.toFile().exists()) return candidate;
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path scripts = current.resolve("deploy/init-scripts/14-travel-expense-reimbursement");
            if (scripts.toFile().exists()) return scripts;
            current = current.getParent();
        }
        return Paths.get("deploy/init-scripts/14-travel-expense-reimbursement");
    }

    @BeforeAll
    static void loadFiles() throws IOException {
        script00 = Files.readString(SCRIPTS_DIR.resolve("00-create-function-unit.sql"));
        script01 = Files.readString(SCRIPTS_DIR.resolve("01-create-tables.sql"));
        script02 = Files.readString(SCRIPTS_DIR.resolve("02-create-bpmn-process.sql"));
        script03 = Files.readString(SCRIPTS_DIR.resolve("03-form-table-bindings.sql"));
        readme = Files.readString(SCRIPTS_DIR.resolve("README.md"));
    }


    // =========================================================================
    // 9.1 Function unit and form definition validation
    // Validates: Requirements 1.1, 1.2, 3.1, 3.2, 4.1
    // =========================================================================
    @Nested
    @DisplayName("9.1 Function unit and form definition validation")
    class FunctionUnitAndFormDefinitionTest {

        @Test
        @DisplayName("00-create-function-unit.sql contains fu-{date}-{hex} function unit code")
        void containsFunctionUnitCode() {
            assertThat(script00).contains("fu-20260403-a1b2c3");
        }

        @Test
        @DisplayName("00-create-function-unit.sql contains function unit name")
        void containsFunctionUnitName() {
            assertThat(script00).contains("Travel Expense Reimbursement");
        }

        @Test
        @DisplayName("00-create-function-unit.sql contains PUBLISHED status")
        void containsPublishedStatus() {
            assertThat(script00).contains("PUBLISHED");
        }

        @Test
        @DisplayName("00-create-function-unit.sql contains version 1.0.0")
        void containsVersion() {
            assertThat(script00).contains("1.0.0");
        }

        @Test
        @DisplayName("Reimbursement Form config_json contains all 9 fields")
        void reimbursementFormContainsAllFields() {
            // Extract the config_json for Reimbursement Form from the SQL
            List<String> expectedFields = List.of(
                    "reimbursement_number", "apply_date", "applicant_name",
                    "department", "travel_destination", "travel_start_date",
                    "travel_end_date", "travel_purpose", "total_amount"
            );
            for (String field : expectedFields) {
                assertThat(script00)
                        .as("Reimbursement Form config_json should contain field '%s'", field)
                        .contains("\"field\": \"" + field + "\"");
            }
        }

        @Test
        @DisplayName("Reimbursement Form config_json has exactly 9 field entries")
        void reimbursementFormHasExactly9Fields() throws Exception {
            // Extract the JSON string between the first config_json value for Reimbursement Form
            String configJson = extractConfigJson(script00, "Reimbursement Form");
            assertThat(configJson).isNotNull();

            JsonNode root = objectMapper.readTree(configJson);
            JsonNode rule = root.get("rule");
            assertThat(rule).isNotNull();
            assertThat(rule.isArray()).isTrue();
            assertThat(rule.size()).isEqualTo(9);
        }

        @Test
        @DisplayName("Approval Form config_json contains approval_comment field")
        void approvalFormContainsApprovalComment() {
            assertThat(script00).contains("\"field\": \"approval_comment\"");
        }

        @Test
        @DisplayName("Approval Form config_json uses textarea type")
        void approvalFormUsesTextarea() {
            // The approval_comment field should be a textarea
            assertThat(script00).contains("\"type\": \"textarea\"");
        }
    }


    // =========================================================================
    // 9.2 Data table and field definition validation
    // Validates: Requirements 2.1, 2.2, 2.3, 2.4
    // =========================================================================
    @Nested
    @DisplayName("9.2 Data table and field definition validation")
    class DataTableAndFieldDefinitionTest {

        @Test
        @DisplayName("01-create-tables.sql creates reimbursement table as MAIN type")
        void createsReimbursementMainTable() {
            assertThat(script01).contains("'reimbursement'");
            assertThat(script01).contains("'MAIN'");
        }

        @Test
        @DisplayName("01-create-tables.sql creates expense_items table as SUB type")
        void createsExpenseItemsSubTable() {
            assertThat(script01).contains("'expense_items'");
            // SUB appears multiple times for different tables
            assertThat(script01).contains("'SUB'");
        }

        @Test
        @DisplayName("01-create-tables.sql creates invoices table as SUB type")
        void createsInvoicesSubTable() {
            assertThat(script01).contains("'invoices'");
        }

        @Test
        @DisplayName("01-create-tables.sql creates approval_actions table as ACTION type")
        void createsApprovalActionsTable() {
            assertThat(script01).contains("'approval_actions'");
            assertThat(script01).contains("'ACTION'");
        }

        @Test
        @DisplayName("01-create-tables.sql creates 4 tables total")
        void createsFourTables() {
            // Count INSERT INTO dw_table_definitions occurrences
            long tableInsertCount = countOccurrences(script01, "INSERT INTO dw_table_definitions");
            assertThat(tableInsertCount).isEqualTo(4);
        }

        @Test
        @DisplayName("Reimbursement table has 15 fields")
        void reimbursementTableHas15Fields() {
            // Count fields between the reimbursement table INSERT and the next table section
            String reimbursementSection = extractTableFieldSection(script01, "v_reimbursement_table_id", "v_items_table_id");
            long fieldCount = countFieldInserts(reimbursementSection);
            assertThat(fieldCount).as("Reimbursement table should have 15 fields").isEqualTo(15);
        }

        @Test
        @DisplayName("ExpenseItems table has 7 fields")
        void expenseItemsTableHas7Fields() {
            String section = extractTableFieldSection(script01, "v_items_table_id", "v_invoices_table_id");
            long fieldCount = countFieldInserts(section);
            assertThat(fieldCount).as("ExpenseItems table should have 7 fields").isEqualTo(7);
        }

        @Test
        @DisplayName("Invoices table has 15 fields")
        void invoicesTableHas15Fields() {
            String section = extractTableFieldSection(script01, "v_invoices_table_id", "v_action_table_id");
            long fieldCount = countFieldInserts(section);
            assertThat(fieldCount).as("Invoices table should have 15 fields").isEqualTo(15);
        }

        @Test
        @DisplayName("ApprovalActions table has 8 fields")
        void approvalActionsTableHas8Fields() {
            String section = extractTableFieldSection(script01, "v_action_table_id", "Summary");
            long fieldCount = countFieldInserts(section);
            assertThat(fieldCount).as("ApprovalActions table should have 8 fields").isEqualTo(8);
        }
    }


    // =========================================================================
    // 9.3 Action definition and N8N_ACTION config validation
    // Validates: Requirements 5.1, 5.2, 5.3
    // =========================================================================
    @Nested
    @DisplayName("9.3 Action definition and N8N_ACTION config validation")
    class ActionDefinitionTest {

        @Test
        @DisplayName("00-create-function-unit.sql contains 提交报销 action with PROCESS_SUBMIT type")
        void containsSubmitAction() {
            assertThat(script00).contains("'提交报销'");
            assertThat(script00).contains("'PROCESS_SUBMIT'");
        }

        @Test
        @DisplayName("00-create-function-unit.sql contains AI 识别发票 action with N8N_ACTION type")
        void containsN8nAction() {
            assertThat(script00).contains("'AI 识别发票'");
            assertThat(script00).contains("'N8N_ACTION'");
        }

        @Test
        @DisplayName("00-create-function-unit.sql contains 审批通过 action with APPROVE type")
        void containsApproveAction() {
            assertThat(script00).contains("'审批通过'");
            assertThat(script00).contains("'APPROVE'");
        }

        @Test
        @DisplayName("00-create-function-unit.sql contains 审批驳回 action with REJECT type")
        void containsRejectAction() {
            assertThat(script00).contains("'审批驳回'");
            assertThat(script00).contains("'REJECT'");
        }

        @Test
        @DisplayName("N8N_ACTION config contains n8nConfigId")
        void n8nActionContainsConfigId() throws Exception {
            String n8nConfig = extractN8nActionConfig(script00);
            assertThat(n8nConfig).isNotNull();
            JsonNode config = objectMapper.readTree(n8nConfig);
            assertThat(config.has("n8nConfigId")).isTrue();
            assertThat(config.get("n8nConfigId").asText()).isNotBlank();
        }

        @Test
        @DisplayName("N8N_ACTION config contains webhookUrl")
        void n8nActionContainsWebhookUrl() throws Exception {
            String n8nConfig = extractN8nActionConfig(script00);
            JsonNode config = objectMapper.readTree(n8nConfig);
            assertThat(config.has("webhookUrl")).isTrue();
            assertThat(config.get("webhookUrl").asText()).contains("webhook");
        }

        @Test
        @DisplayName("N8N_ACTION config contains inputMapping with file_list param")
        void n8nActionContainsInputMapping() throws Exception {
            String n8nConfig = extractN8nActionConfig(script00);
            JsonNode config = objectMapper.readTree(n8nConfig);
            assertThat(config.has("inputMapping")).isTrue();
            JsonNode inputMapping = config.get("inputMapping");
            assertThat(inputMapping.isArray()).isTrue();
            assertThat(inputMapping.size()).isGreaterThan(0);
            // First entry should have paramType file_list
            JsonNode firstEntry = inputMapping.get(0);
            assertThat(firstEntry.get("paramType").asText()).isEqualTo("file_list");
        }

        @Test
        @DisplayName("N8N_ACTION config contains outputMapping with 3 entries")
        void n8nActionContainsOutputMapping() throws Exception {
            String n8nConfig = extractN8nActionConfig(script00);
            JsonNode config = objectMapper.readTree(n8nConfig);
            assertThat(config.has("outputMapping")).isTrue();
            JsonNode outputMapping = config.get("outputMapping");
            assertThat(outputMapping.isArray()).isTrue();
            assertThat(outputMapping.size()).isEqualTo(3);

            // Verify specific mappings
            List<String> sources = new java.util.ArrayList<>();
            List<String> targets = new java.util.ArrayList<>();
            for (JsonNode entry : outputMapping) {
                sources.add(entry.get("source").asText());
                targets.add(entry.get("target").asText());
            }
            assertThat(sources).contains("expenseItems", "summary.totalAmount", "invoices");
            assertThat(targets).contains("ExpenseItems", "total_amount", "InvoiceRecognitionResults");
        }
    }


    // =========================================================================
    // 9.4 BPMN process definition validation
    // Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.6
    // =========================================================================
    @Nested
    @DisplayName("9.4 BPMN process definition validation")
    class BpmnProcessDefinitionTest {

        @Test
        @DisplayName("02-create-bpmn-process.sql contains StartEvent")
        void containsStartEvent() {
            assertThat(script02).contains("bpmn:startEvent");
            assertThat(script02).contains("StartEvent_1");
        }

        @Test
        @DisplayName("02-create-bpmn-process.sql contains 2 UserTasks")
        void containsTwoUserTasks() {
            assertThat(script02).contains("Task_FillReimbursement");
            assertThat(script02).contains("Task_ManagerApproval");
            // Both should be bpmn:userTask
            long userTaskCount = countOccurrences(script02, "<bpmn:userTask ");
            assertThat(userTaskCount).isEqualTo(2);
        }

        @Test
        @DisplayName("02-create-bpmn-process.sql contains ExclusiveGateway")
        void containsExclusiveGateway() {
            assertThat(script02).contains("bpmn:exclusiveGateway");
            assertThat(script02).contains("Gateway_ApprovalResult");
        }

        @Test
        @DisplayName("02-create-bpmn-process.sql contains 2 EndEvents")
        void containsTwoEndEvents() {
            assertThat(script02).contains("EndEvent_Approved");
            assertThat(script02).contains("EndEvent_Rejected");
            long endEventCount = countOccurrences(script02, "<bpmn:endEvent ");
            assertThat(endEventCount).isEqualTo(2);
        }

        @Test
        @DisplayName("02-create-bpmn-process.sql uses format() for dynamic ID substitution")
        void usesFormatFunction() {
            assertThat(script02).contains("format(");
            // Verify %s placeholders are used for dynamic IDs
            assertThat(script02).contains("%s");
        }

        @Test
        @DisplayName("BPMN contains decision condition expressions")
        void containsDecisionConditions() {
            assertThat(script02).contains("decision == 'yes'");
            assertThat(script02).contains("decision != 'yes'");
        }

        @Test
        @DisplayName("BPMN uses base64 encoding for storage")
        void usesBase64Encoding() {
            assertThat(script02).contains("encode(");
            assertThat(script02).contains("'base64'");
        }
    }


    // =========================================================================
    // 9.5 Form-table binding and subform config validation
    // Validates: Requirements 3.3, 3.4, 3.5, 4.2
    // =========================================================================
    @Nested
    @DisplayName("9.5 Form-table binding and subform config validation")
    class FormTableBindingTest {

        @Test
        @DisplayName("03-form-table-bindings.sql creates PRIMARY/EDITABLE binding")
        void createsPrimaryEditableBinding() {
            assertThat(script03).contains("'PRIMARY'");
            assertThat(script03).contains("'EDITABLE'");
        }

        @Test
        @DisplayName("03-form-table-bindings.sql creates 2 SUB/EDITABLE bindings")
        void createsSubEditableBindings() {
            // expense_items and invoices both get SUB/EDITABLE
            assertThat(script03).contains("'SUB'");
            long subBindingCount = countOccurrences(script03, "'SUB'");
            assertThat(subBindingCount).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("03-form-table-bindings.sql creates PRIMARY/READONLY binding for Approval Form")
        void createsPrimaryReadonlyBinding() {
            assertThat(script03).contains("'READONLY'");
        }

        @Test
        @DisplayName("03-form-table-bindings.sql uses RETURNING id for binding IDs")
        void usesReturningId() {
            long returningCount = countOccurrences(script03, "RETURNING id INTO");
            assertThat(returningCount).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("03-form-table-bindings.sql updates subForms with jsonb_set")
        void updatesSubFormsWithJsonbSet() {
            assertThat(script03).contains("jsonb_set");
            assertThat(script03).contains("subForms");
        }

        @Test
        @DisplayName("SubForms config contains expense_type select field")
        void subFormsContainsExpenseTypeSelect() {
            assertThat(script03).contains("\"field\":\"expense_type\"");
            assertThat(script03).contains("\"type\":\"select\"");
        }

        @Test
        @DisplayName("SubForms expense_type has 4 options")
        void expenseTypeHasFourOptions() {
            assertThat(script03).contains("交通");
            assertThat(script03).contains("住宿");
            assertThat(script03).contains("餐饮");
            assertThat(script03).contains("其他");
        }
    }


    // =========================================================================
    // 9.6 Script file structure validation
    // Validates: Requirements 1.1, 1.4, 7.1, 7.2, 7.3, 7.4, 7.5, 8.1
    // =========================================================================
    @Nested
    @DisplayName("9.6 Script file structure validation")
    class ScriptFileStructureTest {

        @Test
        @DisplayName("All 5 files exist (4 SQL + README)")
        void allFilesExist() {
            assertThat(SCRIPTS_DIR.resolve("00-create-function-unit.sql").toFile()).exists();
            assertThat(SCRIPTS_DIR.resolve("01-create-tables.sql").toFile()).exists();
            assertThat(SCRIPTS_DIR.resolve("02-create-bpmn-process.sql").toFile()).exists();
            assertThat(SCRIPTS_DIR.resolve("03-form-table-bindings.sql").toFile()).exists();
            assertThat(SCRIPTS_DIR.resolve("README.md").toFile()).exists();
        }

        @Test
        @DisplayName("00-create-function-unit.sql has header comment")
        void script00HasHeaderComment() {
            assertThat(script00.trim()).startsWith("--");
        }

        @Test
        @DisplayName("01-create-tables.sql has header comment")
        void script01HasHeaderComment() {
            assertThat(script01.trim()).startsWith("--");
        }

        @Test
        @DisplayName("02-create-bpmn-process.sql has header comment")
        void script02HasHeaderComment() {
            assertThat(script02.trim()).startsWith("--");
        }

        @Test
        @DisplayName("03-form-table-bindings.sql has header comment")
        void script03HasHeaderComment() {
            assertThat(script03.trim()).startsWith("--");
        }

        @Test
        @DisplayName("README contains N8N workflow configuration guide")
        void readmeContainsN8nGuide() {
            assertThat(readme).contains("N8N");
        }

        @Test
        @DisplayName("README contains expected input format section")
        void readmeContainsInputFormat() {
            assertThat(readme).contains("预期输入格式");
            assertThat(readme).contains("invoiceFiles");
        }

        @Test
        @DisplayName("README contains expected output format section")
        void readmeContainsOutputFormat() {
            assertThat(readme).contains("预期输出格式");
            assertThat(readme).contains("invoice_type");
            assertThat(readme).contains("invoice_amount");
        }

        @Test
        @DisplayName("README contains recommended node configuration")
        void readmeContainsNodeConfig() {
            assertThat(readme).contains("推荐节点配置");
            assertThat(readme).contains("Webhook");
        }

        @Test
        @DisplayName("README contains Doubao LLM API guide")
        void readmeContainsDoubaoGuide() {
            assertThat(readme).contains("豆包大模型");
            assertThat(readme).contains("base64");
        }

        @Test
        @DisplayName("README references the N8N workflow template file")
        void readmeReferencesWorkflowTemplate() {
            assertThat(readme).contains("travel-expense-invoice-recognition.json");
        }
    }


    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Extract the config_json string for a given form name from the SQL script.
     * Looks for the form name and then extracts the JSON object that follows.
     */
    private static String extractConfigJson(String sql, String formName) {
        int formIdx = sql.indexOf("'" + formName + "'");
        if (formIdx < 0) return null;

        // Find the config_json value after the form name
        // Look for the pattern: 'config_json string starts with {'
        int searchStart = formIdx;
        int jsonStart = -1;

        // Find the next occurrence of a JSON object starting with '{"rule"'
        String marker = "'{\"rule\"";
        int markerIdx = sql.indexOf(marker, searchStart);
        if (markerIdx < 0) return null;

        // The JSON is enclosed in single quotes in SQL
        jsonStart = markerIdx + 1; // skip the opening '
        // Find the matching closing single quote
        // The JSON ends with }' but we need to handle nested quotes
        int depth = 0;
        int jsonEnd = -1;
        for (int i = jsonStart; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    jsonEnd = i + 1;
                    break;
                }
            }
        }
        if (jsonEnd < 0) return null;
        return sql.substring(jsonStart, jsonEnd);
    }


    /**
     * Extract the N8N_ACTION config_json from the SQL script.
     */
    private static String extractN8nActionConfig(String sql) {
        // Find N8N_ACTION type, then find the config_json before it
        int n8nIdx = sql.indexOf("'N8N_ACTION'");
        if (n8nIdx < 0) return null;

        // Look backwards from N8N_ACTION to find the config_json
        // Actually, look for the JSON that contains n8nConfigId near the N8N_ACTION
        String marker = "'{\"n8nConfigId\"";
        int markerIdx = sql.indexOf(marker);
        if (markerIdx < 0) return null;

        int jsonStart = markerIdx + 1;
        int depth = 0;
        int jsonEnd = -1;
        for (int i = jsonStart; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    jsonEnd = i + 1;
                    break;
                }
            }
        }
        if (jsonEnd < 0) return null;
        return sql.substring(jsonStart, jsonEnd);
    }

    /**
     * Count occurrences of a substring in a string.
     */
    private static long countOccurrences(String text, String sub) {
        long count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }


    /**
     * Extract the field definition section for a specific table variable.
     * Looks for field INSERT statements between two markers.
     */
    private static String extractTableFieldSection(String sql, String startVar, String endMarker) {
        // Find the DELETE FROM dw_field_definitions WHERE table_id = startVar
        String deletePattern = "DELETE FROM dw_field_definitions WHERE table_id = " + startVar;
        int startIdx = sql.indexOf(deletePattern);
        if (startIdx < 0) return "";

        // Find the end marker (next table section or Summary)
        int endIdx = sql.indexOf(endMarker, startIdx + deletePattern.length());
        if (endIdx < 0) endIdx = sql.length();

        return sql.substring(startIdx, endIdx);
    }

    /**
     * Count field INSERT rows in a section.
     * Each field is a tuple starting with (v_xxx_table_id, 'field_name', ...
     */
    private static long countFieldInserts(String section) {
        // Count lines that match the pattern of field value tuples
        // Each field row starts with (v_ and contains a field name
        long count = 0;
        String[] lines = section.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("(v_") && trimmed.contains("'")) {
                count++;
            }
        }
        return count;
    }
}
