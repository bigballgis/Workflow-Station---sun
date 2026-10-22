package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds fieldName → sensitiveMask config for Change History display.
 * Uses the same access path as change-history (process instance → its release's forms),
 * so masks work even when the caller cannot load full process detail.
 *
 * <p><b>Product rule (intentional):</b> Change History uses PROCESS-first,
 * first-wins collection across FU forms for PII display. Stage FormRenderer
 * remains per-form independent and does <em>not</em> use this resolver.
 * If TASK/ACTION later need stage-scoped CH masks, resolve by form/stage — do
 * not silently change this ORDER BY without a product decision.
 */
@Component
@RequiredArgsConstructor
public class ChangeHistorySensitiveMaskResolver {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CatalogSnapshotReader snapshots;

    public Map<String, Map<String, Object>> resolveByProcessInstanceId(String processInstanceId) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return out;
        }
        for (Map<String, Object> config : loadFormConfigs(processInstanceId)) {
            collectFromConfig(out, config);
        }
        return out;
    }

    /**
     * Form configs of the release the request was started on, PROCESS first, then TASK, then the rest.
     * A mask removed from the live designer after release must not unmask that release's history.
     */
    private List<Map<String, Object>> loadFormConfigs(String processInstanceId) {
        List<String> pins = jdbcTemplate.queryForList(
                "SELECT function_unit_catalog_id FROM up_process_instance WHERE id = ?",
                String.class, processInstanceId);
        if (pins.isEmpty()) {
            return List.of();
        }
        String catalogId = pins.get(0);
        if (catalogId == null || catalogId.isBlank()) {
            throw new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", processInstanceId);
        }
        List<Map<String, Object>> forms = new ArrayList<>(snapshots.contents(catalogId, "FORM"));
        forms.sort(Comparator.comparingInt(ChangeHistorySensitiveMaskResolver::formTypeRank)
                .thenComparing(form -> formId(form), Comparator.nullsLast(Comparator.naturalOrder())));
        List<Map<String, Object>> configs = new ArrayList<>();
        for (Map<String, Object> form : forms) {
            configs.add(CatalogPinnedTaskFormLoader.publishedFormConfig(objectMapper, form));
        }
        return configs;
    }

    private static int formTypeRank(Map<String, Object> form) {
        Object type = form.get("formType");
        return "PROCESS".equals(type) ? 0 : "TASK".equals(type) ? 1 : 2;
    }

    private static Long formId(Map<String, Object> form) {
        return form.get("formId") instanceof Number number ? number.longValue() : null;
    }

    private void collectFromConfig(Map<String, Map<String, Object>> out, Map<String, Object> root) {
        collectFromRules(out, root.get("rule"));
        Object subForms = root.get("subForms");
        if (subForms instanceof Map<?, ?> map) {
            for (Object sub : map.values()) {
                if (sub instanceof Map<?, ?> subMap) {
                    collectFromRules(out, subMap.get("rule"));
                }
            }
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
