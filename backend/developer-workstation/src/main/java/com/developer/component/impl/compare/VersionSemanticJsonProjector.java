package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.developer.component.impl.compare.VersionSemanticDiffSupport.DesignObject;

/** Names design objects inside the already-normalized, credential-free JSON modules. */
final class VersionSemanticJsonProjector {

    Map<String, DesignObject> project(String module, VersionSnapshotNormalizer.Normalized snapshot) {
        Map<String, DesignObject> result = new LinkedHashMap<>();
        JsonNode tree = snapshot.modules().get(module);
        switch (module) {
            case VersionSnapshotNormalizer.BASIC -> put(result, "function-unit", "FUNCTION_UNIT",
                    "Function Unit", tree);
            case VersionSnapshotNormalizer.TABLES -> tables(result, tree);
            case VersionSnapshotNormalizer.FORMS -> forms(result, tree);
            case VersionSnapshotNormalizer.VIEWS -> views(result, tree);
            case VersionSnapshotNormalizer.ACTIONS -> tree.path("actions").properties().forEach(entry ->
                    put(result, "action/" + entry.getKey(), "ACTION",
                            entry.getValue().path("actionName").asText(entry.getKey()), entry.getValue()));
            case VersionSnapshotNormalizer.CONNECTIONS -> collect(result, tree.path("connections"),
                    "connection", "CONNECTION");
            case VersionSnapshotNormalizer.EMAIL_TEMPLATES -> collectEmails(result,
                    tree.path("emailTemplates"), "template", "EMAIL_TEMPLATE");
            case VersionSnapshotNormalizer.EMAIL_MONITORS -> collectEmails(result,
                    tree.path("emailMonitors"), "monitor", "EMAIL_MONITOR");
            case VersionSnapshotNormalizer.DOCUMENTS -> collect(result, tree, "document", "DOCUMENT");
            default -> throw new IllegalArgumentException("Unsupported JSON module: " + module);
        }
        return result;
    }

    private void tables(Map<String, DesignObject> result, JsonNode tree) {
        tableGroup(result, tree.path("tables"), "table", "TABLE");
        tableGroup(result, tree.path("relationTables"), "relation-table", "RELATION_TABLE");
        collect(result, tree.path("tableRelations"), "relation", "TABLE_RELATION");
    }

    private void tableGroup(Map<String, DesignObject> result, JsonNode tables,
                            String prefix, String type) {
        fieldsOf(result, tables, prefix, "fields", "FIELD");
        fieldsOf(result, tables, prefix, "foreignKeys", "FOREIGN_KEY");
        collectWithout(result, tables, prefix, type, "fields", "foreignKeys");
    }

    private void forms(Map<String, DesignObject> result, JsonNode tree) {
        JsonNode forms = tree.path("forms");
        forms.properties().forEach(form -> {
            String parent = "form/" + form.getKey();
            JsonNode config = form.getValue().path("configJson");
            ObjectNode formProperties = without(form.getValue(), "tableBindings", "stageBindings", "configJson");
            if (config.isObject()) {
                ObjectNode configProperties = without(config, "rule", "subForms");
                ObjectNode subFormProperties = JsonNodeFactory.instance.objectNode();
                JsonNode subForms = config.path("subForms");
                if (subForms.isObject()) {
                    subForms.properties().forEach(subForm -> {
                        ObjectNode properties = without(subForm.getValue(), "rule");
                        if (!properties.isEmpty()) subFormProperties.set(subForm.getKey(), properties);
                        controls(result, parent + "/subForm/" + subForm.getKey(),
                                subForm.getValue().path("rule"));
                    });
                }
                if (!subFormProperties.isEmpty()) configProperties.set("subForms", subFormProperties);
                formProperties.set("config", configProperties);
            }
            put(result, parent, "FORM", form.getValue().path("formName").asText(form.getKey()), formProperties);
            collect(result, form.getValue().path("tableBindings"), parent + "/binding", "FORM_BINDING");
            collect(result, form.getValue().path("stageBindings"), parent + "/stage", "STAGE_BINDING");
            JsonNode rules = config.isArray() ? config : config.path("rule");
            controls(result, parent, rules);
        });
        collect(result, tree.path("linkFormComponents"), "link-form", "LINK_FORM_COMPONENT");
    }

    private void controls(Map<String, DesignObject> result, String parent, JsonNode rules) {
        if (!rules.isArray()) return;
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (int index = 0; index < rules.size(); index++) {
            JsonNode rule = rules.get(index);
            if (!rule.isObject()) continue;
            String field = firstNonBlank(rule, "field", "name");
            String identity = rule.path(VersionFormControlMatcher.KEY).asText(field.isBlank() ? "position-" + index : field);
            int occurrence = occurrences.merge(identity, 1, Integer::sum);
            if (occurrence > 1) identity += "/occurrence-" + occurrence;
            String key = parent + "/control/" + identity;
            String title = firstNonBlank(rule, "title", "field", "type");
            ObjectNode properties = without(rule, "children", "rule", "_fc_id", VersionFormControlMatcher.KEY);
            properties.put("position", index + 1);
            put(result, key, "FORM_CONTROL", title.isBlank() ? field : title, properties);
            controls(result, key + "/children", rule.path("children"));
            controls(result, key + "/rule", rule.path("rule"));
        }
    }

    private void views(Map<String, DesignObject> result, JsonNode tree) {
        JsonNode views = tree.path("mainTableViews");
        fieldsOf(result, views, "view", "fields", "VIEW_FIELD");
        fieldsOf(result, views, "view", "accessRules", "VIEW_ACCESS");
        // Publication status is lifecycle metadata, not a change to the View's design. Preserve
        // it in snapshots for restore, but do not count a DRAFT -> PUBLISHED transition as a
        // semantic View modification (including when comparing older pre-publish snapshots).
        views.properties().forEach(entry -> put(result, "view/" + entry.getKey(), "VIEW",
                entry.getValue().path("mainTableName").asText() + "/"
                        + entry.getValue().path("viewName").asText(),
                without(entry.getValue(), "fields", "accessRules", "status")));
    }

    private void fieldsOf(Map<String, DesignObject> result, JsonNode parents,
            String prefix, String childField, String childType) {
        if (!parents.isObject()) return;
        parents.properties().forEach(parent -> collect(result, parent.getValue().path(childField),
                prefix + "/" + parent.getKey() + "/" + childField, childType));
    }

    private void collectWithout(Map<String, DesignObject> result, JsonNode collection,
            String prefix, String type, String... excluded) {
        if (!collection.isObject()) return;
        collection.properties().forEach(entry -> put(result, prefix + "/" + entry.getKey(), type,
                entry.getKey(), without(entry.getValue(), excluded)));
    }

    private void collect(Map<String, DesignObject> result, JsonNode collection,
            String prefix, String type) {
        if (!collection.isObject()) return;
        collection.properties().forEach(entry -> put(result, prefix + "/" + entry.getKey(), type,
                entry.getKey(), entry.getValue()));
    }

    private void collectEmails(Map<String, DesignObject> result, JsonNode collection, String prefix, String type) {
        collection.properties().forEach(entry -> put(result, prefix + "/" + entry.getKey(), type,
                entry.getValue().path("name").asText(), entry.getValue()));
    }

    private void put(Map<String, DesignObject> result, String key, String type,
                     String label, JsonNode properties) {
        if (result.putIfAbsent(key, new DesignObject(type, key, label, "DESIGN", properties)) != null) {
            throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                    "A saved version contains duplicate semantic identity: " + key);
        }
    }

    private ObjectNode without(JsonNode value, String... excluded) {
        ObjectNode copy = value.isObject() ? ((ObjectNode) value).deepCopy()
                : JsonNodeFactory.instance.objectNode();
        for (String key : excluded) copy.remove(key);
        return copy;
    }

    private String firstNonBlank(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) return value;
        }
        return "";
    }
}
