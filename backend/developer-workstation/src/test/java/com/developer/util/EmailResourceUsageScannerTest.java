package com.developer.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailResourceUsageScannerTest {

    @Test
    void config_postEmail_matchesUidAndIgnoresOtherFields() {
        Map<String, Object> config = Map.of("postEmail", Map.of(
                "connectionId", "uid-a",
                "emailTemplateId", "12"));

        assertTrue(EmailResourceUsageScanner.configReferencesConnection(config, "uid-a", 10L));
        assertTrue(EmailResourceUsageScanner.configReferencesTemplate(config, 12L));
        assertFalse(EmailResourceUsageScanner.configReferencesConnection(config, "uid-other", 99L));
        assertFalse(EmailResourceUsageScanner.configReferencesTemplate(config, 99L));
    }

    @Test
    void config_postEmailRules_takesPrecedenceOverLegacyPostEmail() {
        Map<String, Object> config = new HashMap<>();
        config.put("postEmail", Map.of("connectionId", "uid-legacy", "emailTemplateId", "1"));
        config.put("postEmailRules", List.of(Map.of("connectionId", "uid-rule", "emailTemplateId", "8")));

        assertTrue(EmailResourceUsageScanner.configReferencesConnection(config, "uid-rule", null));
        assertFalse(EmailResourceUsageScanner.configReferencesConnection(config, "uid-legacy", null));
        assertTrue(EmailResourceUsageScanner.configReferencesTemplate(config, 8L));
        assertFalse(EmailResourceUsageScanner.configReferencesTemplate(config, 1L));
    }

    @Test
    void config_numericConnectionId_matchesExactIdOnly() {
        Map<String, Object> config = Map.of("postEmail", Map.of("connectionId", "10"));

        assertTrue(EmailResourceUsageScanner.configReferencesConnection(config, "uid-x", 10L));
        assertFalse(EmailResourceUsageScanner.configReferencesConnection(config, "uid-x", 100L));
        assertFalse(EmailResourceUsageScanner.configReferencesConnection(config, "uid-x", 1L));
    }

    @Test
    void bpmn_matchesExactPropertyValues() {
        String bpmn = """
                <bpmn:sendTask id="Send_1">
                  <custom:property name="connectionId" value="uid-a" />
                  <custom:property value="12" name="emailTemplateId" />
                </bpmn:sendTask>
                """;

        assertTrue(EmailResourceUsageScanner.bpmnReferencesConnection(bpmn, "uid-a", 10L));
        assertTrue(EmailResourceUsageScanner.bpmnReferencesTemplate(bpmn, 12L));
        assertFalse(EmailResourceUsageScanner.bpmnReferencesConnection(bpmn, "uid-b", 12L));
        assertFalse(EmailResourceUsageScanner.bpmnReferencesTemplate(bpmn, 120L));
    }

    @Test
    void empty_or_null_isUnused() {
        assertFalse(EmailResourceUsageScanner.configReferencesConnection(null, "uid", 1L));
        assertFalse(EmailResourceUsageScanner.configReferencesTemplate(Map.of(), 1L));
        assertFalse(EmailResourceUsageScanner.bpmnReferencesConnection(" ", "uid", 1L));
        assertFalse(EmailResourceUsageScanner.bpmnReferencesTemplate(null, 1L));
    }
}
