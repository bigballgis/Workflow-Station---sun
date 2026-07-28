package com.portal.component;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
/** Resolves a Task Form binding embedded in the deployed BPMN definition. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChangeHistoryBpmnFormResolver {
    static Long resolve(JdbcTemplate jdbcTemplate, String processInstanceId, String stageId) {
        if (processInstanceId == null || processInstanceId.isBlank()) return null;
        try {
            List<String> definitions = jdbcTemplate.queryForList(
                    """
                    SELECT pd.bpmn_xml
                    FROM up_process_instance pi
                    INNER JOIN dw_process_definitions pd
                        ON pd.function_unit_version_id = pi.function_unit_version_id
                        OR pd.function_unit_id IN (
                            SELECT fu.id FROM dw_function_units fu
                            WHERE fu.code = COALESCE(NULLIF(pi.function_unit_code, ''), pi.process_definition_key)
                        )
                    WHERE pi.id = ?
                    ORDER BY (pd.function_unit_version_id = pi.function_unit_version_id) DESC NULLS LAST,
                        pd.id DESC
                    LIMIT 1
                    """, String.class, processInstanceId.trim());
            if (definitions.isEmpty()) return null;
            return resolveTaskFormId(decodeBpmnXml(definitions.get(0)), stageId);
        } catch (RuntimeException ex) {
            log.debug("Could not resolve BPMN task form for process {}, stage {}: {}",
                    processInstanceId, stageId, ex.getMessage());
            return null;
        }
    }
    static Long resolveTaskFormId(String bpmnXml, String stageId) {
        if (bpmnXml == null || bpmnXml.isBlank() || stageId == null || stageId.isBlank()) return null;
        int searchFrom = 0;
        while (searchFrom < bpmnXml.length()) {
            int start = bpmnXml.indexOf("<bpmn:userTask", searchFrom);
            if (start < 0) start = bpmnXml.indexOf("<userTask", searchFrom);
            if (start < 0) return null;
            int end = bpmnXml.indexOf("</bpmn:userTask>", start);
            int closingLength = "</bpmn:userTask>".length();
            if (end < 0) {
                end = bpmnXml.indexOf("</userTask>", start);
                closingLength = "</userTask>".length();
            }
            if (end < 0) return null;
            String taskElement = bpmnXml.substring(start, end + closingLength);
            if (hasXmlAttribute(taskElement, "id", stageId)) {
                String formId = customPropertyValue(taskElement, "formId");
                try {
                    return formId != null ? Long.valueOf(formId.trim()) : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            searchFrom = end + closingLength;
        }
        return null;
    }
    private static String decodeBpmnXml(String stored) {
        if (stored == null) return null;
        String trimmed = stored.trim();
        if (trimmed.startsWith("<")) return trimmed;
        try {
            return new String(Base64.getMimeDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return stored;
        }
    }
    private static boolean hasXmlAttribute(String element, String name, String expected) {
        return element.contains(name + "=\"" + expected + "\"")
                || element.contains(name + "='" + expected + "'");
    }
    private static String customPropertyValue(String element, String propertyName) {
        int searchFrom = 0;
        while (searchFrom < element.length()) {
            int property = element.indexOf("<custom:property", searchFrom);
            if (property < 0) return null;
            int end = element.indexOf('>', property);
            if (end < 0) return null;
            String tag = element.substring(property, end + 1);
            if (hasXmlAttribute(tag, "name", propertyName)) return xmlAttribute(tag, "value");
            searchFrom = end + 1;
        }
        return null;
    }
    private static String xmlAttribute(String element, String name) {
        for (char quote : new char[] {'\"', '\''}) {
            String prefix = name + "=" + quote;
            int start = element.indexOf(prefix);
            if (start < 0) continue;
            start += prefix.length();
            int end = element.indexOf(quote, start);
            if (end >= 0) return element.substring(start, end);
        }
        return null;
    }
}