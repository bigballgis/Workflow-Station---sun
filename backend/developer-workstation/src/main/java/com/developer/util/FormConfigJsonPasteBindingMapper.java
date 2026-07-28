package com.developer.util;

import com.developer.entity.FormTableBinding;
import com.developer.entity.TableDefinition;
import com.developer.enums.BindingType;

import java.util.ArrayList;
import java.util.Comparator;
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
 * Builds stale→target binding / relation-table id maps when a form {@code configJson}
 * is pasted into another Function Unit whose {@link FormTableBinding} ids differ.
 *
 * <p>Does not create bindings — maps onto the target form's existing bindings by
 * field overlap (SUB) / sort-order (RELATED) / single PRIMARY. IDs already present
 * on the target are treated as native (mixed-source safe) and left unchanged.
 */
public final class FormConfigJsonPasteBindingMapper {

    private static final Pattern LOOKUP_BINDING_ID =
            Pattern.compile("\"bindingId\"\\s*:\\s*(-?\\d+)");
    private static final Pattern LOOKUP_TABLE_ID =
            Pattern.compile("\"tableId\"\\s*:\\s*(-?\\d+)");

    private FormConfigJsonPasteBindingMapper() {
    }

    public record MappingResult(
            Map<Long, Long> bindingIdMapping,
            Map<Long, Long> relationTableIdMapping,
            List<Long> unmappedStaleBindingIds,
            boolean mixedSource
    ) {
    }

    public static MappingResult buildMapping(
            Map<String, Object> pastedConfig,
            List<FormTableBinding> targetBindings) {
        return buildMapping(pastedConfig, targetBindings, Map.of());
    }

    /**
     * @param tableIdToFields optional FU table field names (table definition id → fields)
     *                        so SUB matching can use field overlap without LazyInitialization.
     */
    public static MappingResult buildMapping(
            Map<String, Object> pastedConfig,
            List<FormTableBinding> targetBindings,
            Map<Long, Set<String>> tableIdToFields) {
        Map<Long, Long> bindingMap = new LinkedHashMap<>();
        Map<Long, Long> tableMap = new LinkedHashMap<>();
        List<Long> unmapped = new ArrayList<>();
        if (pastedConfig == null || targetBindings == null || targetBindings.isEmpty()) {
            return new MappingResult(bindingMap, tableMap, unmapped, false);
        }

        Set<Long> targetIds = targetBindings.stream()
                .map(FormTableBinding::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> staleIds = collectReferencedBindingIds(pastedConfig);
        boolean mixedSource = false;
        Set<Long> foreignIds = new LinkedHashSet<>();
        for (Long id : staleIds) {
            if (targetIds.contains(id)) {
                mixedSource = true;
            } else {
                foreignIds.add(id);
            }
        }
        if (foreignIds.isEmpty()) {
            return new MappingResult(bindingMap, tableMap, unmapped, mixedSource);
        }

        List<FormTableBinding> targetSubs = typedSorted(targetBindings, BindingType.SUB);
        List<FormTableBinding> targetRelated = typedSorted(targetBindings, BindingType.RELATED);
        FormTableBinding targetPrimary = targetBindings.stream()
                .filter(b -> b.getBindingType() == BindingType.PRIMARY)
                .findFirst()
                .orElse(null);

        Map<Long, Set<String>> fieldsByTable = tableIdToFields != null ? tableIdToFields : Map.of();
        Set<Long> usedTarget = new HashSet<>();
        for (Long staleId : foreignIds) {
            String key = String.valueOf(staleId);
            Long mapped = null;
            if (isRelationLike(pastedConfig, key) || isLookupOnly(pastedConfig, staleId)) {
                mapped = nextUnused(targetRelated, usedTarget);
            } else if (isSubLike(pastedConfig, key)) {
                Set<String> fields = collectColumnFieldNames(pastedConfig, key);
                mapped = matchSubByFields(targetSubs, fields, usedTarget, fieldsByTable);
                if (mapped == null) {
                    mapped = nextUnused(targetSubs, usedTarget);
                }
            } else if (targetPrimary != null && !usedTarget.contains(targetPrimary.getId())) {
                mapped = targetPrimary.getId();
                usedTarget.add(mapped);
            }
            if (mapped != null) {
                bindingMap.put(staleId, mapped);
            } else {
                unmapped.add(staleId);
            }
        }

        collectRelationTableIdMapping(pastedConfig, bindingMap, targetBindings, tableMap);
        return new MappingResult(bindingMap, tableMap, unmapped, mixedSource);
    }

    private static List<FormTableBinding> typedSorted(List<FormTableBinding> bindings, BindingType type) {
        return bindings.stream()
                .filter(b -> b.getBindingType() == type)
                .sorted(BINDING_ORDER)
                .toList();
    }

    private static Long nextUnused(List<FormTableBinding> candidates, Set<Long> used) {
        for (FormTableBinding b : candidates) {
            if (b.getId() != null && used.add(b.getId())) {
                return b.getId();
            }
        }
        return null;
    }

    private static Long matchSubByFields(
            List<FormTableBinding> targetSubs,
            Set<String> fields,
            Set<Long> used,
            Map<Long, Set<String>> fieldsByTable) {
        if (fields.isEmpty()) {
            return null;
        }
        FormTableBinding best = null;
        int bestScore = 0;
        for (FormTableBinding candidate : targetSubs) {
            if (candidate.getId() == null || used.contains(candidate.getId())) {
                continue;
            }
            Set<String> tableFields = resolveTableFields(candidate, fieldsByTable);
            if (tableFields.isEmpty()) {
                continue;
            }
            int score = (int) fields.stream().filter(tableFields::contains).count();
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null && bestScore > 0 && best.getId() != null) {
            used.add(best.getId());
            return best.getId();
        }
        return null;
    }

    private static Set<String> resolveTableFields(
            FormTableBinding candidate,
            Map<Long, Set<String>> fieldsByTable) {
        TableDefinition table = candidate.getTable();
        if (table != null && table.getId() != null && fieldsByTable.containsKey(table.getId())) {
            return fieldsByTable.get(table.getId());
        }
        if (table != null && table.getFieldDefinitions() != null) {
            return table.getFieldDefinitions().stream()
                    .map(f -> f.getFieldName())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private static void collectRelationTableIdMapping(
            Map<String, Object> config,
            Map<Long, Long> bindingMap,
            List<FormTableBinding> targetBindings,
            Map<Long, Long> tableMap) {
        Map<Long, FormTableBinding> byId = targetBindings.stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(FormTableBinding::getId, b -> b, (a, b) -> a));
        walkLookupConfigs(config.get("rule"), (staleBindingId, staleTableId) -> {
            Long targetBindingId = bindingMap.get(staleBindingId);
            if (targetBindingId == null) {
                return;
            }
            FormTableBinding target = byId.get(targetBindingId);
            if (target == null || target.getRelationTableId() == null) {
                return;
            }
            if (staleTableId != null && staleTableId > 0
                    && !Objects.equals(staleTableId, target.getRelationTableId())) {
                tableMap.put(staleTableId, target.getRelationTableId());
            }
        });
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> map) {
            for (Object entry : map.values()) {
                if (entry instanceof Map<?, ?> sub && sub.get("rule") != null) {
                    walkLookupConfigs(sub.get("rule"), (staleBindingId, staleTableId) -> {
                        Long targetBindingId = bindingMap.get(staleBindingId);
                        if (targetBindingId == null) {
                            return;
                        }
                        FormTableBinding target = byId.get(targetBindingId);
                        if (target == null || target.getRelationTableId() == null) {
                            return;
                        }
                        if (staleTableId != null && staleTableId > 0
                                && !Objects.equals(staleTableId, target.getRelationTableId())) {
                            tableMap.put(staleTableId, target.getRelationTableId());
                        }
                    });
                }
            }
        }
    }

    @FunctionalInterface
    private interface LookupVisitor {
        void accept(Long bindingId, Long tableId);
    }

    @SuppressWarnings("unchecked")
    private static void walkLookupConfigs(Object ruleNode, LookupVisitor visitor) {
        if (ruleNode instanceof List<?> list) {
            list.forEach(n -> walkLookupConfigs(n, visitor));
            return;
        }
        if (!(ruleNode instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> node = (Map<String, Object>) raw;
        if (node.get("props") instanceof Map<?, ?> propsRaw) {
            Object cfg = ((Map<String, Object>) propsRaw).get("lookupConfig");
            if (cfg instanceof String s) {
                Long bindingId = firstLong(LOOKUP_BINDING_ID, s);
                Long tableId = firstLong(LOOKUP_TABLE_ID, s);
                if (bindingId != null) {
                    visitor.accept(bindingId, tableId);
                }
            }
        }
        if (node.get("children") instanceof List<?> children) {
            children.forEach(c -> walkLookupConfigs(c, visitor));
        }
    }

    private static Long firstLong(Pattern pattern, String json) {
        Matcher m = pattern.matcher(json);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Binding ids referenced by rule / subForms / lookups / keyed maps. */
    public static Set<Long> collectReferencedBindingIds(Map<String, Object> config) {
        Set<Long> ids = new LinkedHashSet<>();
        if (config == null) {
            return ids;
        }
        collectRuleBindingIds(config.get("rule"), ids);
        collectLookupBindingIds(config.get("rule"), ids);
        collectMapKeys(config.get("subForms"), ids);
        collectMapKeys(config.get("subListViews"), ids);
        collectMapKeys(config.get("relationViews"), ids);
        collectMapKeys(config.get("subTablePortalViews"), ids);
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> map) {
            for (Object entry : map.values()) {
                if (entry instanceof Map<?, ?> sub) {
                    collectRuleBindingIds(sub.get("rule"), ids);
                    collectLookupBindingIds(sub.get("rule"), ids);
                }
            }
        }
        return ids;
    }

    private static void collectMapKeys(Object mapObj, Set<Long> ids) {
        if (!(mapObj instanceof Map<?, ?> map)) {
            return;
        }
        for (Object key : map.keySet()) {
            Long parsed = parseLong(key);
            if (parsed != null) {
                ids.add(parsed);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectRuleBindingIds(Object ruleNode, Set<Long> ids) {
        if (ruleNode instanceof List<?> list) {
            list.forEach(n -> collectRuleBindingIds(n, ids));
            return;
        }
        if (!(ruleNode instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> node = (Map<String, Object>) raw;
        addId(ids, node.get("_bindingId"));
        if (node.get("props") instanceof Map<?, ?> props) {
            addId(ids, ((Map<String, Object>) props).get("_bindingId"));
        }
        if (node.get("children") instanceof List<?> children) {
            children.forEach(c -> collectRuleBindingIds(c, ids));
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectLookupBindingIds(Object ruleNode, Set<Long> ids) {
        if (ruleNode instanceof List<?> list) {
            list.forEach(n -> collectLookupBindingIds(n, ids));
            return;
        }
        if (!(ruleNode instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> node = (Map<String, Object>) raw;
        if (node.get("props") instanceof Map<?, ?> props) {
            Object cfg = ((Map<String, Object>) props).get("lookupConfig");
            if (cfg instanceof String s) {
                Long id = firstLong(LOOKUP_BINDING_ID, s);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (node.get("children") instanceof List<?> children) {
            children.forEach(c -> collectLookupBindingIds(c, ids));
        }
    }

    private static void addId(Set<Long> ids, Object raw) {
        if (raw instanceof Number n) {
            ids.add(n.longValue());
        }
    }

    private static boolean isRelationLike(Map<String, Object> config, String key) {
        Object relationViews = config.get("relationViews");
        return relationViews instanceof Map<?, ?> map && map.containsKey(key);
    }

    private static boolean isSubLike(Map<String, Object> config, String key) {
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> map && map.containsKey(key)) {
            return true;
        }
        Object subListViews = config.get("subListViews");
        if (subListViews instanceof Map<?, ?> map && map.containsKey(key)) {
            return true;
        }
        return ruleHasSubTableBinding(config.get("rule"), key);
    }

    private static boolean isLookupOnly(Map<String, Object> config, Long staleId) {
        Set<Long> lookupIds = new HashSet<>();
        collectLookupBindingIds(config.get("rule"), lookupIds);
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> map) {
            for (Object entry : map.values()) {
                if (entry instanceof Map<?, ?> sub) {
                    collectLookupBindingIds(sub.get("rule"), lookupIds);
                }
            }
        }
        return lookupIds.contains(staleId)
                && !isSubLike(config, String.valueOf(staleId))
                && !isRelationLike(config, String.valueOf(staleId));
    }

    @SuppressWarnings("unchecked")
    private static boolean ruleHasSubTableBinding(Object ruleNode, String key) {
        if (ruleNode instanceof List<?> list) {
            return list.stream().anyMatch(n -> ruleHasSubTableBinding(n, key));
        }
        if (!(ruleNode instanceof Map<?, ?> raw)) {
            return false;
        }
        Map<String, Object> node = (Map<String, Object>) raw;
        if ("subTable".equals(node.get("type"))) {
            Object bid = node.get("_bindingId");
            if (bid == null && node.get("props") instanceof Map<?, ?> props) {
                bid = ((Map<String, Object>) props).get("_bindingId");
            }
            if (key.equals(String.valueOf(bid))) {
                return true;
            }
        }
        if (node.get("children") instanceof List<?> children) {
            return children.stream().anyMatch(c -> ruleHasSubTableBinding(c, key));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> collectColumnFieldNames(Map<String, Object> config, String key) {
        Set<String> fields = new LinkedHashSet<>();
        Object views = config.get("subListViews");
        if (views instanceof Map<?, ?> map && map.get(key) instanceof Map<?, ?> entry) {
            Object columns = ((Map<String, Object>) entry).get("columns");
            if (columns instanceof List<?> list) {
                for (Object col : list) {
                    if (col instanceof Map<?, ?> c && c.get("fieldName") instanceof String name) {
                        fields.add(name);
                    }
                }
            }
        }
        if (fields.isEmpty()) {
            Object subForms = config.get("subForms");
            if (subForms instanceof Map<?, ?> map && map.get(key) instanceof Map<?, ?> entry) {
                Object rule = ((Map<String, Object>) entry).get("rule");
                if (rule instanceof List<?> nodes) {
                    for (Object nodeObj : nodes) {
                        if (nodeObj instanceof Map<?, ?> node && node.get("field") instanceof String field) {
                            fields.add(field);
                        }
                    }
                }
            }
        }
        fields.removeIf(name -> name == null || name.isBlank() || name.startsWith("linkForm:"));
        return fields;
    }

    private static Long parseLong(Object key) {
        if (key == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(key).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final Comparator<FormTableBinding> BINDING_ORDER = Comparator
            .comparing(FormTableBinding::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(FormTableBinding::getId, Comparator.nullsLast(Long::compareTo));
}
