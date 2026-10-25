package com.portal.component;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.platform.common.bpmn.BpmnActionBindings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Resolves a Task Form id (and action names) embedded in a release's deployed BPMN.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChangeHistoryBpmnFormResolver {

    static Long resolveTaskFormIdFromStored(String storedXml, String stageId) {
        return resolveTaskFormId(decodeBpmnXml(storedXml), stageId);
    }

    static Long resolveTaskFormId(String bpmnXml, String stageId) {
        if (bpmnXml == null || bpmnXml.isBlank() || stageId == null || stageId.isBlank()) return null;
        for (String taskElement : userTaskElements(bpmnXml)) {
            if (hasXmlAttribute(taskElement, "id", stageId)) {
                String formId = customPropertyValue(taskElement, "formId");
                try {
                    return formId != null ? Long.valueOf(formId.trim()) : null;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Designer action id to action name, from node and process-global parallel {@code actionIds} /
     * {@code actionNames}. A task whose arrays do not pair up is skipped, and an id two tasks name
     * differently is dropped: neither pairing can be trusted.
     */
    static Map<String, String> actionNamesById(String storedXml) {
        Map<String, String> names = new HashMap<>();
        Set<String> conflicting = new HashSet<>();
        if (storedXml == null || storedXml.isBlank()) return names;
        try {
            var document = BpmnActionBindings.parse(storedXml);
            for (var scope : BpmnActionBindings.scopes(document)) {
                var properties = BpmnActionBindings.properties(scope);
                addActionNames(names, conflicting, properties, BpmnActionBindings.idsKey(scope));
            }
        } catch (IllegalArgumentException ex) {
            // FALLBACK(migration): untrusted legacy XML cannot recover IDs; the caller rejects the incomplete release.
            return Map.of();
        }
        conflicting.forEach(names::remove);
        return names;
    }

    private static void addActionNames(Map<String, String> names, Set<String> conflicting,
                                      Map<String, BpmnActionBindings.Property> properties, String key) {
        var idsProperty = properties.get(key);
        var namesProperty = properties.get(key.equals("actionIds") ? "actionNames" : "globalActionNames");
        if (idsProperty == null || namesProperty == null) return;
        List<String> ids;
        List<String> labels;
        try {
            ids = BpmnActionBindings.ids(idsProperty.value());
            labels = BpmnActionBindings.names(namesProperty.value());
        } catch (IllegalArgumentException ex) {
            // FALLBACK(migration): an invalid pair cannot recover IDs; other valid scopes remain usable.
            return;
        }
        if (ids.size() != labels.size()) return;
        for (int i = 0; i < ids.size(); i++) {
            String previous = names.putIfAbsent(ids.get(i), labels.get(i));
            if (previous != null && !previous.equals(labels.get(i))) conflicting.add(ids.get(i));
        }
    }

    private static List<String> userTaskElements(String bpmnXml) {
        List<String> elements = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < bpmnXml.length()) {
            int start = bpmnXml.indexOf("<bpmn:userTask", searchFrom);
            if (start < 0) start = bpmnXml.indexOf("<userTask", searchFrom);
            if (start < 0) break;
            int end = bpmnXml.indexOf("</bpmn:userTask>", start);
            int closingLength = "</bpmn:userTask>".length();
            if (end < 0) {
                end = bpmnXml.indexOf("</userTask>", start);
                closingLength = "</userTask>".length();
            }
            if (end < 0) break;
            elements.add(bpmnXml.substring(start, end + closingLength));
            searchFrom = end + closingLength;
        }
        return elements;
    }
    static String decodeBpmnXml(String stored) {
        if (stored == null) return null;
        String trimmed = stored.trim();
        if (trimmed.startsWith("<")) return trimmed;
        try {
            return new String(Base64.getMimeDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return stored;
        }
    }
    private static boolean hasXmlAttribute(String element, String name, String expected) {
        return element.contains(name + "=\"" + expected + "\"")
                || element.contains(name + "='" + expected + "'");
    }
    /**
     * Designer BPMN stores node fields on either {@code custom:property} or
     * {@code custom_1:values}. Both local names carry {@code name}/{@code value}.
     */
    private static String customPropertyValue(String element, String propertyName) {
        String fromProperty = valueOnCarrier(element, "property", propertyName);
        return fromProperty != null ? fromProperty : valueOnCarrier(element, "values", propertyName);
    }

    private static String valueOnCarrier(String element, String localName, String propertyName) {
        int searchFrom = 0;
        while (searchFrom < element.length()) {
            int property = indexOfCarrier(element, localName, searchFrom);
            if (property < 0) return null;
            int end = element.indexOf('>', property);
            if (end < 0) return null;
            String tag = element.substring(property, end + 1);
            if (hasXmlAttribute(tag, "name", propertyName)) return xmlAttribute(tag, "value");
            searchFrom = end + 1;
        }
        return null;
    }

    /** Tag whose local name is exactly {@code localName}, so {@code properties} is not {@code property}. */
    private static int indexOfCarrier(String element, String localName, int from) {
        int searchFrom = from;
        while (searchFrom < element.length()) {
            int start = element.indexOf('<', searchFrom);
            if (start < 0) return -1;
            int nameEnd = start + 1;
            while (nameEnd < element.length()) {
                char c = element.charAt(nameEnd);
                if (c == ' ' || c == '>' || c == '/' || c == '\t' || c == '\n' || c == '\r') break;
                nameEnd++;
            }
            String tagName = element.substring(start + 1, nameEnd);
            int colon = tagName.lastIndexOf(':');
            String local = colon >= 0 ? tagName.substring(colon + 1) : tagName;
            if (local.equals(localName)) return start;
            searchFrom = nameEnd;
        }
        return -1;
    }
    private static String xmlAttribute(String element, String name) {
        for (char quote : new char[] {'\"', '\''}) {
            String prefix = name + "=" + quote;
            int start = element.indexOf(prefix);
            if (start < 0) continue;
            start += prefix.length();
            int end = element.indexOf(quote, start);
            if (end >= 0) return element.substring(start, end);
        }
        return null;
    }
}
