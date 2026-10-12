package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the server-owned batch capability snapshot for a newly started process.
 *
 * <p>The input is the catalog-pinned Function Unit payload already used for this
 * start.  No live DW definition is consulted here.  Returning an empty optional
 * means the published catalog has no enabled batch control; callers must not add
 * the reserved runtime key in that case.</p>
 */
final class SubTableBatchRuntimeSnapshot {

    static final int SCHEMA_VERSION = 1;

    private static final Set<String> BATCH_FLAGS = Set.of(
            "allowBatchAdd", "allowBatchEdit", "allowBatchDelete", "allowBatchAssign");

    private SubTableBatchRuntimeSnapshot() {
    }

    static Optional<Map<String, Object>> capture(
            Map<String, Object> catalogContent,
            ProcessStartComponent.ActiveCatalogPin pin,
            ObjectMapper objectMapper) {
        if (catalogContent == null || pin == null) {
            throw new IllegalArgumentException("Catalog content and pin are required");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper is required");
        }

        Object rawForms = catalogContent.get("forms");
        if (!(rawForms instanceof List<?> forms)) {
            return Optional.empty();
        }

        Map<String, Object> snapshotForms = new LinkedHashMap<>();
        Set<String> controlIds = new LinkedHashSet<>();
        int formIndex = 0;
        for (Object rawForm : forms) {
            if (!(rawForm instanceof Map<?, ?> rawFormMap)) {
                continue;
            }
            Map<String, Object> form = stringKeyMap(rawFormMap);
            String diagnosticId = form.get("sourceId") == null ? "index " + formIndex : form.get("sourceId").toString();
            Map<String, Object> config = parseConfig(form.get("data"), diagnosticId, objectMapper);
            Map<String, Object> batchForms = mapValue(config.get("batchForms"));
            List<Map<String, Object>> controls = new ArrayList<>();
            collectControls(config.get("rule"), batchForms, controls, controlIds, diagnosticId, objectMapper);
            if (!controls.isEmpty()) {
                String sourceId = requiredText(form.get("sourceId"), "catalog form sourceId for enabled batch control");
                Map<String, Object> formSnapshot = new LinkedHashMap<>();
                formSnapshot.put("controls", controls);
                formSnapshot.put("formType", form.get("formType"));
                formSnapshot.put("scene", form.get("scene"));
                snapshotForms.put(sourceId, formSnapshot);
            }
            formIndex++;
        }
        if (snapshotForms.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SCHEMA_VERSION);
        snapshot.put("catalogId", pin.catalogId());
        snapshot.put("functionUnitCode", pin.code());
        snapshot.put("versionLabel", pin.versionLabel());
        snapshot.put("forms", snapshotForms);
        return Optional.of(snapshot);
    }

    /**
     * Returns only the frozen controls belonging to one rendered form.  The browser
     * never receives the instance-wide snapshot (or controls for other stages).
     */
    static Map<String, Object> projectForForm(Map<String, Object> variables, Object formSourceId,
                                              ObjectMapper objectMapper) {
        if (variables == null || formSourceId == null || objectMapper == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = mapValue(variables.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
        if (!(snapshot.get("schemaVersion") instanceof Number version) || version.intValue() != SCHEMA_VERSION) {
            return Map.of();
        }
        Map<String, Object> forms = mapValue(snapshot.get("forms"));
        Map<String, Object> form = mapValue(forms.get(String.valueOf(formSourceId)));
        if (form.isEmpty() || !(form.get("controls") instanceof List<?> controls)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object control : controls) {
            if (!(control instanceof Map<?, ?> map)) continue;
            Map<String, Object> safe = stringKeyMap(map);
            Object controlId = safe.get("batchControlId");
            if (controlId == null || controlId.toString().isBlank()) continue;
            result.put(controlId.toString(), deepCopy(safe, objectMapper));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collectControls(Object value, Map<String, Object> batchForms,
                                        List<Map<String, Object>> controls, Set<String> controlIds,
                                        String sourceId, ObjectMapper objectMapper) {
        if (value instanceof List<?> list) {
            for (Object child : list) {
                collectControls(child, batchForms, controls, controlIds, sourceId, objectMapper);
            }
            return;
        }
        if (!(value instanceof Map<?, ?> rawRule)) {
            return;
        }
        Map<String, Object> rule = stringKeyMap(rawRule);
        if ("subTable".equals(rule.get("type"))) {
            Map<String, Object> props = mapValue(rule.get("props"));
            if (hasEnabledOperation(props)) {
                String controlId = requiredText(rule.get("batchControlId"),
                        "batchControlId for enabled subTable in form " + sourceId);
                if (!controlIds.add(controlId)) {
                    throw new IllegalArgumentException("Duplicate batchControlId in published catalog: " + controlId);
                }
                String bindingId = requiredBindingId(props.get("_bindingId"), sourceId, controlId);
                Map<String, Object> batchForm = mapValue(batchForms.get(bindingId));
                validateBatchForm(batchForm, sourceId, controlId, bindingId);

                Map<String, Object> control = new LinkedHashMap<>();
                control.put("batchControlId", controlId);
                control.put("bindingId", bindingId);
                control.put("capabilities", enabledCapabilities(props));
                control.put("originalCrud", originalCrud(props));
                control.put("batchForm", deepCopy(batchForm, objectMapper));
                controls.add(control);
            }
        }
        for (Object child : rule.values()) {
            if (child instanceof Map<?, ?> || child instanceof List<?>) {
                collectControls(child, batchForms, controls, controlIds, sourceId, objectMapper);
            }
        }
    }

    private static void validateBatchForm(Map<String, Object> batchForm, String sourceId,
                                          String controlId, String bindingId) {
        if (batchForm.isEmpty()) {
            throw new IllegalArgumentException("Enabled batch control " + controlId
                    + " in form " + sourceId + " has no batchForms[" + bindingId + "] definition");
        }
        Object schemaVersion = batchForm.get("schemaVersion");
        if (!(schemaVersion instanceof Number number) || number.intValue() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported batch form schema for control " + controlId);
        }
        if (!(batchForm.get("rule") instanceof List<?> rules) || rules.isEmpty()) {
            throw new IllegalArgumentException("Batch form rule is required for control " + controlId);
        }
    }

    private static boolean hasEnabledOperation(Map<String, Object> props) {
        return BATCH_FLAGS.stream().anyMatch(flag -> Boolean.TRUE.equals(props.get(flag)));
    }

    private static Map<String, Object> enabledCapabilities(Map<String, Object> props) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String flag : BATCH_FLAGS) {
            result.put(flag, Boolean.TRUE.equals(props.get(flag)));
        }
        return result;
    }

    private static Map<String, Object> originalCrud(Map<String, Object> props) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowAdd", props.get("allowAdd"));
        result.put("allowEdit", props.get("allowEdit"));
        result.put("allowDelete", props.get("allowDelete"));
        return result;
    }

    private static String requiredBindingId(Object rawBindingId, String sourceId, String controlId) {
        if (rawBindingId instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return requiredText(rawBindingId, "_bindingId for control " + controlId + " in form " + sourceId);
    }

    private static String requiredText(Object value, String description) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + description);
        }
        return value.toString().trim();
    }

    private static Map<String, Object> parseConfig(Object rawData, String sourceId, ObjectMapper objectMapper) {
        if (!(rawData instanceof String data) || data.isBlank()) {
            throw new IllegalArgumentException("Missing configJson for catalog form " + sourceId);
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(data, new TypeReference<LinkedHashMap<String, Object>>() { });
            Object nestedConfig = parsed.get("configJson");
            return nestedConfig instanceof Map<?, ?> nested ? stringKeyMap(nested) : parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid configJson for catalog form " + sourceId, ex);
        }
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source, ObjectMapper objectMapper) {
        return objectMapper.convertValue(source, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? stringKeyMap(map) : Map.of();
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                result.put(key.toString(), value);
            }
        });
        return result;
    }
}
