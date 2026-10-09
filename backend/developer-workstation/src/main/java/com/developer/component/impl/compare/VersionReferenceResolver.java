package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;

/** Replaces snapshot-local database references with names before generated IDs are discarded. */
final class VersionReferenceResolver {

    private final ObjectMapper objectMapper;

    VersionReferenceResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode resolve(JsonNode snapshot) {
        return resolve(snapshot, null);
    }

    JsonNode resolve(JsonNode snapshot, JsonNode paired) {
        References refs = new References(snapshot);
        if (paired != null) refs.collectPairedBindings(snapshot, paired);
        ObjectNode result = (ObjectNode) new VersionActionSnapshotProjector(objectMapper).project(replace(snapshot, refs));
        result.put("_formComparePartial", refs.partial);
        return result;
    }

    static String bindingReference(String formName, JsonNode binding) {
        return formName + "/" + binding.path("bindingType").asText() + "/"
                + binding.path("tableName").asText() + "/" + binding.path("filterFkFieldName").asText("");
    }

    private JsonNode replace(JsonNode value, References refs) {
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> result.add(replace(item, refs)));
            return result;
        }
        if (!value.isObject()) return value.deepCopy();
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        value.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode item = parseConfigJson(key, entry.getValue());
            if ("subForms".equals(key) && item.isObject()) {
                ObjectNode subForms = JsonNodeFactory.instance.objectNode();
                item.properties().forEach(subForm -> {
                    String binding = refs.subFormBinding(subForm.getKey());
                    if (subForms.has(binding)) {
                        throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                                "A saved version contains duplicate sub-form binding identity: " + binding);
                    }
                    subForms.set(binding, replace(subForm.getValue(), refs));
                });
                result.set(key, subForms);
                return;
            }
            String stableKey = replacementKey(key, value);
            if (stableKey != null && !item.isNull()) {
                result.put(stableKey, refs.valueFor(key, item.asText()));
            } else {
                result.set(key, replace(item, refs));
            }
        });
        return result;
    }

    private JsonNode parseConfigJson(String key, JsonNode value) {
        if (!"configJson".equals(key) || !value.isTextual()) return value;
        if (value.asText().isBlank()) return JsonNodeFactory.instance.nullNode();
        try {
            JsonNode parsed = objectMapper.readTree(value.asText());
            if (parsed == null) throw new IllegalArgumentException("Empty configJson");
            return parsed;
        } catch (Exception e) {
            throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_CONFIG_INVALID",
                    "A saved version contains invalid configJson JSON");
        }
    }

    private String replacementKey(String key, JsonNode parent) {
        return switch (key) {
            case "targetFormId" -> "targetFormName";
            case "targetBindingId" -> "targetBinding";
            case "sourceRuleId" -> "sourceRule";
            case "emailTemplateId" -> "emailTemplateName";
            case "connectionUid" -> parent.has("connectionType") ? null : "connectionName";
            case "bindingId", "_bindingId" -> parent.has("bindingType") ? null : "bindingRef";
            case "formId" -> parent.has("formName") ? null : "formRef";
            case "linkedFormId" -> parent.hasNonNull("linkedFormName") ? null : "linkedFormRef";
            case "relationTableId" -> parent.has("tableName") && parent.has("fields")
                    ? null : "relationTableName";
            case "targetId" -> parent.hasNonNull("targetCode") ? null : "legacyTargetId";
            default -> null;
        };
    }

    private static final class References {
        private final Map<String, String> forms = new HashMap<>();
        private final Map<String, String> bindings = new HashMap<>();
        private final Map<String, String> subFormBindings = new HashMap<>();
        private final Map<String, String> connections = new HashMap<>();
        private final Map<String, String> rules = new HashMap<>();
        private final Map<String, String> templates = new HashMap<>();
        private final Map<String, String> relationTables = new HashMap<>();
        private final Map<String, String> pairedBindings = new HashMap<>();
        private final Map<String, String> pairedSubForms = new HashMap<>();
        private boolean partial;

        private References(JsonNode snapshot) {
            collect(relationTables, snapshot.path("relationTables"), "relationTableId", "tableName");
            JsonNode formList = snapshot.has("forms") ? snapshot.path("forms") : snapshot.path("formDefinitions");
            if (formList.isArray()) {
                for (JsonNode form : formList) {
                    put(forms, form, "formId", "formName");
                    JsonNode bindingList = form.path("tableBindings");
                    if (bindingList.isArray()) {
                        for (JsonNode binding : bindingList) {
                            String label = bindingReference(form.path("formName").asText(), binding);
                            if (binding.hasNonNull("bindingId")) {
                                String id = binding.get("bindingId").asText();
                                bindings.put(id, label);
                                subFormBindings.put(id, form.path("formName").asText() + "/"
                                        + stableBindingTarget(binding) + "/"
                                        + binding.path("bindingType").asText() + "/"
                                        + binding.path("filterFkFieldName").asText(""));
                            }
                        }
                    }
                }
            }
            collect(connections, snapshot.path("connections"), "connectionUid", "name");
            collect(rules, snapshot.path("emailMonitors"), "ruleId", "name");
            collect(templates, snapshot.path("emailTemplates"), "templateId", "name");
        }

        private String stableBindingTarget(JsonNode binding) {
            String tableName = binding.path("tableName").asText("");
            if (!tableName.isBlank()) return tableName;
            String relationTableName = binding.path("relationTableName").asText("");
            if (relationTableName.isBlank() && binding.hasNonNull("relationTableId")) {
                relationTableName = relationTables.getOrDefault(binding.get("relationTableId").asText(), "");
            }
            if (!relationTableName.isBlank() && !relationTableName.startsWith("unresolved:")) {
                return relationTableName;
            }
            return "related-position-" + binding.path("sortOrder").asText("");
        }

        private void collectPairedBindings(JsonNode snapshot, JsonNode paired) {
            JsonNode ownForms = snapshot.has("forms") ? snapshot.path("forms") : snapshot.path("formDefinitions");
            JsonNode otherForms = paired.has("forms") ? paired.path("forms") : paired.path("formDefinitions");
            Map<String, JsonNode> peers = new HashMap<>();
            otherForms.forEach(form -> {
                if (form.hasNonNull("formId")) peers.put(form.path("formId").asText(), form);
            });
            ownForms.forEach(form -> {
                JsonNode peer = peers.get(form.path("formId").asText());
                if (peer == null) return;
                // Only a persisted form in this pair's same FU may resolve a missing binding.
                // This is comparison evidence, not a repair of dangling saved configuration.
                peer.path("tableBindings").forEach(binding -> {
                    String id = binding.path("bindingId").asText("");
                    if (id.isBlank() || bindings.containsKey(id)) return;
                    String prefix = form.path("formName").asText() + "/";
                    pairedBindings.put(id, bindingReference(form.path("formName").asText(), binding));
                    pairedSubForms.put(id, prefix + stableBindingTarget(binding) + "/"
                            + binding.path("bindingType").asText() + "/" + binding.path("filterFkFieldName").asText(""));
                });
            });
        }

        private void collect(Map<String, String> target, JsonNode list, String id, String name) {
            if (!list.isArray()) return;
            for (JsonNode item : list) put(target, item, id, name);
        }

        private void put(Map<String, String> target, JsonNode item, String id, String name) {
            if (item.hasNonNull(id) && item.hasNonNull(name)) {
                target.put(item.get(id).asText(), item.get(name).asText());
            }
        }

        private String valueFor(String key, String id) {
            Map<String, String> dictionary = switch (key) {
                case "targetFormId", "formId", "linkedFormId" -> forms;
                case "targetBindingId", "bindingId", "_bindingId" -> bindings;
                case "connectionUid" -> connections;
                case "sourceRuleId" -> rules;
                case "emailTemplateId" -> templates;
                case "relationTableId" -> relationTables;
                default -> Map.of();
            };
            String value = dictionary.get(id);
            if (value != null) return value;
            if ("bindingId".equals(key) || "_bindingId".equals(key) || "targetBindingId".equals(key)) {
                partial = true;
                if (pairedBindings.containsKey(id)) return pairedBindings.get(id);
            }
            return "unresolved:" + id;
        }

        private String subFormBinding(String id) {
            if (subFormBindings.containsKey(id)) return subFormBindings.get(id);
            partial = true;
            return pairedSubForms.getOrDefault(id, id);
        }
    }
}
