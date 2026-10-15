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
        Map<String, Map<String, Object>> assignmentContracts = assignmentContracts(catalogContent);
        Set<String> controlIds = new LinkedHashSet<>();
        int formIndex = 0;
        for (Object rawForm : forms) {
            if (!(rawForm instanceof Map<?, ?> rawFormMap)) {
                continue;
            }
            Map<String, Object> form = stringKeyMap(rawFormMap);
            String diagnosticId = form.get("sourceId") == null ? "index " + formIndex : form.get("sourceId").toString();
            Map<String, Object> config = parseConfig(form.get("data"), diagnosticId, objectMapper);
            com.platform.common.form.SubTableBatchDefinition.validate(config);
            Map<String, Object> batchForms = mapValue(config.get("batchForms"));
            List<Map<String, Object>> controls = new ArrayList<>();
            collectControls(config.get("rule"), batchForms, controls, controlIds, diagnosticId,
                    form, assignmentContracts, objectMapper);
            for (Map<String, Object> control : controls) {
                Map<String, Object> flags = mapValue(control.get("capabilities"));
                SubTableBatchFormRules.fields(mapValue(control.get("batchForm")));
                String bindingId = String.valueOf(control.get("bindingId"));
                boolean membership = Boolean.TRUE.equals(flags.get("allowBatchAdd")) || Boolean.TRUE.equals(flags.get("allowBatchDelete"));
                Map<String, Object> design = membership
                        ? SubTableBatchRowDesign.capture(form, bindingId, catalogContent, objectMapper)
                        : SubTableBatchRowDesign.captureFields(form, bindingId, catalogContent, objectMapper);
                SubTableBatchScopeDesign.validateFrozen(design, bindingId);
                if (Boolean.TRUE.equals(flags.get("allowBatchAdd"))) {
                    SubTableBatchRowDesign.validateAddForm(design, mapValue(control.get("batchForm")));
                }
                control.put("rowDesign", deepCopy(design, objectMapper));
            }
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
        if (variables == null || !variables.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)) {
            return Map.of();
        }
        if (formSourceId == null || objectMapper == null) throw invalidRuntimeSnapshot();
        Map<String, Object> snapshot = mapValue(variables.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD));
        if (!(snapshot.get("schemaVersion") instanceof Number version) || version.doubleValue() != SCHEMA_VERSION
                || !(snapshot.get("forms") instanceof Map<?, ?>)) throw invalidRuntimeSnapshot();
        Map<String, Object> forms = mapValue(snapshot.get("forms"));
        if (forms.isEmpty()) throw invalidRuntimeSnapshot();
        if (!forms.containsKey(String.valueOf(formSourceId))) return Map.of();
        Map<String, Object> form = mapValue(forms.get(String.valueOf(formSourceId)));
        if (!(form.get("controls") instanceof List<?> controls) || controls.isEmpty()) throw invalidRuntimeSnapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object control : controls) {
            if (!(control instanceof Map<?, ?> map)) throw invalidRuntimeSnapshot();
            Map<String, Object> safe = stringKeyMap(map);
            Object controlId = safe.get("batchControlId");
            if (!(controlId instanceof String id) || id.isBlank() || result.containsKey(id)) throw invalidRuntimeSnapshot();
            result.put(controlId.toString(), deepCopy(safe, objectMapper));
        }
        return result;
    }

    private static com.portal.exception.PortalException invalidRuntimeSnapshot() {
        return new com.portal.exception.PortalException("409", "Instance batch capability snapshot is invalid");
    }

    @SuppressWarnings("unchecked")
    private static void collectControls(Object value, Map<String, Object> batchForms,
                                        List<Map<String, Object>> controls, Set<String> controlIds,
                                        String sourceId, Map<String, Object> form,
                                        Map<String, Map<String, Object>> assignmentContracts,
                                        ObjectMapper objectMapper) {
        if (value instanceof List<?> list) {
            for (Object child : list) {
                collectControls(child, batchForms, controls, controlIds, sourceId, form, assignmentContracts, objectMapper);
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
                com.platform.common.form.SubTableBatchDefinition.validateForm(batchForm,
                        Boolean.TRUE.equals(props.get("allowBatchAssign")));

                Map<String, Object> control = new LinkedHashMap<>();
                control.put("batchControlId", controlId);
                control.put("bindingId", bindingId);
                control.put("capabilities", enabledCapabilities(props));
                control.put("originalCrud", originalCrud(props));
                control.put("batchForm", deepCopy(batchForm, objectMapper));
                if (Boolean.TRUE.equals(props.get("allowBatchAssign"))) {
                    Map<String, Object> assignment = assignmentForBinding(form, bindingId, assignmentContracts);
                    if (assignment.isEmpty()) {
                        throw new IllegalArgumentException("Batch Assign control " + controlId
                                + " has no matching BPMN assignment contract");
                    }
                    control.put("assignmentConfig", deepCopy(assignment, objectMapper));
                }
                controls.add(control);
            }
        }
        for (Object child : rule.values()) {
            if (child instanceof Map<?, ?> || child instanceof List<?>) {
                collectControls(child, batchForms, controls, controlIds, sourceId, form, assignmentContracts, objectMapper);
            }
        }
    }

    private static Map<String, Map<String, Object>> assignmentContracts(Map<String, Object> catalogContent) {
        Map<String, Map<String, Object>> contracts = new LinkedHashMap<>();
        Object rawProcesses = catalogContent.get("processes");
        if (!(rawProcesses instanceof List<?> processes)) return contracts;
        for (Object raw : processes) {
            if (!(raw instanceof Map<?, ?> process)) continue;
            Object data = process.get("data");
            if (!(data instanceof String bpmnXml) || bpmnXml.isBlank()) continue;
            BpmnMiXmlSupport.buildMiAssignmentsBySubTableName(bpmnXml).forEach((tableName, contract) -> {
                Map<String, Object> previous = contracts.putIfAbsent(tableName, contract);
                if (previous != null && !previous.equals(contract)) {
                    throw new IllegalArgumentException("Conflicting BPMN assignment contract for sub-table " + tableName);
                }
            });
        }
        return contracts;
    }

    private static Map<String, Object> assignmentForBinding(Map<String, Object> form, String bindingId,
                                                              Map<String, Map<String, Object>> contracts) {
        Object rawBindings = form.get("tableBindings");
        if (!(rawBindings instanceof List<?> bindings)) return Map.of();
        String tableName = null;
        for (Object rawBinding : bindings) {
            if (!(rawBinding instanceof Map<?, ?> binding)) continue;
            if (!bindingId.equals(String.valueOf(binding.get("bindingId")))) continue;
            Object value = binding.get("tableName");
            if (value instanceof String name && !name.isBlank()) {
                tableName = name.trim();
                break;
            }
        }
        if (tableName == null) return Map.of();
        final String resolvedTableName = tableName;
        Map<String, Object> direct = contracts.get(resolvedTableName);
        if (direct != null) return direct;
        return contracts.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(resolvedTableName))
                .map(Map.Entry::getValue).findFirst().orElse(Map.of());
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
