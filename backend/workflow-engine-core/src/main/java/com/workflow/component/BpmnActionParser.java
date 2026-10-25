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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN actionIds parser component.
 * Extracts the action definition IDs bound to a userTask.
 * <p>
 * Node {@code actionIds} and process-level {@code globalActionIds} are independent sources:
 * they are merged (node-only first, then global) rather than treated as fallbacks.
 * Legacy IDs bound in both scopes appear once in the global group.
 * Each source uses model → XML when absent; duplicate values use XML to retain document order.
 */
@Slf4j
@Component
public class BpmnActionParser {

    @Autowired
    private RepositoryService repositoryService;

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
     * Extract action IDs for a runtime task: node bindings plus process-level Global bindings.
     */
    public List<String> extractActionIds(Task task) {
        org.flowable.bpmn.model.BpmnModel model;
        try {
            model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        } catch (Exception ex) {
            // FALLBACK(migration): deployed XML remains authoritative when Flowable cannot load its model.
            log.debug("BPMN model unavailable for action bindings: {}", task.getProcessDefinitionId());
            return extractActionIdsFromBpmnXmlResource(task, task.getTaskDefinitionKey());
        }
        var userTask = resolveUserTask(model, task);
        var process = model != null ? model.getMainProcess() : null;
        var node = BpmnActionPropertyReader.model(userTask != null ? userTask.getExtensionElements() : null, "actionIds");
        var global = BpmnActionPropertyReader.model(process != null ? process.getExtensionElements() : null, "globalActionIds");
        List<String> nodeIds = node.duplicated() ? null : parseActionIds(node.raw());
        List<String> globalIds = global.duplicated() ? null : parseActionIds(global.raw());
        if (nodeIds == null || globalIds == null || node.duplicated() || global.duplicated()) {
            // Flowable groups extension elements by tag, losing cross-tag order for duplicate keys.
            XmlActionSources xml = readActionIdsFromBpmnXml(task, userTask != null ? userTask.getId() : task.getTaskDefinitionKey());
            if ((node.duplicated() && (xml == null || xml.nodeIds() == null))
                    || (global.duplicated() && (xml == null || xml.globalIds() == null))) {
                throw new IllegalStateException("Deployed BPMN is required to resolve duplicate action properties");
            }
            if (xml != null) {
                if (nodeIds == null || node.duplicated()) nodeIds = xml.nodeIds();
                if (globalIds == null || global.duplicated()) globalIds = xml.globalIds();
            }
        }
        return mergeActionIds(nodeIds, globalIds);
    }

    private static org.flowable.bpmn.model.UserTask resolveUserTask(
            org.flowable.bpmn.model.BpmnModel bpmnModel, Task task) {
        if (bpmnModel == null || task.getTaskDefinitionKey() == null) {
            return null;
        }
        org.flowable.bpmn.model.FlowElement el = bpmnModel.getFlowElement(task.getTaskDefinitionKey());
        return el instanceof org.flowable.bpmn.model.UserTask userTask ? userTask : null;
    }

    /**
     * Node-only IDs first, then Global IDs in their existing order.
     */
    List<String> mergeActionIds(List<String> nodeIds, List<String> globalIds) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        appendIds(merged, nodeIds);
        LinkedHashSet<String> globals = new LinkedHashSet<>();
        appendIds(globals, globalIds);
        merged.removeAll(globals);
        merged.addAll(globals);
        return merged.isEmpty() ? null : new ArrayList<>(merged);
    }

    private static void appendIds(LinkedHashSet<String> target, List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                target.add(id);
            }
        }
    }

    private record XmlActionSources(List<String> nodeIds, List<String> globalIds) {}

    private List<String> extractActionIdsFromBpmnXmlResource(Task task, String userTaskElementId) {
        XmlActionSources xml = readActionIdsFromBpmnXml(task, userTaskElementId);
        return xml == null ? null : mergeActionIds(xml.nodeIds(), xml.globalIds());
    }

    private XmlActionSources readActionIdsFromBpmnXml(Task task, String userTaskElementId) {
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element node = findUserTaskById(document.getDocumentElement(), key);
            Element process = node;
            while (process != null && !"process".equals(getLocalName(process))) {
                process = process.getParentNode() instanceof Element parent ? parent : null;
            }
            if (process == null) {
                var processes = document.getElementsByTagNameNS("*", "process");
                process = processes.getLength() > 0 ? (Element) processes.item(0) : null;
            }
            return new XmlActionSources(
                    parseActionIds(BpmnActionPropertyReader.xml(node, "actionIds").raw()),
                    parseActionIds(BpmnActionPropertyReader.xml(process, "globalActionIds").raw()));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read deployed BPMN action bindings", ex);
        }
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

    List<String> parseActionIds(String value) {
        return BpmnActionPropertyReader.ids(value);
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
