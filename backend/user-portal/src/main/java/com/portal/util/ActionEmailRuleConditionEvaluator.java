package com.portal.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates View-Design-style filter configs against one {@code up_process_instance} row.
 * Used before publishing post-action email events so only matching rules send mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionEmailRuleConditionEvaluator {

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "process_status", "start_time", "initiator", "current_step");

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param processInstanceId Flowable / portal process instance id
     * @param conditions        one filter config per rule; {@code null} or empty means always match
     * @return same length as {@code conditions}; {@code false} when the row is missing. On SQL/runtime
     *         failure, non-trivial slots stay {@code false} (fail-closed: skip mail, do not block Action).
     */
    public boolean[] evaluate(String processInstanceId, List<Map<String, Object>> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return new boolean[0];
        }
        if (!StringUtils.hasText(processInstanceId)) {
            return new boolean[conditions.size()];
        }
        boolean[] result = new boolean[conditions.size()];
        List<Object> params = new ArrayList<>();
        List<String> selects = new ArrayList<>();
        List<Map<String, Object>> nonTrivial = new ArrayList<>();
        List<Integer> nonTrivialIndexes = new ArrayList<>();

        for (int i = 0; i < conditions.size(); i++) {
            Map<String, Object> condition = conditions.get(i);
            if (isAlwaysMatch(condition)) {
                result[i] = true;
                continue;
            }
            nonTrivialIndexes.add(i);
            nonTrivial.add(condition);
        }
        if (nonTrivial.isEmpty()) {
            return result;
        }

        List<MainTableViewColumnSpec.FieldSource> fields = fieldSourcesFor(nonTrivial);
        MainTableViewDesignerFilterSql.QueryableRef ref = MainTableViewColumnSpec.designerRefFor(
                fields, MainTableViewColumnSpec.SqlSource.INSTANCE);
        MainTableViewDesignerFilterSql compiler =
                new MainTableViewDesignerFilterSql(ref, "action-post-email");

        for (int j = 0; j < nonTrivial.size(); j++) {
            List<Object> clauseParams = new ArrayList<>();
            String where = compiler.whereClause(nonTrivial.get(j), clauseParams);
            if (!StringUtils.hasText(where)) {
                selects.add("TRUE AS r" + j);
            } else {
                String expr = where.startsWith(" AND ") ? where.substring(5) : where;
                selects.add("(" + expr + ") AS r" + j);
                params.addAll(clauseParams);
            }
        }

        params.add(processInstanceId);
        String sql = "SELECT " + String.join(", ", selects)
                + " FROM up_process_instance pi WHERE pi.id = ?";

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, params.toArray());
            for (int j = 0; j < nonTrivialIndexes.size(); j++) {
                Object cell = row.get("r" + j);
                result[nonTrivialIndexes.get(j)] = Boolean.TRUE.equals(cell)
                        || "t".equalsIgnoreCase(String.valueOf(cell));
            }
        } catch (Exception e) {
            // FALLBACK(ux): post-action email must not block the Action; treat eval failure as "no match".
            log.error("[ACTION-EMAIL] condition evaluation failed process={} rules={}: {}",
                    processInstanceId, nonTrivialIndexes.size(), e.getMessage());
        }
        return result;
    }

    static boolean isAlwaysMatch(Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Object conditions = condition.get("conditions");
        Object groups = condition.get("groups");
        boolean emptyConditions = !(conditions instanceof List<?> list) || list.isEmpty();
        boolean emptyGroups = !(groups instanceof List<?> g) || g.isEmpty();
        return emptyConditions && emptyGroups;
    }

    private static List<MainTableViewColumnSpec.FieldSource> fieldSourcesFor(
            List<Map<String, Object>> filters) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> filter : filters) {
            collectFieldNames(filter, names);
        }
        List<MainTableViewColumnSpec.FieldSource> out = new ArrayList<>(names.size());
        for (String name : names) {
            boolean system = SYSTEM_FIELDS.contains(name);
            out.add(new MainTableViewColumnSpec.FieldSource(
                    name, name, system, "text", "text"));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectFieldNames(Map<String, Object> node, Set<String> names) {
        if (node == null) {
            return;
        }
        if (node.get("conditions") instanceof List<?> conditions) {
            for (Object item : conditions) {
                if (item instanceof Map<?, ?> map) {
                    Object field = map.get("fieldName");
                    if (field != null && StringUtils.hasText(field.toString())) {
                        names.add(field.toString().trim());
                    }
                }
            }
        }
        if (node.get("groups") instanceof List<?> groups) {
            for (Object item : groups) {
                if (item instanceof Map<?, ?> map) {
                    collectFieldNames((Map<String, Object>) map, names);
                }
            }
        }
    }
}
