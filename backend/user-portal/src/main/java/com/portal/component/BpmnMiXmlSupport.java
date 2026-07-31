package com.portal.component;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BPMN XML parsing helpers used to locate multi-instance (MI) sub-process configuration
 * (flowable:collection, assigneeField, sequence flows) in process definitions.
 * Extracted from {@link TaskProcessComponent}; pure functions only (no Spring dependencies).
 */
final class BpmnMiXmlSupport {

    private BpmnMiXmlSupport() {
    }

    static Element findElementByBpmnId(Document document, String id) {
        if (document == null || id == null || id.isBlank()) {
            return null;
        }
        NodeList nodes = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && id.equals(e.getAttribute("id"))) {
                return e;
            }
        }
        return null;
    }

    // ==================== BPMN XML parsing helpers ====================

    static Document parseBpmnSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    static Element findElementByLocalNameAndId(Document document, String localName, String id) {
        if (document == null || localName == null || id == null) {
            return null;
        }
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    static Element firstDirectChild(Element parent, String localName) {
        if (parent == null || localName == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    static List<String> getDirectChildTextValues(Element parent, String localName) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (parent == null || localName == null) {
            return values;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                String text = element.getTextContent();
                if (text != null && !text.isBlank()) {
                    values.add(text.trim());
                }
            }
        }
        return values;
    }

    /**
     * Outgoing flows often use sequenceFlow sourceRef to the activity id without an explicit &lt;outgoing&gt; under userTask.
     */
    static List<String> listSequenceFlowIdsWithSourceRef(Document document, String sourceActivityId) {
        List<String> ids = new ArrayList<>();
        if (document == null || sourceActivityId == null || sourceActivityId.isBlank()) {
            return ids;
        }
        NodeList flows = document.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Node n = flows.item(i);
            if (n instanceof Element e && sourceActivityId.equals(e.getAttribute("sourceRef"))) {
                String fid = e.getAttribute("id");
                if (fid != null && !fid.isBlank()) {
                    ids.add(fid);
                }
            }
        }
        return ids;
    }

    /** multiInstanceLoopCharacteristics is not always the first direct child of subProcess in some exports. */
    static Element findMultiInstanceLoopInSubProcess(Element subProcess) {
        if (subProcess == null) {
            return null;
        }
        Element direct = firstDirectChild(subProcess, "multiInstanceLoopCharacteristics");
        if (direct != null) {
            return direct;
        }
        NodeList list = subProcess.getElementsByTagNameNS("*", "multiInstanceLoopCharacteristics");
        if (list.getLength() > 0 && list.item(0) instanceof Element e) {
            return e;
        }
        return null;
    }

    /**
     * 解析 BPMN，建「多实例内层 userTask 的 name → 外层多实例 subProcess 的 name」映射，
     * 供 My Requests 列表把 currentNode（内层任务名，如 "sub form1"）映射成多实例节点名（如 "multi"）。
     * 不含 name 的 MI subProcess 回退用其 id。每个 processDefinition 只需解析一次（调用方缓存）。
     * 解析失败返回空 map（调用方回退 currentNode）。
     */
    static java.util.Map<String, String> buildMiInnerTaskNameToSubProcessName(String bpmnXml) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return map;
        }
        try {
            Document doc = parseBpmnSecurely(bpmnXml);
            NodeList subProcesses = doc.getElementsByTagNameNS("*", "subProcess");
            for (int i = 0; i < subProcesses.getLength(); i++) {
                if (!(subProcesses.item(i) instanceof Element sp)) {
                    continue;
                }
                if (findMultiInstanceLoopInSubProcess(sp) == null) {
                    continue; // 非多实例 subProcess，跳过
                }
                String miName = sp.getAttribute("name");
                if (miName == null || miName.isBlank()) {
                    miName = sp.getAttribute("id"); // 边界无 name 回退 id
                }
                // 该 MI subProcess 内的所有 userTask 的 name → miName
                NodeList tasks = sp.getElementsByTagNameNS("*", "userTask");
                for (int j = 0; j < tasks.getLength(); j++) {
                    if (tasks.item(j) instanceof Element t) {
                        String tn = t.getAttribute("name");
                        if (tn != null && !tn.isBlank()) {
                            map.putIfAbsent(tn.trim(), miName);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败：返回已收集的（可能空）映射，调用方回退 currentNode
        }
        return map;
    }

    static String findFirstPropertyValue(Element root, String propertyName) {
        if (root == null || propertyName == null) {
            return null;
        }
        NodeList nodes = root.getElementsByTagNameNS("*", "property");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element
                    && propertyName.equals(element.getAttribute("name"))) {
                String value = element.getAttribute("value");
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static int findElementStart(String xml, String elementName, String attrName, String attrValue) {
        String openTag = "<" + elementName + " ";
        int pos = 0;
        while (true) {
            int idx = xml.indexOf(openTag, pos);
            if (idx < 0) return -1;
            int tagEnd = xml.indexOf('>', idx);
            if (tagEnd < 0) return -1;
            String tag = xml.substring(idx, tagEnd + 1);
            if (extractAttributeFromTag(tag, attrName, attrValue) != null) {
                return idx;
            }
            pos = idx + 1;
        }
    }

    private static int findElementEnd(String xml, int start) {
        int depth = 1;
        int pos = start + 1;
        while (depth > 0 && pos < xml.length()) {
            int nextOpen = xml.indexOf('<', pos);
            int nextClose = xml.indexOf("</", pos);
            if (nextClose >= 0 && (nextOpen < 0 || nextClose <= nextOpen)) {
                if (nextClose == start) break;
                depth--;
                pos = nextClose + 2;
            } else if (nextOpen >= 0) {
                String nsTag = xml.substring(nextOpen, Math.min(nextOpen + elementName(xml, nextOpen).length() + 2, xml.length()));
                depth++;
                pos = nextOpen + 1;
            } else {
                break;
            }
        }
        int closeTag = xml.indexOf('>', pos);
        return closeTag > 0 ? closeTag + 1 : pos;
    }

    private static String elementName(String xml, int openTagPos) {
        int end = openTagPos + 1;
        while (end < xml.length() && !Character.isWhitespace(xml.charAt(end)) && xml.charAt(end) != '>') {
            end++;
        }
        return xml.substring(openTagPos + 1, end);
    }

    private static String extractAttributeFromTag(String tag, String attrName, String expectedValue) {
        int pos = 0;
        while (pos < tag.length()) {
            while (pos < tag.length() && Character.isWhitespace(tag.charAt(pos))) pos++;
            if (pos >= tag.length()) break;
            int eq = tag.indexOf('=', pos);
            if (eq < 0) break;
            String name = tag.substring(pos, eq).trim();
            pos = eq + 1;
            while (pos < tag.length() && Character.isWhitespace(tag.charAt(pos))) pos++;
            if (pos >= tag.length()) break;
            char quote = tag.charAt(pos);
            if (quote == '"' || quote == '\'') {
                pos++;
                int end = tag.indexOf(quote, pos);
                if (end < 0) break;
                String value = tag.substring(pos, end);
                pos = end + 1;
                if (name.equals(attrName) && value.equals(expectedValue)) {
                    return value;
                }
            } else {
                break;
            }
        }
        return null;
    }

    private static String extractAttribute(String element, String childElement, String attrName) {
        int childStart = element.indexOf("<" + childElement + " ");
        if (childStart < 0) return null;
        int tagEnd = element.indexOf('>', childStart);
        if (tagEnd < 0) return null;
        String tag = element.substring(childStart, tagEnd);
        return extractAttributeFromTag(tag, attrName, null);
    }

    private static String extractAttributeMultiline(String element, String childElement, String attrName) {
        int childStart = element.indexOf("<" + childElement);
        if (childStart < 0) return null;
        int closeTag = element.indexOf("</" + childElement + ">", childStart);
        if (closeTag < 0) return null;
        String inner = element.substring(childStart, closeTag);
        return extractAttributeFromTag(inner, attrName, null);
    }

    static String extractFlowableCollection(Element loopCharacteristics) {
        if (loopCharacteristics == null) {
            return null;
        }

        String collection = loopCharacteristics.getAttributeNS("http://flowable.org/bpmn", "collection");
        if (collection == null || collection.isBlank()) {
            collection = loopCharacteristics.getAttribute("flowable:collection");
        }
        if (collection == null || collection.isBlank()) {
            collection = loopCharacteristics.getAttribute("collection");
        }
        if (collection != null && !collection.isBlank()) {
            return collection.trim();
        }

        NodeList children = loopCharacteristics.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "collection".equals(element.getLocalName())) {
                String text = element.getTextContent();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    static String extractAssigneeFieldFromSubProcess(Element subProcessElement) {
        return findFirstPropertyValue(subProcessElement, "assigneeField");
    }

    /**
     * MI 子任务的分派模式：{@code user}（默认，逐行读用户 id）或 {@code role}（逐行读 role/BU code）。
     * 由设计器写在子流程内层 UserTask 的自定义扩展属性上。
     */
    static String extractAssigneeModeFromSubProcess(Element subProcessElement) {
        return findFirstPropertyValue(subProcessElement, "assigneeMode");
    }

    /** role 模式下存 role code 的子表列名。 */
    static String extractRoleFieldFromSubProcess(Element subProcessElement) {
        return findFirstPropertyValue(subProcessElement, "roleField");
    }

    /** role 模式下存 BU code 的子表列名（可选）。 */
    static String extractBuFieldFromSubProcess(Element subProcessElement) {
        return findFirstPropertyValue(subProcessElement, "buField");
    }

    /**
     * Parses every user task inside an MI subprocess and derives the portal assignment contract by sub-table name.
     * Missing field names remain missing; this parser never supplies legacy column-name defaults.
     */
    static Map<String, Map<String, Object>> buildMiAssignmentsBySubTableName(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return Map.of();
        }
        try {
            Document document = parseBpmnSecurely(bpmnXml);
            Map<String, MiAssignmentContract> contracts = new LinkedHashMap<>();
            NodeList subProcesses = document.getElementsByTagNameNS("*", "subProcess");
            for (int i = 0; i < subProcesses.getLength(); i++) {
                if (!(subProcesses.item(i) instanceof Element subProcess)
                        || findMultiInstanceLoopInSubProcess(subProcess) == null) {
                    continue;
                }
                collectMiAssignmentContracts(subProcess, contracts);
            }
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            contracts.forEach((name, contract) -> result.put(name, contract.toPayload()));
            return result;
        } catch (MiAssignmentConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new MiAssignmentConfigurationException("Failed to parse BPMN MI assignment configuration", e);
        }
    }

    private static void collectMiAssignmentContracts(
            Element subProcess, Map<String, MiAssignmentContract> contracts) {
        NodeList tasks = subProcess.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            if (!(tasks.item(i) instanceof Element task)) {
                continue;
            }
            String mode = normalizedProperty(task, "assigneeMode");
            String subTableName = normalizedProperty(task, "subTableName");
            if (!isSupportedMode(mode) || subTableName == null) {
                continue;
            }
            MiAssignmentContract candidate = new MiAssignmentContract(
                    mode,
                    normalizedProperty(task, "assigneeField"),
                    normalizedProperty(task, "roleField"),
                    normalizedProperty(task, "buField"));
            MiAssignmentContract existing = contracts.putIfAbsent(subTableName, candidate);
            if (existing != null && !existing.equals(candidate)) {
                throw new MiAssignmentConfigurationException(
                        "CONFLICTING_MI_ASSIGNMENT_CONFIG: subTableName '" + subTableName
                                + "' has conflicting MI assignment settings");
            }
        }
    }

    private static String normalizedProperty(Element task, String name) {
        String value = findFirstPropertyValue(task, name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isSupportedMode(String mode) {
        return "user".equalsIgnoreCase(mode)
                || "role".equalsIgnoreCase(mode)
                || "both".equalsIgnoreCase(mode);
    }

    private record MiAssignmentContract(
            String assigneeMode, String assigneeField, String roleField, String buField) {

        private MiAssignmentContract {
            assigneeMode = assigneeMode.toLowerCase(java.util.Locale.ROOT);
        }

        Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("allowUser", !"role".equals(assigneeMode));
            payload.put("allowRole", !"user".equals(assigneeMode));
            putIfPresent(payload, "assigneeField", assigneeField);
            putIfPresent(payload, "roleField", roleField);
            putIfPresent(payload, "buField", buField);
            return payload;
        }

        private static void putIfPresent(Map<String, Object> payload, String key, String value) {
            if (value != null) {
                payload.put(key, value);
            }
        }
    }

    static final class MiAssignmentConfigurationException extends RuntimeException {
        MiAssignmentConfigurationException(String message) {
            super(Objects.requireNonNull(message));
        }

        MiAssignmentConfigurationException(String message, Throwable cause) {
            super(Objects.requireNonNull(message), cause);
        }
    }
}
