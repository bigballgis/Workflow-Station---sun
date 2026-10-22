package com.portal.util;

import com.platform.common.list.ListFilterSql;
import com.portal.component.ActionTableRowRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates View-Design-style filter configs against one {@code up_process_instance} row and,
 * for FORM_POPUP conditions marked {@code source: "ACTION"}, the ACTION table row that the
 * popup submit just wrote to {@code dw_table_data_rows} (runtime_table_id path).
 * Used before publishing post-action email events so only matching rules send mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionEmailRuleConditionEvaluator {

    public static final String ACTION_SOURCE = "ACTION";

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "process_status", "start_time", "initiator", "current_step");

    /** Designed field names are identifiers, so this prefix cannot collide with a MAIN field. */
    private static final String ACTION_FIELD_KEY = "action.";

    private static final MainTableViewColumnSpec.SqlSource ACTION_ROW =
            new MainTableViewColumnSpec.SqlSource("ar.data", false);

    private final JdbcTemplate jdbcTemplate;

    public boolean[] evaluate(String processInstanceId, List<Map<String, Object>> conditions) {
        return evaluate(processInstanceId, conditions, null);
    }

    /**
     * @param processInstanceId Flowable / portal process instance id
     * @param conditions        one filter config per rule; {@code null} or empty means always match
     * @param actionRow         row written by this FORM_POPUP submit; {@code null} for other actions,
     *                          in which case a rule that references ACTION fields never matches
     * @return same length as {@code conditions}; {@code false} when the row is missing. On SQL/runtime
     *         failure, non-trivial slots stay {@code false} (fail-closed: skip mail, do not block Action).
     */
    public boolean[] evaluate(String processInstanceId, List<Map<String, Object>> conditions,
                              ActionTableRowRef actionRow) {
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

        boolean joinActionRow = false;
        for (int i = 0; i < conditions.size(); i++) {
            Map<String, Object> condition = conditions.get(i);
            if (isAlwaysMatch(condition)) {
                result[i] = true;
                continue;
            }
            Set<String> actionFields = new LinkedHashSet<>();
            collectFieldNames(condition, new LinkedHashSet<>(), actionFields);
            if (!actionFields.isEmpty()) {
                if (actionRow == null) {
                    log.warn("[ACTION-EMAIL] skip rule #{} on ACTION fields {}: no action row for process={}",
                            i, actionFields, processInstanceId);
                    continue;
                }
                joinActionRow = true;
            }
            nonTrivialIndexes.add(i);
            nonTrivial.add(withActionFieldKeys(condition));
        }
        if (nonTrivial.isEmpty()) {
            return result;
        }

        try {
            MainTableViewDesignerFilterSql compiler =
                    new MainTableViewDesignerFilterSql(queryableRef(nonTrivial), "action-post-email");
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

            String join = "";
            if (joinActionRow) {
                join = " LEFT JOIN dw_table_data_rows ar ON ar.process_instance_id = pi.id AND ar.row_id = ?";
                params.add(actionRow.rowId());
            }
            params.add(processInstanceId);
            String sql = "SELECT " + String.join(", ", selects)
                    + " FROM up_process_instance pi" + join + " WHERE pi.id = ?";

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

    /** MAIN/system fields read the instance; {@code action.<field>} keys read the joined ACTION row. */
    private static MainTableViewDesignerFilterSql.QueryableRef queryableRef(List<Map<String, Object>> filters) {
        Set<String> mainNames = new LinkedHashSet<>();
        Set<String> actionNames = new LinkedHashSet<>();
        for (Map<String, Object> filter : filters) {
            collectFieldNames(filter, mainNames, actionNames);
        }
        MainTableViewDesignerFilterSql.QueryableRef mainRef = MainTableViewColumnSpec.designerRefFor(
                fieldSources(mainNames, true), MainTableViewColumnSpec.SqlSource.INSTANCE);
        ListFilterSql.ColumnRef actionRef = MainTableViewColumnSpec.columnRefFor(
                fieldSources(actionNames, false), ACTION_ROW);
        return field -> field != null && field.startsWith(ACTION_FIELD_KEY)
                ? actionRef.sqlFor(field.substring(ACTION_FIELD_KEY.length()))
                : mainRef.sqlFor(field);
    }

    private static List<MainTableViewColumnSpec.FieldSource> fieldSources(Set<String> names, boolean main) {
        List<MainTableViewColumnSpec.FieldSource> out = new ArrayList<>(names.size());
        for (String name : names) {
            boolean system = main && SYSTEM_FIELDS.contains(name);
            out.add(new MainTableViewColumnSpec.FieldSource(name, name, system, "text", "text"));
        }
        return out;
    }

    private static boolean isActionSource(Map<?, ?> condition) {
        Object source = condition.get("source");
        return source != null && ACTION_SOURCE.equals(source.toString().trim());
    }

    @SuppressWarnings("unchecked")
    private static void collectFieldNames(Map<String, Object> node, Set<String> mainNames, Set<String> actionNames) {
        if (node == null) {
            return;
        }
        if (node.get("conditions") instanceof List<?> conditions) {
            for (Object item : conditions) {
                if (item instanceof Map<?, ?> map) {
                    Object field = map.get("fieldName");
                    if (field != null && StringUtils.hasText(field.toString())) {
                        (isActionSource(map) ? actionNames : mainNames).add(field.toString().trim());
                    }
                }
            }
        }
        if (node.get("groups") instanceof List<?> groups) {
            for (Object item : groups) {
                if (item instanceof Map<?, ?> map) {
                    collectFieldNames((Map<String, Object>) map, mainNames, actionNames);
                }
            }
        }
    }

    /** Copy of {@code node} whose ACTION-source conditions carry {@code action.<field>} as fieldName. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withActionFieldKeys(Map<String, Object> node) {
        Map<String, Object> copy = new LinkedHashMap<>(node);
        if (node.get("conditions") instanceof List<?> conditions) {
            List<Object> out = new ArrayList<>(conditions.size());
            for (Object item : conditions) {
                if (item instanceof Map<?, ?> map && isActionSource(map) && map.get("fieldName") != null) {
                    Map<String, Object> cond = new HashMap<>((Map<String, Object>) map);
                    cond.put("fieldName", ACTION_FIELD_KEY + map.get("fieldName").toString().trim());
                    out.add(cond);
                } else {
                    out.add(item);
                }
            }
            copy.put("conditions", out);
        }
        if (node.get("groups") instanceof List<?> groups) {
            List<Object> out = new ArrayList<>(groups.size());
            for (Object item : groups) {
                out.add(item instanceof Map<?, ?> map ? withActionFieldKeys((Map<String, Object>) map) : item);
            }
            copy.put("groups", out);
        }
        return copy;
    }
}
