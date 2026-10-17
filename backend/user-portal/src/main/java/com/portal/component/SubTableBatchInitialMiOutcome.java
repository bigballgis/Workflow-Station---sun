package com.portal.component;

import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Observes only initial-assignment collections already built by a strict source-bound preflight. */
public final class SubTableBatchInitialMiOutcome {
    private SubTableBatchInitialMiOutcome() { }
    record PersonalTarget(Map<String, Object> rowKey, String assignee) { }
    record RoleTarget(Map<String, Object> rowKey, String businessUnitCode, String roleCode, java.util.Set<String> members) { }
    record Expected(String node, boolean personalOnly, String table, List<Map<String, Object>> rowKeys,
                    List<PersonalTarget> people, List<RoleTarget> roles) { }
    /** Server-only plan returned across the completion transaction, never serialized as a response. */
    public record Plan(String instanceId, String definitionId, List<Expected> expected) { }

    static List<Expected> plan(Element target, Map<String, Object> controls, List<?> items,
                               Map<SubTableBatchRoleEligibility.Target, SubTableBatchRoleEligibility.Pool> pools) {
        List<Expected> expected = new ArrayList<>();
        for (Object raw : controls.values()) {
            Map<?, ?> control = (Map<?, ?>) raw;
            if (!Boolean.TRUE.equals(((Map<?, ?>) control.get("capabilities")).get("allowBatchAssign"))) continue;
            Map<?, ?> config = (Map<?, ?>) control.get("assignmentConfig");
            String node = (String) config.get("taskDefinitionKey");
            if (!containsTask(target, node) || items.isEmpty()) continue;
            Object personField = config.get("assigneeField");
            boolean personalOnly = personField != null && items.stream()
                    .allMatch(value -> ((Map<?, ?>) value).get(personField) instanceof String);
            String table = (String) ((Map<?, ?>) ((Map<?, ?>) control.get("rowDesign")).get("binding")).get("tableName");
            List<Map<String, Object>> keys = new ArrayList<>();
            List<PersonalTarget> people = new ArrayList<>();
            List<RoleTarget> roles = new ArrayList<>();
            for (Object rawItem : items) {
                var key = new java.util.LinkedHashMap<String, Object>();
                ((Map<?, ?>) ((Map<?, ?>) rawItem).get("rowKey")).forEach((name, value) -> key.put((String) name, value));
                keys.add(Map.copyOf(key));
                if (personField != null && ((Map<?, ?>) rawItem).get(personField) instanceof String person) {
                    people.add(new PersonalTarget(Map.copyOf(key), person));
                }
                if (config.get("roleField") != null && ((Map<?, ?>) rawItem).get(config.get("roleField")) instanceof String role) {
                    String bu = config.get("buField") == null ? null : (String) ((Map<?, ?>) rawItem).get(config.get("buField"));
                    var pool = pools.get(new SubTableBatchRoleEligibility.Target(bu, role));
                    if (pool == null || pool.members().isEmpty()) throw new com.portal.exception.PortalException("409", "Role observation plan is missing");
                    roles.add(new RoleTarget(Map.copyOf(key), pool.resolved().businessUnitCode(), pool.resolved().roleCode(), java.util.Set.copyOf(pool.members())));
                }
            }
            Expected candidate = new Expected(node, personalOnly, table, List.copyOf(keys), List.copyOf(people), List.copyOf(roles));
            if (!expected.contains(candidate)) expected.add(candidate);
        }
        return expected;
    }

    private static boolean containsTask(Element target, String id) {
        var tasks = target.getElementsByTagNameNS("*", "userTask");
        for (int index = 0; index < tasks.getLength(); index++) {
            if (id.equals(((Element) tasks.item(index)).getAttribute("id"))) return true;
        }
        return false;
    }

}
