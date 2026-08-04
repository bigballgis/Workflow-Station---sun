package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds fieldName → sensitiveMask config for Change History display.
 * Uses the same access path as change-history (process instance → FU forms),
 * so masks work even when the caller cannot load full process detail.
 *
 * <p><b>Product rule (intentional):</b> Change History uses PROCESS-first,
 * first-wins collection across FU forms for PII display. Stage FormRenderer
 * remains per-form independent and does <em>not</em> use this resolver.
 * If TASK/ACTION later need stage-scoped CH masks, resolve by form/stage — do
 * not silently change this ORDER BY without a product decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeHistorySensitiveMaskResolver {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Map<String, Object>> resolveByProcessInstanceId(String processInstanceId) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return out;
        }
        List<String> configs = loadFormConfigJsons(processInstanceId);
        for (String raw : configs) {
            collectFromConfigJson(out, raw);
        }
        return out;
    }

    private List<String> loadFormConfigJsons(String processInstanceId) {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT fd.config_json::text
                            FROM up_process_instance pi
                            INNER JOIN dw_function_units fu ON fu.code = pi.function_unit_code
                            INNER JOIN dw_form_definitions fd ON fd.function_unit_id = fu.id
                            WHERE pi.process_instance_id = ?
                            ORDER BY CASE fd.form_type
                                WHEN 'PROCESS' THEN 0
                                WHEN 'TASK' THEN 1
                                ELSE 2
                            END,
                            fd.id
                            """,
                    (rs, rowNum) -> rs.getString(1),
                    processInstanceId);
        } catch (Exception ex) {
            log.warn("sensitive-mask lookup failed for process {}: {}", processInstanceId, ex.getMessage());
            return List.of();
        }
    }

    private void collectFromConfigJson(Map<String, Map<String, Object>> out, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(raw, new TypeReference<>() {});
            collectFromRules(out, root.get("rule"));
            Object subForms = root.get("subForms");
            if (subForms instanceof Map<?, ?> map) {
                for (Object sub : map.values()) {
                    if (sub instanceof Map<?, ?> subMap) {
                        collectFromRules(out, subMap.get("rule"));
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("skip malformed form configJson for mask lookup: {}", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFromRules(Map<String, Map<String, Object>> out, Object rulesObj) {
        if (!(rulesObj instanceof List<?> rules)) {
            return;
        }
        Deque<Object> stack = new ArrayDeque<>(rules);
        while (!stack.isEmpty()) {
            Object node = stack.pop();
            if (!(node instanceof Map<?, ?> rule)) {
                continue;
            }
            Object children = rule.get("children");
            if (children instanceof List<?> list) {
                for (Object c : list) {
                    stack.push(c);
                }
            }
            if (!"input".equals(String.valueOf(rule.get("type")))) {
                continue;
            }
            Object field = rule.get("field");
            if (!(field instanceof String fieldName) || fieldName.isBlank() || out.containsKey(fieldName)) {
                continue;
            }
            Object propsObj = rule.get("props");
            if (!(propsObj instanceof Map<?, ?> props)) {
                continue;
            }
            Object inputType = props.get("type");
            if ("textarea".equals(inputType) || "password".equals(inputType)) {
                continue;
            }
            Object maskObj = props.get("sensitiveMask");
            if (!(maskObj instanceof Map<?, ?> mask)) {
                continue;
            }
            if (!Boolean.TRUE.equals(mask.get("enabled"))) {
                continue;
            }
            out.put(fieldName, new LinkedHashMap<>((Map<String, Object>) mask));
        }
    }
}
