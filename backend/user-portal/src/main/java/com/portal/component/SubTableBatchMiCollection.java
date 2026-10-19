package com.portal.component;

import com.portal.exception.PortalException;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict collection construction only for frozen initial-assignment targets. No live metadata or scored slices. */
final class SubTableBatchMiCollection {
    private SubTableBatchMiCollection() { }
    private record Contract(String table, List<String> keys, Map<?, ?> assignment, SubTableBatchMiScope.Scope scope) { }

    static void validateTargets(Document document, Map<String, Object> controls) {
        if (controls == null) throw invalid("Frozen assignment controls are missing");
        for (Map<?, ?> control : assignmentControls(controls)) {
            String table = text(map(map(control.get("rowDesign")).get("binding")).get("tableName"));
            Map<?, ?> config = map(control.get("assignmentConfig"));
            Element task = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "userTask", text(config.get("taskDefinitionKey")));
            if (task == null || nearestMiParent(task) == null || !table.equals(BpmnMiXmlSupport.findFirstPropertyValue(task, "subTableName"))
                    || !config.equals(BpmnMiXmlSupport.assignmentContract(task))) {
                throw invalid("Frozen assignment does not match deployed BPMN");
            }
        }
    }

    static boolean inject(Element subProcess, Map<String, Object> variables, Map<String, Object> controls,
                          Map<String, Object> baseline, java.util.function.BiConsumer<Set<String>, Set<SubTableBatchRoleEligibility.Target>> validateTargets) {
        List<Map<?, ?>> enabled = assignmentControls(controls);
        if (enabled.isEmpty()) return false; // Explicit non-assignment path, not recovery from bad configuration.
        Contract selected = null;
        for (Map<?, ?> control : enabled) {
            Map<?, ?> binding = map(map(control.get("rowDesign")).get("binding"));
            Map<?, ?> config = map(control.get("assignmentConfig"));
            String table = text(binding.get("tableName"));
            String target = text(config.get("taskDefinitionKey"));
            Element task = BpmnMiXmlSupport.findElementByLocalNameAndId(subProcess.getOwnerDocument(), "userTask", target);
            boolean within = task != null && isWithin(task, subProcess);
            if (!within) continue; // The whole current-form target set was already verified before traversal.
            if (nearestMiParent(task) != subProcess) throw invalid("Nested assignment target requires a separate collection scope");
            if (!config.equals(BpmnMiXmlSupport.assignmentContract(task))) throw invalid("Frozen assignment does not match deployed BPMN");
            List<String> keys = strings(binding.get("primaryKeyFields"));
            Contract candidate = new Contract(table, keys, config, SubTableBatchMiScope.resolve(control, baseline));
            validateMapping(candidate);
            if (selected != null && !selected.equals(candidate)) throw invalid("Frozen MI source is ambiguous");
            selected = candidate;
        }
        if (selected == null) return false; // This reachable MI target does not enable Batch Assign.
        String collection = BpmnMiXmlSupport.extractFlowableCollection(BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(subProcess));
        if (collection == null || collection.isBlank()) throw invalid("Deployed assignment collection is missing");
        Object source = map(variables.get("__subTables__")).get("dw:" + selected.table().toLowerCase(Locale.ROOT));
        if (!(source instanceof List<?> rows)) throw invalid("Frozen assignment canonical store is missing");
        List<Map<String, Object>> items = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        for (Object raw : rows) {
            Map<?, ?> row = map(raw);
            if (selected.scope().includes(row)) items.add(item(row, selected, seen));
        }
        Set<String> people = new HashSet<>();
        Set<SubTableBatchRoleEligibility.Target> roles = new HashSet<>();
        Object personField = selected.assignment().get("assigneeField");
        Object roleField = selected.assignment().get("roleField");
        Object buField = selected.assignment().get("buField");
        for (Map<String, Object> item : items) {
            if (personField != null && item.get(personField) instanceof String person) people.add(person);
            if (roleField != null && item.get(roleField) instanceof String role) {
                roles.add(new SubTableBatchRoleEligibility.Target(buField == null ? null : text(item.get(buField)), role));
            }
        }
        validateTargets.accept(people, roles);
        variables.put(collection, items); // Publish only after every row has passed.
        return true;
    }

    private static void validateMapping(Contract contract) {
        Map<?, ?> config = contract.assignment();
        if (!(config.get("allowUser") instanceof Boolean user) || !(config.get("allowRole") instanceof Boolean role)
                || (!user && !role)) throw invalid("Frozen assignment modes are missing");
        Set<String> fields = new HashSet<>();
        for (String property : List.of("assigneeField", "roleField", "buField")) {
            Object raw = config.get(property);
            boolean required = (user && "assigneeField".equals(property)) || (role && "roleField".equals(property));
            if (raw == null && !required) continue;
            String field = text(raw);
            if (!fields.add(field) || contract.keys().contains(field)) throw invalid("Frozen assignment fields overlap identity or each other");
        }
    }

    private static Map<String, Object> item(Map<?, ?> row, Contract contract, Set<List<String>> seen) {
        Map<String, Object> key = new LinkedHashMap<>();
        for (String field : contract.keys()) {
            Object value = row.get(field);
            if (!(value instanceof String || value instanceof Number) || String.valueOf(value).isBlank()) throw invalid("Assignment row identity is missing");
            key.put(field, value);
        }
        if (!seen.add(key.values().stream().map(String::valueOf).toList())) throw invalid("Assignment row identity is duplicated");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowKey", key);
        if (key.size() == 1) result.put("rowId", key.values().iterator().next());
        Map<?, ?> config = contract.assignment();
        String person = target(row, config.get("assigneeField"));
        String role = target(row, config.get("roleField"));
        String bu = target(row, config.get("buField"));
        if ((person == null) == (role == null)) throw invalid("Assignment row must have exactly one target mode");
        if (person != null) {
            if (!Boolean.TRUE.equals(config.get("allowUser")) || bu != null) throw invalid("Assignment person mode is not allowed or retains role context");
            result.put(text(config.get("assigneeField")), person);
        } else {
            if (!Boolean.TRUE.equals(config.get("allowRole")) || (config.get("buField") != null && bu == null)) {
                throw invalid("Assignment role mode requires its configured BU");
            }
            result.put(text(config.get("roleField")), role);
            if (config.get("buField") != null) result.put(text(config.get("buField")), bu);
        }
        return result;
    }

    private static String target(Map<?, ?> row, Object field) {
        if (field == null) return null;
        Object value = row.get(text(field));
        if (value == null || "".equals(value)) return null;
        if (!(value instanceof String target) || target.isBlank() || !target.equals(target.trim())) throw invalid("Assignment target is not a valid scalar identifier");
        return target;
    }
    private static boolean isWithin(Element task, Element parent) {
        for (var node = task.getParentNode(); node != null; node = node.getParentNode()) if (node == parent) return true;
        return false;
    }
    private static Element nearestMiParent(Element task) {
        for (var node = task.getParentNode(); node instanceof Element element; node = node.getParentNode()) {
            if ("subProcess".equals(element.getLocalName()) && BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(element) != null) return element;
        }
        return null;
    }
    private static List<Map<?, ?>> assignmentControls(Map<String, Object> controls) {
        return controls.values().stream().map(SubTableBatchMiCollection::map)
                .filter(control -> Boolean.TRUE.equals(map(control.get("capabilities")).get("allowBatchAssign"))).toList();
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) throw invalid("Frozen assignment primary keys are missing");
        List<String> fields = list.stream().map(SubTableBatchMiCollection::text).toList();
        if (new HashSet<>(fields).size() != fields.size()) throw invalid("Frozen assignment primary keys are duplicated");
        return fields;
    }
    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw invalid("Frozen assignment metadata is missing");
        return text;
    }
    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw invalid("Frozen assignment context is missing");
        return map;
    }
    private static PortalException invalid(String message) { return new PortalException("409", message); }
}
