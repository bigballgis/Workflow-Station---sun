package com.developer.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewriter for database ID references in BPMN XML.
 *
 * <p>Used during clone/import of function units to replace residual source function unit IDs
 * in BPMN extension properties with the target function unit's corresponding IDs.
 * Relevant extension elements look like:
 * <pre>{@code
 *   <custom:property name="subTableId" value="13" />
 *   <custom:property name="formId" value="11" />
 *   <custom:property name="actionIds" value="[12,34,56]" />
 *   <custom_1:values name="actionIds" value="[12,34]" />
 * }</pre>
 *
 * <p>Resolution order (inside blocks):
 * <ol>
 *   <li><b>Name first</b>: when the same {@code <X:properties>} block contains both {@code subTableName} +
 *       {@code subTableId} (or {@code tableName} + {@code tableId} / {@code formName} +
 *       {@code formId}), look up the corresponding entity ID on the clone side by name and write it back;
 *       this way even if the source data has inconsistent IDs and names (user swapped tables/forms in the designer
 *       causing stale IDs), the cloned BPMN will still point to the correct cloned entities.</li>
 *   <li><b>ID fallback</b>: no name attribute, or name not found on the clone side → use old ID → new ID mapping.</li>
 *   <li><b>actionIds array</b>: map each ID individually; keep original for unmatched (actionNames length often
 *       differs from actionIds, making reliable name matching impossible).</li>
 * </ol>
 *
 * <p>Supports both Base64-encoded and plain-text XML: decodes, rewrites, and re-encodes in the original format.
 */
public final class BpmnIdRewriter {

    /** Matches {@code <prefix:properties ...> ... </prefix:properties>} blocks (including open/close tags). */
    private static final Pattern PROPERTIES_BLOCK = Pattern.compile(
            "<(\\w+):properties\\b[^>]*>.*?</\\1:properties>",
            Pattern.DOTALL);

    /** Matches {@code <prefix:properties ...>} opening tag. */
    private static final Pattern OPENING_PROPERTIES_TAG = Pattern.compile(
            "<(\\w+):properties\\b[^>]*>",
            Pattern.DOTALL);

    /** Matches {@code <prefix:property ... />} or {@code <prefix:values ... />} self-closing extension elements. */
    private static final Pattern EXTENSION_ELEMENT = Pattern.compile(
            "<(\\w+):(property|values)\\s+([^/>]*?)/>",
            Pattern.DOTALL);

    private static final Pattern NAME_ATTR = Pattern.compile(
            "\\bname\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern VALUE_ATTR = Pattern.compile(
            "(\\bvalue\\s*=\\s*[\"'])([^\"']*)([\"'])");

    private BpmnIdRewriter() {
    }

    /**
     * Legacy API: rewrite using ID mapping only.
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping) {
        return rewrite(bpmnXml, tableIdMapping, formIdMapping, actionIdMapping,
                Map.of(), Map.of());
    }

    /**
     * Overload without table renaming — keeps {@code subTableName}/{@code tableName} values verbatim.
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping,
                                 Map<String, Long> clonedTableNameToId,
                                 Map<String, Long> clonedFormNameToId) {
        return rewrite(bpmnXml, tableIdMapping, formIdMapping, actionIdMapping,
                clonedTableNameToId, clonedFormNameToId, Map.of());
    }

    /**
     * Rewrite ID references in BPMN XML, name-first with ID fallback.
     *
     * @param bpmnXml             original BPMN XML (may be Base64 encoded)
     * @param tableIdMapping      old TableDefinition.id → new id (fallback for subTableId / tableId)
     * @param formIdMapping       old FormDefinition.id → new id (fallback for formId)
     * @param actionIdMapping     old ActionDefinition.id → new id (array elements mapped individually for actionIds)
     * @param clonedTableNameToId clone-side table name → clone TableDefinition.id (preferred when subTableName/tableName present in block).
     *                            Keyed by the table name as it appears in the SOURCE BPMN (resolution runs before any name rewrite).
     * @param clonedFormNameToId  clone-side form name → clone FormDefinition.id (preferred when formName present in block)
     * @param sourceToNewTableName source table name → renamed clone table name. When non-empty, {@code subTableName}/{@code tableName}
     *                            property VALUES are rewritten to the new name so the cloned BPMN's runtime table references
     *                            (e.g. MI {@code subTableName} → physical/JSON sub-table) point at the clone's tables, not the source's.
     * @return rewritten BPMN XML, encoding matches input; returns unchanged for null/blank input
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping,
                                 Map<String, Long> clonedTableNameToId,
                                 Map<String, Long> clonedFormNameToId,
                                 Map<String, String> sourceToNewTableName) {
        return rewrite(bpmnXml, tableIdMapping, formIdMapping, actionIdMapping,
                clonedTableNameToId, clonedFormNameToId, sourceToNewTableName, Map.of(), Map.of());
    }

    /**
     * Full rewrite including Send Email extension properties {@code connectionId} / {@code emailTemplateId}.
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping,
                                 Map<String, Long> clonedTableNameToId,
                                 Map<String, Long> clonedFormNameToId,
                                 Map<String, String> sourceToNewTableName,
                                 Map<Long, Long> connectionIdMapping,
                                 Map<Long, Long> emailTemplateIdMapping) {
        return rewrite(bpmnXml, tableIdMapping, formIdMapping, actionIdMapping,
                clonedTableNameToId, clonedFormNameToId, sourceToNewTableName,
                connectionIdMapping, emailTemplateIdMapping, Map.of());
    }

    /**
     * Full rewrite plus Send Task {@code connectionId} when it stores {@code connectionUid} (UUID).
     * Clone mints a new uid; numeric {@code connectionId} mapping alone cannot rewrite that value.
     */
    public static String rewrite(String bpmnXml,
                                 Map<Long, Long> tableIdMapping,
                                 Map<Long, Long> formIdMapping,
                                 Map<Long, Long> actionIdMapping,
                                 Map<String, Long> clonedTableNameToId,
                                 Map<String, Long> clonedFormNameToId,
                                 Map<String, String> sourceToNewTableName,
                                 Map<Long, Long> connectionIdMapping,
                                 Map<Long, Long> emailTemplateIdMapping,
                                 Map<String, String> connectionUidMapping) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return bpmnXml;
        }
        if (!hasRewritableMappings(tableIdMapping, formIdMapping, actionIdMapping,
                clonedTableNameToId, clonedFormNameToId, sourceToNewTableName,
                connectionIdMapping, emailTemplateIdMapping, connectionUidMapping)) {
            return bpmnXml;
        }

        String decoded = XmlEncodingUtil.smartDecode(bpmnXml);
        boolean wasEncoded = !decoded.equals(bpmnXml);

        String rewritten = rewriteAll(decoded,
                tableIdMapping, formIdMapping, actionIdMapping,
                clonedTableNameToId, clonedFormNameToId, sourceToNewTableName,
                connectionIdMapping, emailTemplateIdMapping, connectionUidMapping);

        if (rewritten.equals(decoded)) {
            return bpmnXml;
        }
        return wasEncoded ? XmlEncodingUtil.encode(rewritten) : rewritten;
    }

    /**
     * Main rewrite flow:
     * <ul>
     *   <li>Inside {@code <X:properties>} blocks: apply "name-first + ID fallback" rewriting;</li>
     *   <li>Outside blocks: for scattered {@code <X:property>} / {@code <X:values>} self-closing elements,
     *       apply ID fallback only.</li>
     * </ul>
     */
    private static String rewriteAll(String xml,
                                     Map<Long, Long> tableIdMapping,
                                     Map<Long, Long> formIdMapping,
                                     Map<Long, Long> actionIdMapping,
                                     Map<String, Long> clonedTableNameToId,
                                     Map<String, Long> clonedFormNameToId,
                                     Map<String, String> sourceToNewTableName,
                                     Map<Long, Long> connectionIdMapping,
                                     Map<Long, Long> emailTemplateIdMapping,
                                     Map<String, String> connectionUidMapping) {
        Matcher m = PROPERTIES_BLOCK.matcher(xml);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (m.find()) {
            // Outside block region: ID fallback only
            sb.append(rewriteUnwrapped(
                    xml.substring(pos, m.start()),
                    tableIdMapping, formIdMapping, actionIdMapping,
                    connectionIdMapping, emailTemplateIdMapping, connectionUidMapping));
            // Inside block: name + ID combined judgment
            sb.append(rewriteBlock(
                    m.group(),
                    tableIdMapping, formIdMapping, actionIdMapping,
                    clonedTableNameToId, clonedFormNameToId, sourceToNewTableName,
                    connectionIdMapping, emailTemplateIdMapping, connectionUidMapping));
            pos = m.end();
        }
        sb.append(rewriteUnwrapped(
                xml.substring(pos),
                tableIdMapping, formIdMapping, actionIdMapping,
                connectionIdMapping, emailTemplateIdMapping, connectionUidMapping));
        return sb.toString();
    }

    /**
     * Rewrite the content of a {@code <X:properties>...</X:properties>} block,
     * applying name-first + ID fallback strategy.
     */
    private static String rewriteBlock(String block,
                                       Map<Long, Long> tableIdMapping,
                                       Map<Long, Long> formIdMapping,
                                       Map<Long, Long> actionIdMapping,
                                       Map<String, Long> clonedTableNameToId,
                                       Map<String, Long> clonedFormNameToId,
                                       Map<String, String> sourceToNewTableName,
                                       Map<Long, Long> connectionIdMapping,
                                       Map<Long, Long> emailTemplateIdMapping,
                                       Map<String, String> connectionUidMapping) {
        Matcher openM = OPENING_PROPERTIES_TAG.matcher(block);
        if (!openM.find()) {
            return block;
        }
        int contentStart = openM.end();
        int contentEnd = block.lastIndexOf("</");
        if (contentEnd <= contentStart) {
            return block;
        }
        String openTag = block.substring(0, contentStart);
        String content = block.substring(contentStart, contentEnd);
        String closeTag = block.substring(contentEnd);

        Map<String, String> nameToValue = parsePropertyMap(content);

        Map<String, String> rewrites = new LinkedHashMap<>();

        String newSubTableId = resolveSingularId(
                nameToValue.get("subTableId"),
                nameToValue.get("subTableName"),
                tableIdMapping, clonedTableNameToId);
        if (newSubTableId != null && !newSubTableId.equals(nameToValue.get("subTableId"))) {
            rewrites.put("subTableId", newSubTableId);
        }

        String newTableId = resolveSingularId(
                nameToValue.get("tableId"),
                nameToValue.get("tableName"),
                tableIdMapping, clonedTableNameToId);
        if (newTableId != null && !newTableId.equals(nameToValue.get("tableId"))) {
            rewrites.put("tableId", newTableId);
        }

        String newFormId = resolveSingularId(
                nameToValue.get("formId"),
                nameToValue.get("formName"),
                formIdMapping, clonedFormNameToId);
        if (newFormId != null && !newFormId.equals(nameToValue.get("formId"))) {
            rewrites.put("formId", newFormId);
        }

        if (nameToValue.containsKey("actionIds") && isNonEmpty(actionIdMapping)) {
            String oldActionIds = nameToValue.get("actionIds");
            String newActionIds = remapArrayLongs(oldActionIds, actionIdMapping);
            if (!Objects.equals(oldActionIds, newActionIds)) {
                rewrites.put("actionIds", newActionIds);
            }
        }

        applyConnectionIdRewrite(nameToValue, connectionIdMapping, connectionUidMapping, rewrites);
        applySingularIdRewrite("emailTemplateId", nameToValue, emailTemplateIdMapping, rewrites);

        // Rename table-name references (subTableName / tableName) to the clone's renamed tables.
        // Runtime reads these values directly (e.g. MI subTableName → physical/JSON sub-table), so a
        // stale source name here would make the clone read the SOURCE table's data.
        if (isNonEmpty(sourceToNewTableName)) {
            applyTableNameRewrite("subTableName", nameToValue, sourceToNewTableName, rewrites);
            applyTableNameRewrite("tableName", nameToValue, sourceToNewTableName, rewrites);
        }

        if (rewrites.isEmpty()) {
            return block;
        }

        String newContent = content;
        for (Map.Entry<String, String> e : rewrites.entrySet()) {
            newContent = replacePropertyValueByName(newContent, e.getKey(), e.getValue());
        }
        return openTag + newContent + closeTag;
    }

    /**
     * Rewrite scattered extension elements outside blocks, ID mapping fallback only.
     */
    private static String rewriteUnwrapped(String segment,
                                           Map<Long, Long> tableIdMapping,
                                           Map<Long, Long> formIdMapping,
                                           Map<Long, Long> actionIdMapping,
                                           Map<Long, Long> connectionIdMapping,
                                           Map<Long, Long> emailTemplateIdMapping,
                                           Map<String, String> connectionUidMapping) {
        Map<String, Function<String, String>> remappers = new HashMap<>();
        if (isNonEmpty(tableIdMapping)) {
            Function<String, String> tableRemap = v -> remapSingularLong(v, tableIdMapping);
            remappers.put("subTableId", tableRemap);
            remappers.put("tableId", tableRemap);
        }
        if (isNonEmpty(formIdMapping)) {
            remappers.put("formId", v -> remapSingularLong(v, formIdMapping));
        }
        if (isNonEmpty(actionIdMapping)) {
            remappers.put("actionIds", v -> remapArrayLongs(v, actionIdMapping));
        }
        if (isNonEmpty(connectionIdMapping) || isNonEmpty(connectionUidMapping)) {
            remappers.put("connectionId",
                    v -> remapConnectionId(v, connectionIdMapping, connectionUidMapping));
        }
        if (isNonEmpty(emailTemplateIdMapping)) {
            remappers.put("emailTemplateId", v -> remapSingularLong(v, emailTemplateIdMapping));
        }
        if (remappers.isEmpty()) {
            return segment;
        }

        Matcher m = EXTENSION_ELEMENT.matcher(segment);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String element = m.group();
            String attrs = m.group(3);

            Matcher nameM = NAME_ATTR.matcher(attrs);
            if (!nameM.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            Function<String, String> remapper = remappers.get(nameM.group(1));
            if (remapper == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            Matcher valueM = VALUE_ATTR.matcher(attrs);
            if (!valueM.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            String oldValue = valueM.group(2);
            String newValue = remapper.apply(oldValue);
            if (Objects.equals(oldValue, newValue)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(element));
                continue;
            }
            String oldValueAttr = valueM.group();
            String newValueAttr = valueM.group(1) + newValue + valueM.group(3);
            String newElement = element.replace(oldValueAttr, newValueAttr);
            m.appendReplacement(sb, Matcher.quoteReplacement(newElement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean hasRewritableMappings(Map<Long, Long> tableIdMapping,
                                                 Map<Long, Long> formIdMapping,
                                                 Map<Long, Long> actionIdMapping,
                                                 Map<String, Long> clonedTableNameToId,
                                                 Map<String, Long> clonedFormNameToId,
                                                 Map<String, String> sourceToNewTableName,
                                                 Map<Long, Long> connectionIdMapping,
                                                 Map<Long, Long> emailTemplateIdMapping,
                                                 Map<String, String> connectionUidMapping) {
        return isNonEmpty(tableIdMapping)
                || isNonEmpty(formIdMapping)
                || isNonEmpty(actionIdMapping)
                || isNonEmpty(clonedTableNameToId)
                || isNonEmpty(clonedFormNameToId)
                || isNonEmpty(sourceToNewTableName)
                || isNonEmpty(connectionIdMapping)
                || isNonEmpty(emailTemplateIdMapping)
                || isNonEmpty(connectionUidMapping);
    }

    private static void applyConnectionIdRewrite(Map<String, String> nameToValue,
                                                 Map<Long, Long> connectionIdMapping,
                                                 Map<String, String> connectionUidMapping,
                                                 Map<String, String> rewrites) {
        if (!nameToValue.containsKey("connectionId")) {
            return;
        }
        String oldValue = nameToValue.get("connectionId");
        String newValue = remapConnectionId(oldValue, connectionIdMapping, connectionUidMapping);
        if (!Objects.equals(oldValue, newValue)) {
            rewrites.put("connectionId", newValue);
        }
    }

    private static String remapConnectionId(String value,
                                            Map<Long, Long> connectionIdMapping,
                                            Map<String, String> connectionUidMapping) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (isNonEmpty(connectionUidMapping)) {
            String mappedUid = connectionUidMapping.get(trimmed);
            if (mappedUid != null) {
                return mappedUid;
            }
        }
        return remapSingularLong(trimmed, connectionIdMapping);
    }

    private static void applySingularIdRewrite(String property,
                                               Map<String, String> nameToValue,
                                               Map<Long, Long> idMapping,
                                               Map<String, String> rewrites) {
        if (!isNonEmpty(idMapping) || !nameToValue.containsKey(property)) {
            return;
        }
        String oldValue = nameToValue.get(property);
        String newValue = remapSingularLong(oldValue, idMapping);
        if (!Objects.equals(oldValue, newValue)) {
            rewrites.put(property, newValue);
        }
    }

    /**
     * Parse properties block content, extracting name → value for all
     * {@code <X:property>} / {@code <X:values>} elements.
     */
    private static Map<String, String> parsePropertyMap(String content) {
        Map<String, String> result = new HashMap<>();
        Matcher m = EXTENSION_ELEMENT.matcher(content);
        while (m.find()) {
            String attrs = m.group(3);
            Matcher nameM = NAME_ATTR.matcher(attrs);
            Matcher valueM = VALUE_ATTR.matcher(attrs);
            if (nameM.find() && valueM.find()) {
                result.putIfAbsent(nameM.group(1), valueM.group(2));
            }
        }
        return result;
    }

    /**
     * Queue a rewrite of a table-name property value ({@code subTableName} / {@code tableName}) to its
     * renamed clone counterpart, when present in this block and mapped.
     */
    private static void applyTableNameRewrite(String property,
                                              Map<String, String> nameToValue,
                                              Map<String, String> sourceToNewTableName,
                                              Map<String, String> rewrites) {
        String oldName = nameToValue.get(property);
        if (oldName == null || oldName.isBlank()) {
            return;
        }
        String newName = sourceToNewTableName.get(oldName.trim());
        if (newName != null && !newName.equals(oldName)) {
            rewrites.put(property, newName);
        }
    }

    /**
     * Name-first resolution of a single ID.
     * <ol>
     *   <li>oldName non-null and found in nameToId → returns name-mapped new ID (recommended path).</li>
     *   <li>Otherwise if oldIdStr found in idMapping → returns mapped new ID.</li>
     *   <li>Both fail → returns null (caller preserves original).</li>
     * </ol>
     */
    private static String resolveSingularId(String oldIdStr,
                                            String oldName,
                                            Map<Long, Long> idMapping,
                                            Map<String, Long> nameToId) {
        if (oldIdStr == null) {
            return null;
        }
        if (oldName != null && !oldName.isBlank() && isNonEmpty(nameToId)) {
            Long byName = nameToId.get(oldName.trim());
            if (byName != null) {
                return String.valueOf(byName);
            }
        }
        if (isNonEmpty(idMapping)) {
            try {
                Long oldId = Long.parseLong(oldIdStr.trim());
                Long mapped = idMapping.get(oldId);
                if (mapped != null) {
                    return String.valueOf(mapped);
                }
            } catch (NumberFormatException ignored) {
                // keep original
            }
        }
        return null;
    }

    /**
     * Replace the value of a {@code <X:property|values>} element with the given property name
     * inside a properties block. Compatible with both attribute orderings and single/double quotes.
     */
    private static String replacePropertyValueByName(String content, String propertyName, String newValue) {
        Pattern p = Pattern.compile(
                "(<\\w+:(?:property|values)\\s+[^/>]*?\\bname\\s*=\\s*[\"']"
                        + Pattern.quote(propertyName)
                        + "[\"'][^/>]*?\\bvalue\\s*=\\s*[\"'])([^\"']*)([\"'][^/>]*?/>)"
                        + "|"
                        + "(<\\w+:(?:property|values)\\s+[^/>]*?\\bvalue\\s*=\\s*[\"'])([^\"']*)([\"'][^/>]*?\\bname\\s*=\\s*[\"']"
                        + Pattern.quote(propertyName)
                        + "[\"'][^/>]*?/>)",
                Pattern.DOTALL);
        Matcher m = p.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            if (m.group(1) != null) {
                replacement = m.group(1) + newValue + m.group(3);
            } else {
                replacement = m.group(4) + newValue + m.group(6);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String remapSingularLong(String value, Map<Long, Long> mapping) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            Long old = Long.parseLong(value.trim());
            Long mapped = mapping.get(old);
            return mapped != null ? String.valueOf(mapped) : value;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String remapArrayLongs(String value, Map<Long, Long> mapping) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        boolean wrapped = trimmed.startsWith("[") && trimmed.endsWith("]");
        String inner = wrapped ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        if (inner.isBlank()) {
            return value;
        }
        String[] parts = inner.split(",");
        StringBuilder sb = new StringBuilder();
        if (wrapped) {
            sb.append('[');
        }
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String part = parts[i].trim();
            try {
                Long old = Long.parseLong(part);
                Long mapped = mapping.get(old);
                if (mapped != null) {
                    sb.append(mapped);
                    changed = true;
                } else {
                    sb.append(part);
                }
            } catch (NumberFormatException e) {
                sb.append(part);
            }
        }
        if (wrapped) {
            sb.append(']');
        }
        return changed ? sb.toString() : value;
    }

    private static boolean isNonEmpty(Map<?, ?> m) {
        return m != null && !m.isEmpty();
    }
}
