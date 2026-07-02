package com.developer.util;

import com.developer.entity.FormTableBinding;
import com.developer.enums.BindingType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs binding-keyed configJson maps (subForms, subListViews, …) when keys still
 * reference stale bindingIds from export/seed while {@link FormTableBinding#getId()} values
 * were reassigned on import or init-script insert.
 */
public final class FormConfigJsonOrphanBindingRepair {

    private static final String[] SUB_BINDING_FIELDS = {
            "subForms",
            "subListViews",
            "subTablePortalViews",
    };

    private static final String RELATED_BINDING_FIELD = "relationViews";

    private FormConfigJsonOrphanBindingRepair() {
    }

    public static boolean repairOrphanedBindingKeys(
            Map<String, Object> configJson,
            List<FormTableBinding> bindings) {
        if (configJson == null || bindings == null || bindings.isEmpty()) {
            return false;
        }
        Set<Long> currentIds = new LinkedHashSet<>();
        for (FormTableBinding binding : bindings) {
            if (binding.getId() != null) {
                currentIds.add(binding.getId());
            }
        }
        if (currentIds.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (String fieldName : SUB_BINDING_FIELDS) {
            changed |= repairMapField(configJson, fieldName, bindings, currentIds, BindingType.SUB);
        }
        changed |= repairMapField(configJson, RELATED_BINDING_FIELD, bindings, currentIds, BindingType.RELATED);
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean repairMapField(
            Map<String, Object> configJson,
            String fieldName,
            List<FormTableBinding> bindings,
            Set<Long> currentIds,
            BindingType bindingType) {
        Object raw = configJson.get(fieldName);
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return false;
        }
        Map<String, Object> keyedMap = new LinkedHashMap<>((Map<String, Object>) rawMap);
        List<String> orphanKeys = orphanKeys(keyedMap, currentIds);
        if (orphanKeys.isEmpty()) {
            return false;
        }

        List<FormTableBinding> typedBindings = bindings.stream()
                .filter(b -> b.getBindingType() == bindingType)
                .sorted(BINDING_ORDER)
                .toList();
        if (typedBindings.isEmpty() || orphanKeys.size() != typedBindings.size()) {
            return false;
        }

        boolean changed = false;
        List<String> remappedOrphans = new ArrayList<>();
        for (int i = 0; i < typedBindings.size(); i++) {
            FormTableBinding binding = typedBindings.get(i);
            String orphanKey = orphanKeys.get(i);
            String newKey = String.valueOf(binding.getId());
            if (entryIsUsable(keyedMap.get(newKey), fieldName)) {
                continue;
            }
            Object orphanValue = keyedMap.get(orphanKey);
            if (!entryIsUsable(orphanValue, fieldName)) {
                continue;
            }
            keyedMap.put(newKey, orphanValue);
            remappedOrphans.add(orphanKey);
            changed = true;
        }

        for (String orphanKey : remappedOrphans) {
            keyedMap.remove(orphanKey);
        }

        if (changed) {
            configJson.put(fieldName, keyedMap);
        }
        return changed;
    }

    private static List<String> orphanKeys(Map<String, Object> keyedMap, Set<Long> currentIds) {
        List<String> orphans = new ArrayList<>();
        for (String key : keyedMap.keySet()) {
            Long parsed = parseLong(key);
            if (parsed == null || !currentIds.contains(parsed)) {
                orphans.add(key);
            }
        }
        orphans.sort(Comparator.comparingLong(key -> {
            Long parsed = parseLong(key);
            return parsed != null ? parsed : Long.MAX_VALUE;
        }));
        return orphans;
    }

    private static boolean entryIsUsable(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> entry)) {
            return value != null;
        }
        return switch (fieldName) {
            case "subForms" -> hasNonEmptyList(entry.get("rule"));
            case "subListViews" -> hasNonEmptyList(entry.get("columns"));
            case "relationViews" -> hasNonEmptyList(entry.get("viewFields"));
            case "subTablePortalViews" -> !entry.isEmpty();
            default -> !entry.isEmpty();
        };
    }

    private static boolean hasNonEmptyList(Object raw) {
        return raw instanceof List<?> list && !list.isEmpty();
    }

    private static Long parseLong(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(key.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final Comparator<FormTableBinding> BINDING_ORDER = Comparator
            .comparing(FormTableBinding::getSortOrder, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(FormTableBinding::getId, Comparator.nullsLast(Long::compareTo));
}
