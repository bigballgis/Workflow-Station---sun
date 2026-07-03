package com.developer.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Map fields in form configJson keyed by bindingId to new bindingIds (shared by clone / import).
 * Also rewrites {@code _bindingId} on subTable placeholders in the canvas rule tree so bindings are not lost after export/import/clone.
 * Lookup fields persist their binding inside a {@code lookupConfig} JSON string ({@code {"bindingId":N,...}});
 * its {@code bindingId} is remapped too, otherwise imported Lookup configs point at stale bindings (show raw id).
 *
 * <p>{@link #remapIds} additionally rewrites cross-entity references that only import needs:
 * <ul>
 *   <li>{@code subTablePortalViews} values' {@code assigneeTodoFormSource.formId} / {@code .linkFormColumnId}
 *       (negative = virtual per-binding link column, positive = dw_link_form_components id);</li>
 *   <li>the same {@code portalViews} payload embedded on subTable rule nodes ({@code props.portalViews});</li>
 *   <li>sub-list-view linkForm columns' positive {@code componentId} / {@code linkFormColumnId} / {@code linkedFormId};</li>
 *   <li>{@code lookupConfig} JSON strings' {@code tableId} (rt_table_definitions id, remapped across environments;
 *       negative virtual ids such as sys_users are left untouched).</li>
 * </ul>
 */
public final class FormConfigJsonBindingIdRewriter {

    /** Matches the numeric {@code "bindingId": N} entry inside a lookupConfig JSON string. */
    private static final Pattern LOOKUP_CONFIG_BINDING_ID = Pattern.compile("(\"bindingId\"\\s*:\\s*)(-?\\d+)");

    /** Matches the numeric {@code "tableId": N} entry inside a lookupConfig JSON string. */
    private static final Pattern LOOKUP_CONFIG_TABLE_ID = Pattern.compile("(\"tableId\"\\s*:\\s*)(-?\\d+)");

    private static final String[] BINDING_KEYED_FIELDS = {
            "subForms",
            "subListViews",
            "relationViews",
            "subTablePortalViews"
    };

    private FormConfigJsonBindingIdRewriter() {
    }

    /** Legacy entry point (clone / form copy): binding ids only. */
    public static void remapBindingIds(Map<String, Object> configJson, Map<Long, Long> bindingIdMapping) {
        remapIds(configJson, bindingIdMapping, Map.of(), Map.of(), Map.of());
    }

    /**
     * Full import-time remap.
     *
     * @param bindingIdMapping        old FormTableBinding.id → new id
     * @param formIdMapping           old FormDefinition.id → new id
     * @param componentIdMapping      old dw_link_form_components.id → new id (positive ids)
     * @param relationTableIdMapping  old rt_table_definitions.id → new id
     */
    public static void remapIds(Map<String, Object> configJson,
                                Map<Long, Long> bindingIdMapping,
                                Map<Long, Long> formIdMapping,
                                Map<Long, Long> componentIdMapping,
                                Map<Long, Long> relationTableIdMapping) {
        if (configJson == null) {
            return;
        }
        RemapContext ctx = new RemapContext(
                orEmpty(bindingIdMapping), orEmpty(formIdMapping),
                orEmpty(componentIdMapping), orEmpty(relationTableIdMapping));
        if (ctx.isEmpty()) {
            return;
        }
        for (String fieldName : BINDING_KEYED_FIELDS) {
            remapMapKeys(configJson, fieldName, ctx.bindingIds);
        }
        remapRuleBindingIds(configJson.get("rule"), ctx);
        remapSubFormRules(configJson.get("subForms"), ctx);
        remapSubListViewColumnBindingRefs(configJson.get("subListViews"), ctx);
        remapPortalViewsValues(configJson.get("subTablePortalViews"), ctx);
    }

    /** Carries all id mappings through the rewrite walk. */
    private record RemapContext(Map<Long, Long> bindingIds,
                                Map<Long, Long> formIds,
                                Map<Long, Long> componentIds,
                                Map<Long, Long> relationTableIds) {
        boolean isEmpty() {
            return bindingIds.isEmpty() && formIds.isEmpty()
                    && componentIds.isEmpty() && relationTableIds.isEmpty();
        }
    }

    private static Map<Long, Long> orEmpty(Map<Long, Long> m) {
        return m != null ? m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static void remapSubFormRules(Object subFormsObj, RemapContext ctx) {
        if (!(subFormsObj instanceof Map<?, ?> subForms)) {
            return;
        }
        for (Object entryVal : subForms.values()) {
            if (entryVal instanceof Map<?, ?> subFormMap) {
                remapRuleBindingIds(subFormMap.get("rule"), ctx);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void remapSubListViewColumnBindingRefs(Object subListViewsObj, RemapContext ctx) {
        if (!(subListViewsObj instanceof Map<?, ?> subListViews)) {
            return;
        }
        for (Object entryVal : subListViews.values()) {
            if (!(entryVal instanceof Map<?, ?> entry)) {
                continue;
            }
            Object columns = entry.get("columns");
            if (!(columns instanceof List<?> colList)) {
                continue;
            }
            for (Object col : colList) {
                if (!(col instanceof Map<?, ?> colMapRaw)) {
                    continue;
                }
                Map<String, Object> colMap = (Map<String, Object>) colMapRaw;
                remapBindingIdField(colMap, "boundSubTableBindingId", ctx.bindingIds);
                remapLinkFormComponentRef(colMap, "componentId", ctx);
                remapLinkFormComponentRef(colMap, "linkFormColumnId", ctx);
                remapBindingIdField(colMap, "linkedFormId", ctx.formIds);
                remapLookupConfig(colMap, ctx);
            }
        }
    }

    /**
     * Remaps the {@code assigneeTodoFormSource} references inside each {@code subTablePortalViews} value.
     * These reference other entities by id (form / link-form column) and previously survived import verbatim,
     * leaving the portal display config pointing at the SOURCE environment's ids.
     */
    @SuppressWarnings("unchecked")
    private static void remapPortalViewsValues(Object portalViewsObj, RemapContext ctx) {
        if (!(portalViewsObj instanceof Map<?, ?> portalViews)) {
            return;
        }
        for (Object entryVal : portalViews.values()) {
            if (entryVal instanceof Map<?, ?> entry) {
                remapPortalViewsPayload((Map<String, Object>) entry, ctx);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void remapPortalViewsPayload(Map<String, Object> portalViews, RemapContext ctx) {
        Object formSource = portalViews.get("assigneeTodoFormSource");
        if (!(formSource instanceof Map<?, ?> formSourceRaw)) {
            return;
        }
        Map<String, Object> formSourceMap = (Map<String, Object>) formSourceRaw;
        remapBindingIdField(formSourceMap, "formId", ctx.formIds);
        remapLinkFormComponentRef(formSourceMap, "linkFormColumnId", ctx);
    }

    @SuppressWarnings("unchecked")
    private static void remapRuleBindingIds(Object ruleObj, RemapContext ctx) {
        if (!(ruleObj instanceof List<?> rules)) {
            return;
        }
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> nodeRaw)) {
                continue;
            }
            Map<String, Object> node = (Map<String, Object>) nodeRaw;
            if ("subTable".equals(node.get("type"))) {
                remapBindingIdField(node, "_bindingId", ctx.bindingIds);
                Object props = node.get("props");
                if (props instanceof Map<?, ?> propsRaw) {
                    Map<String, Object> propsMap = (Map<String, Object>) propsRaw;
                    remapBindingIdField(propsMap, "_bindingId", ctx.bindingIds);
                    remapBindingIdField(propsMap, "bindingId", ctx.bindingIds);
                    Object portalViews = propsMap.get("portalViews");
                    if (portalViews instanceof Map<?, ?> pvRaw) {
                        remapPortalViewsPayload((Map<String, Object>) pvRaw, ctx);
                    }
                }
            }
            // Lookup fields keep their binding in props.lookupConfig (JSON string), any node type.
            Object lookupProps = node.get("props");
            if (lookupProps instanceof Map<?, ?> lookupPropsRaw) {
                remapLookupConfig((Map<String, Object>) lookupPropsRaw, ctx);
            }
            Object children = node.get("children");
            if (children instanceof List<?>) {
                remapRuleBindingIds(children, ctx);
            }
        }
    }

    private static void remapBindingIdField(Map<String, Object> container, String fieldName,
                                            Map<Long, Long> idMapping) {
        Object raw = container.get(fieldName);
        Long remapped = remapIdValue(raw, idMapping);
        if (remapped != null && !remapped.equals(asLong(raw))) {
            container.put(fieldName, remapped);
        }
    }

    /**
     * Remaps {@code bindingId} and {@code tableId} inside a {@code lookupConfig} JSON string (e.g. on a
     * {@code lookup} rule node or a sub-list-view column). {@code tableId} is a rt_table_definitions id;
     * negative (virtual) ids like sys_users stay untouched. Leaves the rest of the JSON as-is.
     */
    private static void remapLookupConfig(Map<String, Object> container, RemapContext ctx) {
        Object raw = container.get("lookupConfig");
        if (!(raw instanceof String json) || json.isBlank()) {
            return;
        }
        String rewritten = rewriteJsonNumberField(json, LOOKUP_CONFIG_BINDING_ID, ctx.bindingIds, false);
        rewritten = rewriteJsonNumberField(rewritten, LOOKUP_CONFIG_TABLE_ID, ctx.relationTableIds, true);
        if (!rewritten.equals(json)) {
            container.put("lookupConfig", rewritten);
        }
    }

    /** Rewrites one numeric field in a JSON string via regex; optionally skips negative (virtual) ids. */
    private static String rewriteJsonNumberField(String json, Pattern pattern,
                                                 Map<Long, Long> mapping, boolean positiveOnly) {
        if (mapping.isEmpty()) {
            return json;
        }
        Matcher matcher = pattern.matcher(json);
        StringBuilder rewritten = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            long oldId = Long.parseLong(matcher.group(2));
            Long newId = (positiveOnly && oldId < 0) ? null : mapping.get(oldId);
            String replacement = (newId != null && newId != oldId)
                    ? matcher.group(1) + newId
                    : matcher.group(0);
            if (newId != null && newId != oldId) {
                changed = true;
            }
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return changed ? rewritten.toString() : json;
    }

    /**
     * Remaps a link-form column reference: negative values are virtual per-binding columns
     * ({@code -bindingId}), positive values are persisted dw_link_form_components ids.
     */
    private static void remapLinkFormComponentRef(Map<String, Object> container, String fieldName,
                                                  RemapContext ctx) {
        Object raw = container.get(fieldName);
        if (!(raw instanceof Number number)) {
            return;
        }
        long value = number.longValue();
        if (value < 0) {
            Long newBindingId = ctx.bindingIds.get(Math.abs(value));
            if (newBindingId != null) {
                container.put(fieldName, -newBindingId);
            }
        } else {
            Long newComponentId = ctx.componentIds.get(value);
            if (newComponentId != null) {
                container.put(fieldName, newComponentId);
            }
        }
    }

    private static Long remapIdValue(Object raw, Map<Long, Long> idMapping) {
        Long oldId = asLong(raw);
        if (oldId == null) {
            return null;
        }
        return idMapping.getOrDefault(oldId, oldId);
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void remapMapKeys(Map<String, Object> configJson, String fieldName,
                                     Map<Long, Long> bindingIdMapping) {
        if (bindingIdMapping.isEmpty()) {
            return;
        }
        Object fieldValue = configJson.get(fieldName);
        if (!(fieldValue instanceof Map<?, ?> oldMapRaw)) {
            return;
        }
        Map<String, Object> oldMap = (Map<String, Object>) oldMapRaw;
        Map<String, Object> newMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
            try {
                Long oldId = Long.parseLong(entry.getKey());
                Long newId = bindingIdMapping.get(oldId);
                if (newId != null) {
                    newMap.put(String.valueOf(newId), entry.getValue());
                } else {
                    newMap.put(entry.getKey(), entry.getValue());
                }
            } catch (NumberFormatException e) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        configJson.put(fieldName, newMap);
    }
}
