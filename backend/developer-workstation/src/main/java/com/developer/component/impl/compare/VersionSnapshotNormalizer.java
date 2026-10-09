package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Read-only projection of saved snapshots into stable, comparable module trees. */
@Component
@RequiredArgsConstructor
public class VersionSnapshotNormalizer {

    public static final String BASIC = "BASIC";
    public static final String PROCESS = "PROCESS";
    public static final String TABLES = "TABLES";
    public static final String FORMS = "FORMS";
    public static final String VIEWS = "VIEWS";
    public static final String ACTIONS = "ACTIONS";
    public static final String AUTOMATION = "AUTOMATION";
    public static final String CONNECTIONS = "CONNECTIONS";
    public static final String EMAIL_TEMPLATES = "EMAIL_TEMPLATES";
    public static final String EMAIL_MONITORS = "EMAIL_MONITORS";
    public static final String DECISIONS = "DECISIONS";
    public static final String DOCUMENTS = "DOCUMENTS";

    private final ObjectMapper objectMapper;
    private final VersionXmlNormalizer xmlNormalizer = new VersionXmlNormalizer();

    /** The sanitized source is compare-internal only; it must never be serialized in the API. */
    public record Normalized(Map<String, JsonNode> modules, Set<String> notCaptured,
                             JsonNode sanitizedSource, boolean modern) {}

    public Normalized normalize(byte[] snapshotData) {
        return normalize(snapshotData, null);
    }

    Normalized normalize(byte[] snapshotData, byte[] pairedData) {
        JsonNode snapshot = readSnapshot(snapshotData, pairedData);
        boolean modern = snapshot.has("tables") || snapshot.has("snapshotSchemaVersion");
        requireCoreCollections(snapshot, modern);
        Set<String> notCaptured = new LinkedHashSet<>();
        Map<String, JsonNode> modules = new LinkedHashMap<>();
        modules.put(BASIC, basic(snapshot));
        modules.put(PROCESS, process(snapshot, modern));
        modules.put(TABLES, grouped(snapshot, modern, new String[][] {
                {modern ? "tables" : "tableDefinitions", "tables"},
                {"tableRelations", "tableRelations"}, {"relationTables", "relationTables"}}, notCaptured));
        modules.put(FORMS, grouped(snapshot, modern, new String[][] {
                {modern ? "forms" : "formDefinitions", "forms"},
                {"linkFormComponents", "linkFormComponents"}}, notCaptured));
        modules.put(VIEWS, grouped(snapshot, modern,
                new String[][] {{"mainTableViews", "mainTableViews"}}, notCaptured));
        modules.put(ACTIONS, grouped(snapshot, modern,
                new String[][] {{modern ? "actions" : "actionDefinitions", "actions"}}, notCaptured));
        modules.put(CONNECTIONS, grouped(snapshot, modern,
                new String[][] {{"connections", "connections"}}, notCaptured));
        modules.put(EMAIL_TEMPLATES, grouped(snapshot, modern,
                new String[][] {{"emailTemplates", "emailTemplates"}}, notCaptured));
        modules.put(EMAIL_MONITORS, grouped(snapshot, modern,
                new String[][] {{"emailMonitors", "emailMonitors"}}, notCaptured));
        modules.put(DECISIONS, decisions(snapshot, modern));
        modules.put(DOCUMENTS, documents(snapshot, notCaptured));
        if (!modern) {
            notCaptured.addAll(Set.of(VIEWS, CONNECTIONS, EMAIL_TEMPLATES, EMAIL_MONITORS));
        } else {
            if (!snapshot.has("emailTemplates")) notCaptured.add(EMAIL_TEMPLATES);
            if (!snapshot.has("emailMonitors")) notCaptured.add(EMAIL_MONITORS);
        }
        return new Normalized(modules, notCaptured, snapshot, modern);
    }

    private JsonNode readSnapshot(byte[] data, byte[] pairedData) {
        try {
            JsonNode parsed = objectMapper.readTree(data);
            if (parsed == null || !parsed.isObject()) {
                throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID",
                        "A saved version has no valid snapshot object");
            }
            JsonNode paired = pairedData == null ? null : objectMapper.readTree(pairedData);
            return SnapshotSensitiveDataFilter.redact(new VersionReferenceResolver(objectMapper).resolve(parsed, paired));
        } catch (IOException e) {
            throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID",
                    "A saved version has invalid snapshot JSON");
        }
    }

    private void requireCoreCollections(JsonNode snapshot, boolean modern) {
        if (!modern) return;
        for (String key : new String[] {"tables", "forms", "actions", "decisions"}) {
            if (!snapshot.path(key).isArray()) throw invalidShape(key);
        }
    }

    private JsonNode basic(JsonNode snapshot) {
        ObjectNode basic = JsonNodeFactory.instance.objectNode();
        for (String key : new String[] {"name", "code", "description", "status", "tags", "icon"}) {
            if (snapshot.has(key)) basic.set(key, snapshot.get(key));
        }
        return normalizeTree(basic, "");
    }

    private JsonNode process(JsonNode snapshot, boolean modern) {
        JsonNode xml = snapshot.get(modern ? "process" : "processXml");
        if (xml == null || xml.isNull()) return JsonNodeFactory.instance.nullNode();
        if (!xml.isTextual()) throw invalidShape("process");
        return JsonNodeFactory.instance.textNode(xmlNormalizer.normalize(xml.asText()));
    }

    private JsonNode grouped(JsonNode snapshot, boolean modern, String[][] keys, Set<String> notCaptured) {
        ObjectNode group = JsonNodeFactory.instance.objectNode();
        for (String[] pair : keys) {
            String source = pair[0];
            String name = pair[1];
            if (!modern && !Set.of("tables", "forms", "actions").contains(name)) {
                continue;
            }
            JsonNode value = snapshot.get(source);
            if (value == null && !modern) {
                notCaptured.add(moduleFor(name));
                continue;
            }
            group.set(name, collection(value, name));
        }
        return group;
    }

    private String moduleFor(String collection) {
        return switch (collection) {
            case "tables", "tableRelations", "relationTables" -> TABLES;
            case "forms", "linkFormComponents" -> FORMS;
            case "actions" -> ACTIONS;
            default -> collection;
        };
    }

    private JsonNode collection(JsonNode value, String name) {
        ObjectNode items = JsonNodeFactory.instance.objectNode();
        if (value == null) return items;
        if (!value.isArray()) throw invalidShape(name);
        int index = 0;
        for (JsonNode item : value) {
            String key = identity(name, item, index++);
            if (items.has(key)) throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                    "A saved version contains duplicate " + name + " identity: " + key);
            items.set(key, normalizeTree(item, name));
        }
        return sort(items);
    }

    private JsonNode decisions(JsonNode snapshot, boolean modern) {
        return VersionDecisionMatcher.initial(snapshot, modern);
    }

    private JsonNode documents(JsonNode snapshot, Set<String> notCaptured) {
        JsonNode value = snapshot.get("documents");
        if (value == null) {
            notCaptured.add(DOCUMENTS);
            return JsonNodeFactory.instance.objectNode();
        }
        if (!value.isObject()) throw invalidShape("documents");
        return normalizeTree(value, "documents");
    }

    private JsonNode normalizeTree(JsonNode value, String property) {
        return normalizeTree(value, property, false);
    }

    private JsonNode normalizeTree(JsonNode value, String property, boolean extraction) {
        if (value.isObject()) {
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            value.properties().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String key = entry.getKey().equals("fieldDefinitions") ? "fields" : entry.getKey();
                JsonNode item = entry.getValue();
                if ((!extraction && item.isNull()) || generatedId(key)
                        || "mainTableViews".equals(property) && "viewId".equals(key)) return;
                if (!extraction && "fields".equals(property) && "isComputed".equals(key) && item.isBoolean() && !item.asBoolean()) {
                    return;
                }
                // #1667: the designer stores an unused FK reference as either null or [].
                // Do not collapse non-empty references or invalid empty references on an active FK.
                if (!extraction && "fields".equals(property) && "refPrimaryKeyFields".equals(key)
                        && !value.path("isForeignKey").asBoolean(false) && item.isArray() && item.isEmpty()) {
                    return;
                }
                // Email extraction mappings are ordered configuration, not table-field collections.
                normalized.set(key, normalizeTree(item, key, extraction
                        || "emailMonitors".equals(property) && "extractionRules".equals(key)));
            });
            return normalized;
        }
        if (value.isArray()) {
            if (!extraction && Set.of("fields", "foreignKeys", "tableBindings", "accessRules", "fkFillSources",
                    "viewFields", "stageBindings").contains(property)) {
                return collection(value, property);
            }
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> array.add(normalizeTree(item, property, extraction)));
            return array;
        }
        if (value.isTextual() && Set.of("configJson", "sortConfig", "filterConfig", "computedField",
                "pkGenerationJson", "lookupConfig").contains(property)) {
            if (value.asText().isBlank()) return JsonNodeFactory.instance.nullNode();
            try {
                return normalizeTree(SnapshotSensitiveDataFilter.redact(objectMapper.readTree(value.asText())), property, extraction);
            } catch (IOException e) {
                throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_CONFIG_INVALID",
                        "A saved version contains invalid " + property + " JSON");
            }
        }
        return value;
    }

    private boolean generatedId(String key) {
        return Set.of("tableId", "formId", "bindingId", "relationTableId", "componentId",
                "actionId", "connectionId", "connectionUid", "templateId", "ruleId", "ruleUid",
                "sourceRuleId", "targetFormId", "targetBindingId", "linkedFormId", "targetId")
                .contains(key);
    }

    private String identity(String name, JsonNode item, int index) {
        return switch (name) {
            case "tables", "relationTables" -> required(item, "tableName", name);
            case "tableRelations" -> required(item, "sourceTableName", name) + "."
                    + required(item, "sourceFieldName", name) + "->"
                    + required(item, "targetTableName", name) + "."
                    + required(item, "targetFieldName", name);
            case "forms" -> initialFormIdentity(item, index);
            case "linkFormComponents" -> required(item, "componentName", name);
            case "mainTableViews" -> initialViewIdentity(item, index);
            case "actions" -> required(item, "actionName", name);
            case "connections" -> required(item, "name", name);
            case "emailMonitors", "emailTemplates" -> initialEmailIdentity(item, name, index);
            case "fields", "viewFields" -> required(item, "fieldName", name);
            case "foreignKeys" -> required(item, "fieldName", name) + "->"
                    + required(item, "refFieldName", name);
            case "tableBindings" -> bindingTarget(item) + "/"
                    + required(item, "bindingType", name) + "/" + item.path("filterFkFieldName").asText("");
            case "accessRules" -> required(item, "targetType", name) + "/"
                    + (item.hasNonNull("targetCode") ? item.get("targetCode").asText()
                    : item.path("legacyTargetId").asText(""));
            case "fkFillSources" -> required(item, "fieldName", name);
            case "stageBindings" -> required(item, "stageId", name) + "/" + item.path("scene").asText("");
            default -> Integer.toString(index);
        };
    }

    private String initialViewIdentity(JsonNode item, int index) {
        required(item, "mainTableName", "mainTableViews");
        required(item, "viewName", "mainTableViews");
        // Preserve every saved record, including valid same-name Views. Pair alignment follows.
        return "saved-" + index;
    }

    private String initialFormIdentity(JsonNode item, int index) {
        required(item, "formName", "forms");
        return "saved-" + index;
    }

    private String initialEmailIdentity(JsonNode item, String collection, int index) {
        required(item, "name", collection);
        return "saved-" + index;
    }

    private String required(JsonNode node, String key, String collection) {
        String value = node.path(key).asText("");
        if (value.isBlank()) throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_IDENTITY_MISSING",
                "A saved version has a " + collection + " item without " + key);
        return value;
    }

    private String bindingTarget(JsonNode binding) {
        String tableName = binding.path("tableName").asText("");
        if (!tableName.isBlank()) return tableName;
        String relationTableName = binding.path("relationTableName").asText("");
        if (!relationTableName.isBlank() && !relationTableName.startsWith("unresolved:")) {
            return relationTableName;
        }
        // RELATED bindings can legitimately have no tableName. Their exported order is the
        // only remaining stable discriminator when their relation table cannot be resolved.
        return "related-position-" + required(binding, "sortOrder", "tableBindings");
    }

    private ObjectNode sort(ObjectNode value) {
        ObjectNode sorted = JsonNodeFactory.instance.objectNode();
        Map<String, JsonNode> entries = new TreeMap<>();
        value.properties().forEach(entry -> entries.put(entry.getKey(), entry.getValue()));
        entries.forEach(sorted::set);
        return sorted;
    }

    private DeveloperBusinessException invalidShape(String field) {
        return new DeveloperBusinessException("BIZ_VERSION_COMPARE_SNAPSHOT_INVALID",
                "A saved version has an invalid " + field + " structure");
    }
}
