package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the audit payload from the immutable user submission rather than from
 * variables after
 * PK/FK, MI, workflow and audit-field enrichment. Only fields editable in the
 * current form survive.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeHistorySubmissionFilter {
    private static final List<String> ROW_IDENTITY_FIELDS = List.of(
            "row_id", "rowId", "rowID", "id_idw", "_rowKey", "rowKey", "id");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> copyPayload(Map<String, Object> source) {
        if (source == null || source.isEmpty())
            return new LinkedHashMap<>();
        return objectMapper.convertValue(source, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    public Map<String, Object> filterProcessSubmission(String functionUnitCode,
            Map<String, Object> submitted,
            Map<String, Object> enriched) {
        return retainUserEditableSubmission(submitted, enriched,
                loadProcessFormDefinition(functionUnitCode));
    }

    public Map<String, Object> filterTaskSubmission(String processInstanceId,
            String stageId,
            Map<String, Object> submitted,
            Map<String, Object> enriched) {
        return retainUserEditableSubmission(submitted, enriched,
                loadTaskFormDefinition(processInstanceId, stageId));
    }

    public Object filterProcessSubTableBaseline(String functionUnitCode, Object storedSubTables) {
        return filterSubTableBaseline(storedSubTables, loadProcessFormDefinition(functionUnitCode));
    }

    public Object filterTaskSubTableBaseline(String stageId, Object storedSubTables) {
        return filterTaskSubTableBaseline(null, stageId, storedSubTables);
    }

    public Object filterTaskSubTableBaseline(String processInstanceId,
            String stageId,
            Object storedSubTables) {
        return filterSubTableBaseline(storedSubTables,
                loadTaskFormDefinition(processInstanceId, stageId));
    }

    private Object filterSubTableBaseline(Object storedSubTables, Map<String, Object> formDefinition) {
        if (!(storedSubTables instanceof Map<?, ?> tables))
            return null;
        Map<String, Object> wrapper = Map.of("__subTables__", castMap(tables));
        return retainUserEditableSubmission(wrapper, wrapper, formDefinition).get("__subTables__");
    }

    public Map<String, Object> retainUserEditableSubmission(Map<String, Object> submitted,
            Map<String, Object> enriched,
            Map<String, Object> formDefinition) {
        if (submitted == null || submitted.isEmpty() || formDefinition == null || formDefinition.isEmpty()) {
            return Map.of();
        }
        if (truthy(formDefinition.get("readOnly")))
            return Map.of();
        Map<String, Object> config = mapValue(formDefinition.get("configJson"));
        if (config.isEmpty())
            return Map.of();
        Map<String, String> permissions = stringMapValue(formDefinition.get("fieldPermissions"));
        Set<String> topLevelEditable = collectEditableFields(config, permissions);
        Map<String, Set<String>> editableByBinding = collectEditableSubFormFields(config, permissions);
        BindingAliases aliases = resolveBindingContract(
                formDefinition, topLevelEditable, editableByBinding);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : topLevelEditable) {
            if (!submitted.containsKey(field))
                continue;
            // The audit value must be exactly what the user submitted. Enriched values may
            // contain
            // workflow outcomes or normalized system representations of the same field.
            result.put(field, submitted.get(field));
        }
        Object submittedSubTables = submitted.get("__subTables__");
        if (submittedSubTables instanceof Map<?, ?> rawTables) {
            Map<?, ?> enrichedTables = enriched != null && enriched.get("__subTables__") instanceof Map<?, ?> map
                    ? map
                    : Map.of();
            Map<String, Object> filteredTables = filterSubTables(
                    rawTables, enrichedTables, editableByBinding, aliases);
            if (!filteredTables.isEmpty())
                result.put("__subTables__", filteredTables);
        }
        return copyPayload(result);
    }

    private Map<String, Object> filterSubTables(Map<?, ?> submittedTables,
            Map<?, ?> enrichedTables,
            Map<String, Set<String>> editableByBinding,
            BindingAliases aliases) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> bestPriorityByBinding = new HashMap<>();
        Map<String, Map<String, Map<String, Object>>> rowsByTableAndIdentity = new LinkedHashMap<>();
        List<Map.Entry<?, ?>> entries = new ArrayList<>(submittedTables.entrySet());
        entries.sort((left, right) -> {
            int priority = Integer.compare(
                    aliasPriority(left.getKey(), aliases), aliasPriority(right.getKey(), aliases));
            return priority != 0 ? priority
                    : String.valueOf(left.getKey()).compareToIgnoreCase(String.valueOf(right.getKey()));
        });
        for (Map.Entry<?, ?> entry : entries) {
            String rawKey = stringValue(entry.getKey());
            if (rawKey == null || !(entry.getValue() instanceof List<?> submittedRows))
                continue;
            String bindingId = aliases.aliasToBinding().getOrDefault(normalizeAlias(rawKey), rawKey);
            Set<String> editableFields = editableByBinding.get(bindingId);
            if (editableFields == null || editableFields.isEmpty())
                continue;
            int priority = aliasPriority(rawKey, aliases);
            Integer bestPriority = bestPriorityByBinding.putIfAbsent(bindingId, priority);
            if (bestPriority != null && priority > bestPriority)
                continue;
            List<?> enrichedRows = findRows(enrichedTables, rawKey, bindingId, aliases);
            List<Map<String, Object>> filteredRows = new ArrayList<>();
            for (int i = 0; i < submittedRows.size(); i++) {
                if (!(submittedRows.get(i) instanceof Map<?, ?> submittedRow))
                    continue;
                Map<?, ?> enrichedRow = findEnrichedRow(submittedRow, enrichedRows, i);
                Map<String, Object> filteredRow = new LinkedHashMap<>();
                for (String identityField : ROW_IDENTITY_FIELDS) {
                    Object identity = enrichedRow.containsKey(identityField)
                            ? enrichedRow.get(identityField)
                            : submittedRow.get(identityField);
                    if (identity != null)
                        filteredRow.put(identityField, identity);
                }
                for (String field : editableFields) {
                    if (submittedRow.containsKey(field))
                        filteredRow.put(field, submittedRow.get(field));
                }
                // Keep identity-only rows so removal of all editable values can still be
                // compared.
                if (!filteredRow.isEmpty())
                    filteredRows.add(filteredRow);
            }
            // Canonicalize every alias to its physical table name. Otherwise the same row
            // can be
            // recorded twice as, for example, "subtable" and "participants" across task
            // forms.
            // An explicitly submitted empty list remains present to represent delete-all
            // intent.
            String outputKey = aliases.bindingToHistoryName().get(bindingId);
            if (outputKey == null) {
                outputKey = ChangeHistoryComponent.normalizeSubTableNameForHistory(rawKey);
            }
            if (outputKey == null)
                continue;
            Map<String, Map<String, Object>> rowsByIdentity = rowsByTableAndIdentity
                    .computeIfAbsent(outputKey, ignored -> new LinkedHashMap<>());
            if (submittedRows.isEmpty() && priority <= bestPriorityByBinding.get(bindingId)) {
                rowsByIdentity.clear();
            }
            for (Map<String, Object> row : filteredRows) {
                String identity = rowIdentity(row);
                if (identity == null)
                    identity = "__index_" + rowsByIdentity.size();
                rowsByIdentity.putIfAbsent(identity, row);
            }
        }
        rowsByTableAndIdentity.forEach((tableName, rows) -> result.put(tableName, new ArrayList<>(rows.values())));
        return result;
    }

    private int aliasPriority(Object rawKeyValue, BindingAliases aliases) {
        String rawKey = stringValue(rawKeyValue);
        if (rawKey == null)
            return Integer.MAX_VALUE;
        String bindingId = aliases.aliasToBinding().getOrDefault(normalizeAlias(rawKey), rawKey);
        return aliases.aliasPriorities().getOrDefault(normalizeAlias(rawKey), Integer.MAX_VALUE);
    }

    private Map<?, ?> findEnrichedRow(Map<?, ?> submittedRow, List<?> enrichedRows, int fallbackIndex) {
        Set<String> submittedIdentities = rowIdentities(submittedRow);
        if (!submittedIdentities.isEmpty()) {
            for (Object candidate : enrichedRows) {
                if (candidate instanceof Map<?, ?> row
                        && !java.util.Collections.disjoint(submittedIdentities, rowIdentities(row))) {
                    return row;
                }
            }
            return Map.of();
        }
        return fallbackIndex < enrichedRows.size() && enrichedRows.get(fallbackIndex) instanceof Map<?, ?> row
                ? row
                : Map.of();
    }

    private Set<String> rowIdentities(Map<?, ?> row) {
        Set<String> identities = new HashSet<>();
        for (String field : ROW_IDENTITY_FIELDS) {
            String value = stringValue(row.get(field));
            if (value != null)
                identities.add(value);
        }
        return identities;
    }

    private String rowIdentity(Map<String, Object> row) {
        for (String field : ROW_IDENTITY_FIELDS) {
            String value = stringValue(row.get(field));
            if (value != null)
                return field + "=" + value;
        }
        return null;
    }

    private BindingAliases resolveBindingContract(Map<String, Object> formDefinition,
            Set<String> topLevelEditable,
            Map<String, Set<String>> editableByBinding) {
        Map<String, String> aliasToBinding = new HashMap<>();
        Map<String, String> bindingToHistoryName = new HashMap<>();
        Map<String, Integer> aliasPriorities = new HashMap<>();
        editableByBinding.keySet().forEach(id -> {
            aliasToBinding.put(normalizeAlias(id), id);
            aliasPriorities.put(normalizeAlias(id), 0);
        });
        String formId = stringValue(formDefinition.get("formId"));
        if (formId == null)
            return new BindingAliases(aliasToBinding, bindingToHistoryName, aliasPriorities);
        try {
            List<Map<String, Object>> bindings = jdbcTemplate.queryForList(
                    """
                            SELECT binding.id, binding.binding_type, binding.binding_mode,
                                COALESCE(td.table_name, rt.table_name) AS table_name,
                                COALESCE(td.table_display_name, rt.display_name) AS table_display_name,
                                sibling.id AS sibling_id
                            FROM dw_form_definitions form
                            INNER JOIN dw_form_table_bindings binding ON binding.form_id = form.id
                            LEFT JOIN dw_table_definitions td ON td.id = binding.table_id
                            LEFT JOIN rt_table_definitions rt ON rt.id = binding.relation_table_id
                            LEFT JOIN dw_form_definitions sibling_form
                                ON sibling_form.function_unit_id = form.function_unit_id
                            LEFT JOIN dw_form_table_bindings sibling ON sibling.form_id = sibling_form.id
                                AND ((binding.table_id IS NOT NULL AND sibling.table_id = binding.table_id)
                                    OR (binding.relation_table_id > 0
                                        AND sibling.relation_table_id = binding.relation_table_id))
                            WHERE form.id = ?
                            """, Long.valueOf(formId));
            for (Map<String, Object> binding : bindings) {
                String type = stringValue(binding.get("binding_type"));
                String mode = stringValue(binding.get("binding_mode"));
                if ("PRIMARY".equalsIgnoreCase(type) && !"EDITABLE".equalsIgnoreCase(mode)) {
                    topLevelEditable.clear();
                }
                if (!"PRIMARY".equalsIgnoreCase(type) && !"EDITABLE".equalsIgnoreCase(mode)) {
                    String bindingId = stringValue(binding.get("id"));
                    if (bindingId != null)
                        editableByBinding.remove(bindingId);
                }
            }
            for (Map<String, Object> binding : bindings) {
                String bindingId = stringValue(binding.get("id"));
                if (bindingId == null || !editableByBinding.containsKey(bindingId))
                    continue;
                registerAlias(aliasToBinding, aliasPriorities, bindingId, binding.get("table_name"), 1);
                registerAlias(aliasToBinding, aliasPriorities, bindingId, binding.get("table_display_name"), 2);
                registerAlias(aliasToBinding, aliasPriorities, bindingId, binding.get("sibling_id"), 3);
                String tableName = stringValue(binding.get("table_name"));
                if (tableName != null)
                    bindingToHistoryName.putIfAbsent(bindingId, tableName);
            }
        } catch (RuntimeException ex) {
            // Form binding metadata is authoritative. If it cannot be read, fail closed
            // rather
            // than attributing potentially read-only values to the user.
            log.warn("Could not resolve form binding modes for change history form {}: {}",
                    formId, ex.getMessage());
            topLevelEditable.clear();
            editableByBinding.clear();
        }
        return new BindingAliases(aliasToBinding, bindingToHistoryName, aliasPriorities);
    }

    private List<?> findRows(Map<?, ?> enrichedTables,
            String rawKey,
            String bindingId,
            BindingAliases aliases) {
        Object exact = enrichedTables.get(rawKey);
        if (exact instanceof List<?> rows)
            return rows;
        String expectedBinding = aliases.aliasToBinding().getOrDefault(normalizeAlias(rawKey), bindingId);
        for (Map.Entry<?, ?> candidate : enrichedTables.entrySet()) {
            String candidateKey = stringValue(candidate.getKey());
            if (candidateKey == null || !(candidate.getValue() instanceof List<?> rows))
                continue;
            String candidateBinding = aliases.aliasToBinding()
                    .getOrDefault(normalizeAlias(candidateKey), candidateKey);
            if (expectedBinding.equals(candidateBinding))
                return rows;
        }
        return List.of();
    }

    private Set<String> collectEditableFields(Map<String, Object> config,
            Map<String, String> permissions) {
        Set<String> fields = new HashSet<>();
        collectEditableRules(config.get("rule"), permissions, fields, true);
        return fields;
    }

    private Map<String, Set<String>> collectEditableSubFormFields(Map<String, Object> config,
            Map<String, String> permissions) {
        Map<String, Set<String>> result = new HashMap<>();
        Object subForms = config.get("subForms");
        if (!(subForms instanceof Map<?, ?> forms))
            return result;
        for (Map.Entry<?, ?> entry : forms.entrySet()) {
            String bindingId = stringValue(entry.getKey());
            if (bindingId == null || !(entry.getValue() instanceof Map<?, ?> rawConfig))
                continue;
            Set<String> fields = new HashSet<>();
            collectEditableRules(rawConfig.get("rule"), permissions, fields, true);
            result.put(bindingId, fields);
        }
        return result;
    }

    private void collectEditableRules(Object rulesValue,
            Map<String, String> permissions,
            Set<String> fields,
            boolean ancestorEditable) {
        if (!(rulesValue instanceof List<?> rules))
            return;
        for (Object ruleValue : rules) {
            if (!(ruleValue instanceof Map<?, ?> rule))
                continue;
            String field = stringValue(rule.get("field"));
            boolean effectiveEditable = ancestorEditable && isRuleContainerEditable(rule);
            if (field != null && effectiveEditable && isEditableRule(field, rule, permissions))
                fields.add(field);
            collectEditableRules(rule.get("children"), permissions, fields, effectiveEditable);
        }
    }

    private boolean isRuleContainerEditable(Map<?, ?> rule) {
        if (truthy(rule.get("readonly")) || truthy(rule.get("disabled")) || truthy(rule.get("hidden"))) {
            return false;
        }
        if (Boolean.FALSE.equals(rule.get("display")))
            return false;
        Object propsValue = rule.get("props");
        return !(propsValue instanceof Map<?, ?> props)
                || (!truthy(props.get("readonly")) && !truthy(props.get("disabled"))
                        && !truthy(props.get("hidden")));
    }

    private boolean isEditableRule(String field,
            Map<?, ?> rule,
            Map<String, String> permissions) {
        if (field.startsWith("__"))
            return false;
        String permission = permissions.get(field);
        if (permission != null && !"EDITABLE".equalsIgnoreCase(permission))
            return false;
        return isRuleContainerEditable(rule);
    }

    private void registerAlias(Map<String, String> aliases, Map<String, Integer> priorities,
            String bindingId, Object value, int priority) {
        String alias = stringValue(value);
        if (alias == null)
            return;
        String normalized = normalizeAlias(alias);
        aliases.putIfAbsent(normalized, bindingId);
        priorities.putIfAbsent(normalized, priority);
    }

    private Map<String, Object> loadProcessFormDefinition(String functionUnitCode) {
        if (functionUnitCode == null || functionUnitCode.isBlank())
            return Map.of();
        return queryFormDefinition(
                """
                        SELECT fd.id AS form_id, fd.config_json::text AS config_json,
                            fd.field_permissions::text AS field_permissions
                        FROM dw_form_definitions fd
                        INNER JOIN dw_function_units fu ON fu.id = fd.function_unit_id
                        WHERE fu.code = ? AND fd.form_type = 'PROCESS'
                        ORDER BY fd.id DESC LIMIT 1
                        """, functionUnitCode.trim());
    }

    private Map<String, Object> loadTaskFormDefinition(String processInstanceId, String stageId) {
        if (processInstanceId == null || processInstanceId.isBlank()
                || stageId == null || stageId.isBlank())
            return Map.of();
        Long bpmnFormId = ChangeHistoryBpmnFormResolver.resolve(
                jdbcTemplate, processInstanceId, stageId.trim());
        if (bpmnFormId != null) {
            Map<String, Object> deployedForm = queryFormDefinition(
                    """
                            SELECT fd.id AS form_id, fd.config_json::text AS config_json,
                            fd.field_permissions::text AS field_permissions,
                            false AS read_only
                            FROM dw_form_definitions fd
                            WHERE fd.id = ? AND fd.form_type = 'TASK'
                            LIMIT 1
                            """, bpmnFormId);
            if (!deployedForm.isEmpty())
                return deployedForm;
        }
        Map<String, Object> boundForm = queryFormDefinition(
                """
                          SELECT fd.id AS form_id, fd.config_json::text AS config_json,
                              fd.field_permissions::text AS field_permissions,
                              sb.read_only
                        FROM up_process_instance pi
                        INNER JOIN dw_function_units fu
                            ON fu.code = COALESCE(NULLIF(pi.function_unit_code, ''), pi.process_definition_key)
                        INNER JOIN dw_form_definitions fd ON fd.function_unit_id = fu.id
                        INNER JOIN dw_form_stage_bindings sb ON sb.form_id = fd.id
                        WHERE pi.id = ? AND sb.stage_id = ? AND fd.form_type = 'TASK'
                        ORDER BY fd.id DESC LIMIT 1
                        """, processInstanceId.trim(), stageId.trim());
        return boundForm;
    }

    static Long resolveTaskFormId(String bpmnXml, String stageId) {
        return ChangeHistoryBpmnFormResolver.resolveTaskFormId(bpmnXml, stageId);
    }

    private Map<String, Object> queryFormDefinition(String sql, Object... parameters) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
            if (rows.isEmpty())
                return Map.of();
            Map<String, Object> row = rows.get(0);
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("configJson", parseObject(row.get("config_json")));
            definition.put("fieldPermissions", parseStringMap(row.get("field_permissions")));
            definition.put("readOnly", truthy(row.get("read_only")));
            String formId = stringValue(row.get("form_id"));
            if (formId != null)
                definition.put("formId", formId);
            return definition;
        } catch (RuntimeException ex) {
            log.warn("Could not resolve form metadata for user change history: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> parseObject(Object raw) {
        if (raw instanceof Map<?, ?> map)
            return castMap(map);
        if (raw == null || String.valueOf(raw).isBlank())
            return Map.of();
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, String> parseStringMap(Object raw) {
        if (raw instanceof Map<?, ?> map)
            return stringMapValue(map);
        if (raw == null || String.valueOf(raw).isBlank())
            return Map.of();
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null)
                result.put(String.valueOf(key), value);
        });
        return result;
    }

    private static Map<String, String> stringMapValue(Object value) {
        if (!(value instanceof Map<?, ?> map))
            return Map.of();
        Map<String, String> result = new HashMap<>();
        map.forEach((key, item) -> {
            if (key != null && item != null)
                result.put(String.valueOf(key), String.valueOf(item));
        });
        return result;
    }

    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        if (value == null)
            return null;
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeAlias(String value) {
        String normalized = ChangeHistoryComponent.normalizeSubTableNameForHistory(value);
        return normalized != null ? normalized : value.trim().toLowerCase();
    }

    private record BindingAliases(Map<String, String> aliasToBinding,
            Map<String, String> bindingToHistoryName,
            Map<String, Integer> aliasPriorities) {
    }
}