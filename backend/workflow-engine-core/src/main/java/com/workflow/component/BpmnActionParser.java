package com.workflow.component;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN actionIds parser component.
 * Extracts actionIds (action definition ID list) bound to userTasks from BPMN process definitions.
 * 
 * Supports three-tier fallback strategy:
 * 1. Flowable BpmnModel in-memory model (extension elements)
 * 2. BPMN XML DOM parsing
 * 3. BPMN XML regex matching
 * 
 * Extracted from TaskManagerComponent to reduce its complexity.
 */
@Slf4j
@Component
public class BpmnActionParser {

    @Autowired
    private RepositoryService repositoryService;

    private static final Pattern ACTION_IDS_IN_USER_TASK = Pattern.compile(
        "name\\s*=\\s*[\"']actionIds[\"'][^>]*?value\\s*=\\s*[\"']([^\"']*)[\"']|value\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?name\\s*=\\s*[\"']actionIds[\"']",
        Pattern.DOTALL
    );

    private static final Pattern GLOBAL_ACTION_IDS = Pattern.compile(
        "name\\s*=\\s*[\"']globalActionIds[\"'][^>]*?value\\s*=\\s*[\"']([^\"']*)[\"']|value\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?name\\s*=\\s*[\"']globalActionIds[\"']",
        Pattern.DOTALL
    );

    /**
     * Deployed BPMN is immutable per {@code processDefinitionId} (id carries version + uuid, so a redeploy
     * yields a new id and naturally invalidates these caches). The To Do list + BU filter + orphan repair
     * read the same XML / userTask properties dozens of times per request, so cache them. Empty string is a
     * tombstone for "resolved to null" to allow caching negative lookups.
     */
    private static final String NULL_SENTINEL = "";
    private final Map<String, String> bpmnXmlByProcessDef = new ConcurrentHashMap<>();
    private final Map<String, String> userTaskPropertyValueCache = new ConcurrentHashMap<>();

    /**
     * Read the value of a custom:property (name/value) on the specified UserTask
     * from the deployed BPMN XML.
     * <p>Consistent with {@link #extractActionIds}: the Flowable in-memory model
     * may not load full custom extensions, so raw XML must be read.</p>
     *
     * @param processDefinitionId Flowable process definition ID (with version:uuid)
     * @param userTaskElementId   BPMN userTask element id (e.g. Task_SubmitApplication)
     * @param propertyName        property name attribute, e.g. assigneeType, roleId
     * @return value or null
     */
    public String getUserTaskExtensionPropertyValue(String processDefinitionId, String userTaskElementId,
                                                    String propertyName) {
        if (processDefinitionId == null || processDefinitionId.isBlank()
                || userTaskElementId == null || userTaskElementId.isBlank()
                || propertyName == null || propertyName.isBlank()) {
            return null;
        }
        String trimmedProp = propertyName.trim();
        String cacheKey = processDefinitionId + '|' + userTaskElementId + '|' + trimmedProp;
        String cached = userTaskPropertyValueCache.get(cacheKey);
        if (cached != null) {
            return NULL_SENTINEL.equals(cached) ? null : cached;
        }
        String resolved = computeUserTaskExtensionPropertyValue(processDefinitionId, userTaskElementId, trimmedProp);
        userTaskPropertyValueCache.put(cacheKey, resolved != null ? resolved : NULL_SENTINEL);
        return resolved;
    }

    private String computeUserTaskExtensionPropertyValue(String processDefinitionId, String userTaskElementId,
                                                         String propertyName) {
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (pd == null) {
                return null;
            }
            String xml = readDeploymentBpmnXml(pd);
            if (xml == null || xml.isBlank()) {
                return null;
            }
            String v = findUserTaskPropertyValueDom(xml, userTaskElementId, propertyName);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
            v = findUserTaskPropertyValueRegex(xml, userTaskElementId, propertyName);
            return v != null && !v.isBlank() ? v.trim() : null;
        } catch (Exception e) {
            log.debug("getUserTaskExtensionPropertyValue {} / {} failed: {}", userTaskElementId, propertyName,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Resolve the sub-table name for a task inside a multi-instance subprocess.
     * Some later nodes in the subprocess do not repeat subTableName, so look at
     * sibling userTasks within the same multi-instance subprocess.
     */
    public String getMultiInstanceSubProcessSubTableName(String processDefinitionId, String userTaskElementId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()
                || userTaskElementId == null || userTaskElementId.isBlank()) {
            return null;
        }
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (pd == null) {
                return null;
            }
            String xml = readDeploymentBpmnXml(pd);
            if (xml == null || xml.isBlank()) {
                return null;
            }
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element userTask = findUserTaskById(doc.getDocumentElement(), userTaskElementId);
            if (userTask == null) {
                return null;
            }
            Element parent = findAncestorSubProcess(userTask);
            if (parent == null || !containsLocalName(parent, "multiInstanceLoopCharacteristics")) {
                return null;
            }
            return findFirstAttributeInTree(parent, "name", "subTableName", "value");
        } catch (Exception e) {
            log.debug("getMultiInstanceSubProcessSubTableName {} failed: {}", userTaskElementId, e.getMessage());
            return null;
        }
    }

    /**
     * Read a custom extension property from the multi-instance {@code subProcess} that contains
     * the given user task (e.g. {@code miTaskStatusField}, {@code miTaskCurrentNodeField}).
     * <p>Walks the sub-process subtree in DOM order, same strategy as
     * {@link #getMultiInstanceSubProcessSubTableName}.</p>
     */
    public String getMultiInstanceSubProcessExtensionPropertyValue(String processDefinitionId, String userTaskElementId,
                                                                   String propertyName) {
        if (processDefinitionId == null || processDefinitionId.isBlank()
                || userTaskElementId == null || userTaskElementId.isBlank()
                || propertyName == null || propertyName.isBlank()) {
            return null;
        }
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (pd == null) {
                return null;
            }
            String xml = readDeploymentBpmnXml(pd);
            if (xml == null || xml.isBlank()) {
                return null;
            }
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element userTask = findUserTaskById(doc.getDocumentElement(), userTaskElementId);
            if (userTask == null) {
                return null;
            }
            Element parent = findAncestorSubProcess(userTask);
            if (parent == null || !containsLocalName(parent, "multiInstanceLoopCharacteristics")) {
                return null;
            }
            String v = findFirstAttributeInTree(parent, "name", propertyName.trim(), "value");
            return v != null && !v.isBlank() ? v.trim() : null;
        } catch (Exception e) {
            log.debug("getMultiInstanceSubProcessExtensionPropertyValue {} / {} failed: {}",
                    userTaskElementId, propertyName, e.getMessage());
            return null;
        }
    }

    private String readDeploymentBpmnXml(ProcessDefinition pd) throws IOException {
        String cached = bpmnXmlByProcessDef.get(pd.getId());
        if (cached != null) {
            return NULL_SENTINEL.equals(cached) ? null : cached;
        }
        String xml = readDeploymentBpmnXmlUncached(pd);
        bpmnXmlByProcessDef.put(pd.getId(), xml != null ? xml : NULL_SENTINEL);
        return xml;
    }

    private String readDeploymentBpmnXmlUncached(ProcessDefinition pd) throws IOException {
        String resourceName = pd.getResourceName();
        String rn = resourceName != null ? resourceName.toLowerCase() : "";
        if (resourceName == null || (!rn.endsWith(".bpmn20.xml") && !rn.endsWith(".bpmn"))) {
            List<String> names = repositoryService.getDeploymentResourceNames(pd.getDeploymentId());
            resourceName = names.stream()
                    .filter(name -> name != null && (name.endsWith(".bpmn20.xml") || name.endsWith(".bpmn")))
                    .findFirst()
                    .orElse(resourceName);
        }
        if (resourceName == null) {
            return null;
        }
        try (InputStream in = repositoryService.getResourceAsStream(pd.getDeploymentId(), resourceName)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Extract actionIds from a task's BPMN definition.
     * Three-tier fallback: BpmnModel extension → BPMN XML DOM → BPMN XML regex.
     */
    public List<String> extractActionIds(Task task) {
        try {
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(
                task.getProcessDefinitionId()
            );

            org.flowable.bpmn.model.UserTask userTask = (org.flowable.bpmn.model.UserTask) bpmnModel.getFlowElement(
                task.getTaskDefinitionKey()
            );

            if (userTask == null) {
                log.debug("UserTask not found in BPMN for task: {}", task.getId());
                return extractActionIdsFromBpmnXmlResource(task, null);
            }

            // 1) Recursively scan all extensions under UserTask (supports custom:properties / flat property)
            List<String> fromExt = extractActionIdsRecursive(userTask.getExtensionElements(), "actionIds");
            if (fromExt != null && !fromExt.isEmpty()) {
                return fromExt;
            }

            // 2) Process-level globalActionIds (designer "global binding")
            org.flowable.bpmn.model.Process mainProcess = bpmnModel.getMainProcess();
            if (mainProcess != null) {
                List<String> global = extractActionIdsRecursive(mainProcess.getExtensionElements(), "globalActionIds");
                if (global != null && !global.isEmpty()) {
                    return global;
                }
            }

            // 3) Raw BPMN text fallback (Flowable sometimes does not put custom namespace child nodes in the in-memory model)
            return extractActionIdsFromBpmnXmlResource(task, userTask.getId());

        } catch (Exception e) {
            log.warn("Failed to extract actionIds for task {}: {}", task.getId(), e.getMessage());
            return extractActionIdsFromBpmnXmlResource(task, task.getTaskDefinitionKey());
        }
    }

    /**
     * Depth-first search for extension nodes whose name attribute matches the given
     * property name (actionIds / globalActionIds).
     */
    List<String> extractActionIdsRecursive(
            Map<String, List<ExtensionElement>> extensions,
            String propertyName) {
        if (extensions == null || extensions.isEmpty()) {
            return null;
        }
        for (List<ExtensionElement> group : extensions.values()) {
            if (group == null) {
                continue;
            }
            for (ExtensionElement el : group) {
                String n = el.getAttributeValue(null, "name");
                if (propertyName.equals(n)) {
                    String value = el.getAttributeValue(null, "value");
                    if (value == null || value.isEmpty()) {
                        value = el.getElementText();
                    }
                    List<String> parsed = parseActionIds(value);
                    if (parsed != null && !parsed.isEmpty()) {
                        return parsed;
                    }
                }
                List<String> nested = extractActionIdsRecursive(el.getChildElements(), propertyName);
                if (nested != null && !nested.isEmpty()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private List<String> extractActionIdsFromBpmnXmlResource(Task task, String userTaskElementId) {
        try {
            ProcessDefinition pd = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
            if (pd == null) {
                return null;
            }
            String xml = readDeploymentBpmnXml(pd);
            if (xml == null || xml.isBlank()) {
                return null;
            }
            String key = userTaskElementId != null ? userTaskElementId : task.getTaskDefinitionKey();
            // 1) DOM parsing (most robust: handles any namespace and attribute order)
            List<String> fromDom = parseActionIdsFromBpmnDom(xml, key, "actionIds");
            if (fromDom != null && !fromDom.isEmpty()) {
                return fromDom;
            }
            // 2) Regex (double-quoted / single-quoted)
            List<String> fromTask = parseActionIdsFromUserTaskXmlBlock(xml, key, "actionIds");
            if (fromTask != null && !fromTask.isEmpty()) {
                return fromTask;
            }
            List<String> global = parseActionIdsFromProcessXmlBlock(xml, "globalActionIds");
            if (global != null && !global.isEmpty()) {
                return global;
            }
            return parseActionIdsFromProcessXmlBlockDom(xml, "globalActionIds");
        } catch (Exception e) {
            log.debug("BPMN XML fallback for actionIds failed: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Internal Methods ====================

    private List<String> parseActionIdsFromBpmnDom(String xml, String taskDefinitionKey, String propertyName) {
        if (xml == null || taskDefinitionKey == null || !"actionIds".equals(propertyName)) {
            return null;
        }
        String value = findUserTaskPropertyValueDom(xml, taskDefinitionKey, propertyName);
        return value != null ? parseActionIds(value) : null;
    }

    /**
     * Find the first extension node in the userTask subtree that has name/value attributes
     * with name equal to propertyName.
     */
    private String findUserTaskPropertyValueDom(String xml, String taskDefinitionKey, String propertyName) {
        if (xml == null || taskDefinitionKey == null || propertyName == null) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            if (root == null) {
                return null;
            }
            Element userTask = findUserTaskById(root, taskDefinitionKey);
            if (userTask == null) {
                return null;
            }
            return findFirstAttributeInTree(userTask, "name", propertyName, "value");
        } catch (Exception e) {
            log.trace("DOM parse for property {} failed: {}", propertyName, e.getMessage());
            return null;
        }
    }

    private String findUserTaskPropertyValueRegex(String xml, String taskDefinitionKey, String propName) {
        if (xml == null || taskDefinitionKey == null || propName == null) {
            return null;
        }
        int idPos = xml.indexOf("id=\"" + taskDefinitionKey + "\"");
        if (idPos < 0) {
            return null;
        }
        int ut = xml.lastIndexOf("<userTask", idPos);
        if (ut < 0) {
            ut = xml.lastIndexOf("userTask ", idPos);
        }
        if (ut < 0) {
            ut = xml.lastIndexOf("<bpmn:userTask", idPos);
        }
        if (ut < 0) {
            return null;
        }
        int end = xml.indexOf("</userTask>", idPos);
        if (end < 0) {
            end = xml.indexOf("</bpmn:userTask>", idPos);
        }
        if (end < 0) {
            return null;
        }
        String block = xml.substring(ut, Math.min(xml.length(), end + 20));
        Pattern p = Pattern.compile(
                "name\\s*=\\s*[\"']" + Pattern.quote(propName)
                        + "[\"'][^>]*?value\\s*=\\s*[\"']([^\"']*)[\"']|value\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?name\\s*=\\s*[\"']"
                        + Pattern.quote(propName) + "[\"']",
                Pattern.DOTALL);
        Matcher m = p.matcher(block);
        if (m.find()) {
            String a = m.group(1);
            String b = m.group(2);
            if (a != null && !a.isEmpty()) {
                return a;
            }
            if (b != null && !b.isEmpty()) {
                return b;
            }
        }
        return null;
    }

    private Element findUserTaskById(Element root, String id) {
        if (root == null || id == null) return null;
        String local = getLocalName(root);
        String nodeId = root.getAttribute("id");
        if ("userTask".equals(local) && id.equals(nodeId)) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                Element found = findUserTaskById((Element) n, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Element findAncestorSubProcess(Element element) {
        Node current = element != null ? element.getParentNode() : null;
        while (current != null) {
            if (current instanceof Element el && "subProcess".equals(getLocalName(el))) {
                return el;
            }
            current = current.getParentNode();
        }
        return null;
    }

    private boolean containsLocalName(Element root, String localName) {
        if (root == null || localName == null) return false;
        if (localName.equals(getLocalName(root))) return true;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && containsLocalName((Element) n, localName)) {
                return true;
            }
        }
        return false;
    }

    private String findFirstAttributeInTree(Element root, String attrName, String attrValue, String valueAttrName) {
        if (root == null) return null;
        String name = root.getAttribute(attrName);
        if (attrValue.equals(name)) {
            String v = root.getAttribute(valueAttrName);
            if (v != null && !v.isEmpty()) return v;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                String v = findFirstAttributeInTree((Element) n, attrName, attrValue, valueAttrName);
                if (v != null) return v;
            }
        }
        return null;
    }

    private static String getLocalName(Element el) {
        String local = el.getLocalName();
        return local != null ? local : el.getTagName().replaceAll("^[^:]+:", "");
    }

    private List<String> parseActionIdsFromProcessXmlBlockDom(String xml, String propertyName) {
        if (xml == null || !"globalActionIds".equals(propertyName)) return null;
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            if (root == null) return null;
            String value = findFirstAttributeInTree(root, "name", "globalActionIds", "value");
            return value != null ? parseActionIds(value) : null;
        } catch (Exception e) {
            log.trace("DOM parse for globalActionIds failed: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Regex Parsing Methods ====================

    private List<String> parseActionIdsFromUserTaskXmlBlock(String xml, String taskDefinitionKey, String propName) {
        if (xml == null || taskDefinitionKey == null) {
            return null;
        }
        int idPos = xml.indexOf("id=\"" + taskDefinitionKey + "\"");
        if (idPos < 0) {
            return null;
        }
        int ut = xml.lastIndexOf("<userTask", idPos);
        if (ut < 0) {
            ut = xml.lastIndexOf("userTask ", idPos);
        }
        if (ut < 0) {
            ut = xml.lastIndexOf("<bpmn:userTask", idPos);
        }
        if (ut < 0) {
            return null;
        }
        int end = xml.indexOf("</userTask>", idPos);
        if (end < 0) {
            end = xml.indexOf("</bpmn:userTask>", idPos);
        }
        if (end < 0) {
            return null;
        }
        String block = xml.substring(ut, Math.min(xml.length(), end + 20));
        if (!"actionIds".equals(propName)) {
            return null;
        }
        Matcher m = ACTION_IDS_IN_USER_TASK.matcher(block);
        if (m.find()) {
            String v = m.group(1) != null && !m.group(1).isEmpty() ? m.group(1) : m.group(2);
            return parseActionIds(v);
        }
        return null;
    }

    private List<String> parseActionIdsFromProcessXmlBlock(String xml, String propName) {
        if (xml == null || !"globalActionIds".equals(propName)) {
            return null;
        }
        Matcher m = GLOBAL_ACTION_IDS.matcher(xml);
        if (m.find()) {
            String v = m.group(1) != null && !m.group(1).isEmpty() ? m.group(1) : m.group(2);
            return parseActionIds(v);
        }
        return null;
    }

    // ==================== Value Parsing ====================

    /**
     * Parse actionIds string: converts "[id1,id2]" or a JSON array to List&lt;String&gt;.
     */
    List<String> parseActionIds(String value) {
        try {
            if (value == null || value.isEmpty()) {
                return null;
            }

            String trimmed = value.trim();

        // JSON array: ["a","b"] or [1,2,3] (designer stores numeric IDs)
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(trimmed);
                    if (node != null && node.isArray() && !node.isEmpty()) {
                        List<String> ids = new ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode n : node) {
                            if (n == null || n.isNull()) continue;
                            String s = n.isTextual() ? n.asText() : n.asText();
                            s = s != null ? s.trim() : "";
                            if (!s.isEmpty()) ids.add(s);
                        }
                        if (!ids.isEmpty()) return ids;
                    }
                } catch (Exception ignore) {
                    // fall through
                }
            }

            // Legacy: "[id1,id2]" -> "id1,id2"
            String cleaned = trimmed.replaceAll("[\\[\\]\\s\"]", "");
            if (cleaned.isEmpty()) return null;

            return java.util.Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            log.error("Error parsing actionIds: " + value, e);
            return null;
        }
    }

    /**
     * Create a DocumentBuilderFactory hardened against XXE (XML External Entity) attacks.
     */
    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception e) {
            log.debug("Failed to set disallow-doctype-decl: {}", e.getMessage());
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception e) {
            log.debug("Failed to set external-general-entities: {}", e.getMessage());
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            log.debug("Failed to set external-parameter-entities: {}", e.getMessage());
        }
        try {
            factory.setXIncludeAware(false);
        } catch (Exception e) {
            log.debug("Failed to set XIncludeAware: {}", e.getMessage());
        }
        return factory;
    }
}
