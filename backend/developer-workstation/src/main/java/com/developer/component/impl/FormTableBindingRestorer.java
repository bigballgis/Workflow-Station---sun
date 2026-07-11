package com.developer.component.impl;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.SubTableViewConfig;
import com.developer.entity.SubTableViewField;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingLinkMode;
import com.developer.enums.BindingMode;
import com.developer.enums.BindingType;
import com.developer.enums.FormType;
import com.developer.enums.SubMode;
import com.developer.enums.TableType;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.FormTableBindingRepository;
import com.developer.repository.SubTableViewConfigRepository;
import com.developer.repository.TableDefinitionRepository;
import com.developer.util.FormConfigJsonBindingIdRewriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rebuilds missing {@link FormTableBinding} rows when rollback/legacy restore left
 * {@code configJson} pointing at stale binding ids (subTable placeholders show "stale").
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FormTableBindingRestorer {

    /** Virtual rt id for platform {@code sys_users} lookup bindings. */
    static final long SYS_USERS_RELATION_TABLE_ID = -1_000_000_001L;

    private static final Pattern LOOKUP_CONFIG_BINDING_ID =
            Pattern.compile("\"bindingId\"\\s*:\\s*(-?\\d+)");

    private final FormDefinitionRepository formDefinitionRepository;
    private final FormTableBindingRepository formTableBindingRepository;
    private final TableDefinitionRepository tableDefinitionRepository;
    private final SubTableViewConfigRepository subTableViewConfigRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void repairFunctionUnitForms(Long functionUnitId) {
        List<TableDefinition> tables = tableDefinitionRepository.findByFunctionUnitIdWithFields(functionUnitId);
        if (tables.isEmpty()) {
            return;
        }
        for (FormDefinition form : formDefinitionRepository.findByFunctionUnitId(functionUnitId)) {
            repairFormIfMissingBindings(form, tables);
        }
    }

    boolean repairFormIfMissingBindings(FormDefinition form, List<TableDefinition> tables) {
        if (form == null || form.getConfigJson() == null || form.getConfigJson().isEmpty()) {
            return false;
        }
        if (formTableBindingRepository.countByFormId(form.getId()) > 0) {
            return false;
        }

        Map<String, Object> configJson = mutableConfigCopy(form.getConfigJson());
        Set<Long> staleBindingIds = collectStaleBindingIds(configJson);
        if (staleBindingIds.isEmpty()) {
            return false;
        }

        Map<Long, Long> bindingIdMapping = new HashMap<>();
        int sortOrder = 0;
        TableDefinition mainTable = resolveMainTable(tables, configJson);
        if (mainTable != null) {
            saveBinding(form, mainTable, null, BindingType.PRIMARY,
                    primaryMode(form), null, BindingLinkMode.structuralFk, null, sortOrder++);
        }

        for (Long staleId : staleBindingIds) {
            String key = String.valueOf(staleId);
            if (isRelationViewBinding(configJson, key)) {
                FormTableBinding related = saveBinding(form, null, SYS_USERS_RELATION_TABLE_ID,
                        BindingType.RELATED, BindingMode.READONLY, null, BindingLinkMode.structuralFk,
                        null, sortOrder++);
                bindingIdMapping.put(staleId, related.getId());
                continue;
            }
            Set<String> columnFields = collectColumnFieldNames(configJson, key);
            TableDefinition subTable = matchSubTable(tables, columnFields, configJson, key);
            if (subTable == null) {
                log.warn("Skipping stale binding {} on form {} — no matching sub-table for fields {}",
                        staleId, form.getId(), columnFields);
                continue;
            }
            String fkField = inferForeignKeyField(subTable, columnFields);
            BindingLinkMode linkMode = "row_id".equals(fkField) ? BindingLinkMode.miParticipantRow
                    : BindingLinkMode.structuralFk;
            FormTableBinding subBinding = saveBinding(form, subTable, null, BindingType.SUB,
                    subBindingMode(form, subTable), fkField, linkMode, SubMode.FULL, sortOrder++);
            attachSubListView(subBinding, configJson, key);
            bindingIdMapping.put(staleId, subBinding.getId());
        }

        if (bindingIdMapping.isEmpty()) {
            return false;
        }

        FormConfigJsonBindingIdRewriter.remapBindingIds(configJson, bindingIdMapping);
        form.setConfigJson(configJson);
        if (mainTable != null && form.getBoundTable() == null) {
            form.setBoundTable(mainTable);
        }
        formDefinitionRepository.save(form);
        log.info("Rebuilt {} form table binding(s) for form {} ({})",
                bindingIdMapping.size(), form.getId(), form.getFormName());
        return true;
    }

    private static Set<Long> collectStaleBindingIds(Map<String, Object> configJson) {
        Set<Long> ids = new LinkedHashSet<>();
        collectRuleBindingIds(configJson.get("rule"), ids);
        collectLookupBindingIds(configJson.get("rule"), ids);
        if (configJson.get("relationViews") instanceof Map<?, ?> relationViews) {
            relationViews.keySet().stream()
                    .map(FormTableBindingRestorer::parseLongKey)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
        }
        return ids;
    }

    private static Long parseLongKey(Object key) {
        if (key == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(key).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectRuleBindingIds(Object ruleNode, Set<Long> ids) {
        if (ruleNode instanceof List<?> list) {
            list.forEach(node -> collectRuleBindingIds(node, ids));
            return;
        }
        if (!(ruleNode instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> node = (Map<String, Object>) rawMap;
        addBindingId(ids, node.get("_bindingId"));
        if (node.get("props") instanceof Map<?, ?> propsRaw) {
            addBindingId(ids, ((Map<String, Object>) propsRaw).get("_bindingId"));
        }
        if (node.get("children") instanceof List<?> children) {
            children.forEach(child -> collectRuleBindingIds(child, ids));
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectLookupBindingIds(Object ruleNode, Set<Long> ids) {
        if (ruleNode instanceof List<?> list) {
            list.forEach(node -> collectLookupBindingIds(node, ids));
            return;
        }
        if (!(ruleNode instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> node = (Map<String, Object>) rawMap;
        if (node.get("props") instanceof Map<?, ?> propsRaw) {
            Object lookupConfig = ((Map<String, Object>) propsRaw).get("lookupConfig");
            if (lookupConfig instanceof String cfg) {
                Matcher matcher = LOOKUP_CONFIG_BINDING_ID.matcher(cfg);
                if (matcher.find()) {
                    ids.add(Long.parseLong(matcher.group(1)));
                }
            }
        }
        if (node.get("children") instanceof List<?> children) {
            children.forEach(child -> collectLookupBindingIds(child, ids));
        }
    }

    private static void addBindingId(Set<Long> ids, Object raw) {
        if (raw instanceof Number number) {
            ids.add(number.longValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean isRelationViewBinding(Map<String, Object> configJson, String key) {
        Object relationViews = configJson.get("relationViews");
        if (!(relationViews instanceof Map<?, ?> map) || !map.containsKey(key)) {
            return false;
        }
        Object entry = map.get(key);
        if (!(entry instanceof Map<?, ?> view)) {
            return true;
        }
        Object allFields = view.get("allFields");
        if (!(allFields instanceof List<?> fields)) {
            return true;
        }
        return fields.stream()
                .filter(Map.class::isInstance)
                .map(f -> (Map<String, Object>) f)
                .anyMatch(f -> "username".equals(f.get("fieldName")));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> collectColumnFieldNames(Map<String, Object> configJson, String key) {
        Set<String> fields = new LinkedHashSet<>();
        collectColumns(configJson.get("subListViews"), key, fields);
        if (fields.isEmpty()) {
            collectSubFormFields(configJson.get("subForms"), key, fields);
        }
        fields.removeIf(name -> name == null || name.isBlank() || name.startsWith("linkForm:"));
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static void collectColumns(Object viewsObj, String key, Set<String> out) {
        if (!(viewsObj instanceof Map<?, ?> views) || !(views.get(key) instanceof Map<?, ?> entry)) {
            return;
        }
        Object columns = ((Map<String, Object>) entry).get("columns");
        if (!(columns instanceof List<?> list)) {
            return;
        }
        for (Object colObj : list) {
            if (colObj instanceof Map<?, ?> col && col.get("fieldName") instanceof String fieldName) {
                out.add(fieldName);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectSubFormFields(Object subFormsObj, String key, Set<String> out) {
        if (!(subFormsObj instanceof Map<?, ?> subForms) || !(subForms.get(key) instanceof Map<?, ?> entry)) {
            return;
        }
        Object rule = ((Map<String, Object>) entry).get("rule");
        if (!(rule instanceof List<?> nodes)) {
            return;
        }
        for (Object nodeObj : nodes) {
            if (nodeObj instanceof Map<?, ?> node && node.get("field") instanceof String field) {
                out.add(field);
            }
        }
    }

    private static TableDefinition resolveMainTable(List<TableDefinition> tables, Map<String, Object> configJson) {
        Set<String> canvasFields = collectCanvasFieldNames(configJson.get("rule"));
        return tables.stream()
                .filter(t -> t.getTableType() == TableType.MAIN)
                .max((a, b) -> Integer.compare(scoreTable(a, canvasFields), scoreTable(b, canvasFields)))
                .orElse(tables.stream().filter(t -> t.getTableType() == TableType.MAIN).findFirst().orElse(null));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> collectCanvasFieldNames(Object ruleNode) {
        Set<String> fields = new HashSet<>();
        if (ruleNode instanceof List<?> list) {
            list.forEach(node -> fields.addAll(collectCanvasFieldNames(node)));
            return fields;
        }
        if (!(ruleNode instanceof Map<?, ?> rawMap)) {
            return fields;
        }
        Map<String, Object> node = (Map<String, Object>) rawMap;
        if (!"subTable".equals(node.get("type")) && node.get("field") instanceof String field) {
            fields.add(field);
        }
        if (node.get("children") instanceof List<?> children) {
            children.forEach(child -> fields.addAll(collectCanvasFieldNames(child)));
        }
        return fields;
    }

    private static int scoreTable(TableDefinition table, Set<String> canvasFields) {
        if (table.getFieldDefinitions() == null || canvasFields.isEmpty()) {
            return 0;
        }
        Set<String> tableFields = table.getFieldDefinitions().stream()
                .map(f -> f.getFieldName())
                .collect(Collectors.toSet());
        return (int) canvasFields.stream().filter(tableFields::contains).count();
    }

    private static TableDefinition matchSubTable(List<TableDefinition> tables,
                                                 Set<String> columnFields,
                                                 Map<String, Object> configJson,
                                                 String key) {
        List<TableDefinition> subTables = tables.stream()
                .filter(t -> t.getTableType() == TableType.SUB)
                .toList();
        if (subTables.isEmpty()) {
            return null;
        }
        if (columnFields.size() == 1 && columnFields.contains("file")) {
            return subTables.stream()
                    .filter(t -> hasField(t, "file"))
                    .findFirst()
                    .orElse(null);
        }
        TableDefinition best = null;
        int bestScore = 0;
        for (TableDefinition candidate : subTables) {
            int score = scoreTable(candidate, columnFields);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null && bestScore > 0) {
            return best;
        }
        Object subForms = configJson.get("subForms");
        if (subForms instanceof Map<?, ?> map && map.containsKey(key)) {
            return subTables.stream().filter(t -> !hasField(t, "file") || columnFields.contains("file"))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static boolean hasField(TableDefinition table, String fieldName) {
        return table.getFieldDefinitions() != null && table.getFieldDefinitions().stream()
                .anyMatch(f -> fieldName.equals(f.getFieldName()));
    }

    private static String inferForeignKeyField(TableDefinition table, Set<String> columnFields) {
        if (columnFields.contains("row_id") || hasField(table, "row_id")) {
            return "row_id";
        }
        if (hasField(table, "case_id")) {
            return "case_id";
        }
        return "row_id";
    }

    private static BindingMode primaryMode(FormDefinition form) {
        return form.getFormType() == FormType.PROCESS ? BindingMode.EDITABLE : BindingMode.READONLY;
    }

    private static BindingMode subBindingMode(FormDefinition form, TableDefinition subTable) {
        if (form.getFormType() == FormType.PROCESS) {
            return BindingMode.EDITABLE;
        }
        if (hasField(subTable, "file")) {
            return BindingMode.EDITABLE;
        }
        return BindingMode.READONLY;
    }

    private FormTableBinding saveBinding(FormDefinition form,
                                         TableDefinition table,
                                         Long relationTableId,
                                         BindingType bindingType,
                                         BindingMode bindingMode,
                                         String foreignKeyField,
                                         BindingLinkMode linkMode,
                                         SubMode subMode,
                                         int sortOrder) {
        FormTableBinding binding = FormTableBinding.builder()
                .form(form)
                .table(table)
                .relationTableId(relationTableId)
                .bindingType(bindingType)
                .bindingMode(bindingMode)
                .foreignKeyField(foreignKeyField)
                .bindingLinkMode(linkMode)
                .sortOrder(sortOrder)
                .subMode(subMode)
                .build();
        return formTableBindingRepository.save(binding);
    }

    @SuppressWarnings("unchecked")
    private void attachSubListView(FormTableBinding binding, Map<String, Object> configJson, String key) {
        Object viewsObj = configJson.get("subListViews");
        if (!(viewsObj instanceof Map<?, ?> views) || !(views.get(key) instanceof Map<?, ?> entry)) {
            return;
        }
        Object columnsObj = ((Map<String, Object>) entry).get("columns");
        if (!(columnsObj instanceof List<?> columns) || columns.isEmpty()) {
            return;
        }
        SubTableViewConfig config = SubTableViewConfig.builder()
                .binding(binding)
                .viewFields(new ArrayList<>())
                .build();
        config = subTableViewConfigRepository.save(config);
        List<SubTableViewField> fields = new ArrayList<>();
        int order = 0;
        for (Object colObj : columns) {
            if (!(colObj instanceof Map<?, ?> colRaw)) {
                continue;
            }
            Map<String, Object> col = (Map<String, Object>) colRaw;
            if (!"field".equals(String.valueOf(col.get("columnType")))
                    && col.get("fieldName") == null) {
                continue;
            }
            String fieldName = Objects.toString(col.get("fieldName"), null);
            if (fieldName == null || fieldName.startsWith("linkForm:")) {
                continue;
            }
            SubTableViewField field = SubTableViewField.builder()
                    .viewConfig(config)
                    .fieldName(fieldName)
                    .displayLabel(col.get("displayName") instanceof String label ? label : fieldName)
                    .sortOrder(order++)
                    .visible(true)
                    .build();
            fields.add(field);
        }
        config.setViewFields(fields);
        subTableViewConfigRepository.save(config);
        binding.setSubListViewId(config.getId());
        formTableBindingRepository.save(binding);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableConfigCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, mutableValue(value)));
        return copy;
    }

    private static Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), mutableValue(v)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(item -> out.add(mutableValue(item)));
            return out;
        }
        return value;
    }
}
