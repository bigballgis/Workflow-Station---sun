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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN actionIds 解析组件
 * 从 BPMN 流程定义中提取 userTask 绑定的 actionIds（动作定义 ID 列表）。
 * 
 * 支持三层回退策略：
 * 1. Flowable BpmnModel 内存模型（extension elements）
 * 2. BPMN XML DOM 解析
 * 3. BPMN XML 正则匹配
 * 
 * 从 TaskManagerComponent 中提取，降低该类的复杂度。
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
     * 从已部署 BPMN XML 读取指定 UserTask 上 custom:property（name / value）的值。
     * <p>与 {@link #extractActionIds} 一致：Flowable 内存模型可能未载入完整 custom 扩展，需读原始 XML。</p>
     *
     * @param processDefinitionId Flowable 流程定义 id（含 version:uuid）
     * @param userTaskElementId   BPMN 中 userTask 的 id（如 Task_SubmitApplication）
     * @param propertyName        property 的 name 属性，如 assigneeType、roleId
     * @return value 或 null
     */
    public String getUserTaskExtensionPropertyValue(String processDefinitionId, String userTaskElementId,
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
            String v = findUserTaskPropertyValueDom(xml, userTaskElementId, propertyName.trim());
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
            v = findUserTaskPropertyValueRegex(xml, userTaskElementId, propertyName.trim());
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
     * 从任务的 BPMN 定义中提取 actionIds。
     * 三层回退：BpmnModel extension → BPMN XML DOM → BPMN XML 正则。
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

            // 1) 递归扫描 UserTask 下全部 extension（兼容 custom:properties / 扁平 property）
            List<String> fromExt = extractActionIdsRecursive(userTask.getExtensionElements(), "actionIds");
            if (fromExt != null && !fromExt.isEmpty()) {
                return fromExt;
            }

            // 2) 流程级 globalActionIds（设计器「全局绑定」）
            org.flowable.bpmn.model.Process mainProcess = bpmnModel.getMainProcess();
            if (mainProcess != null) {
                List<String> global = extractActionIdsRecursive(mainProcess.getExtensionElements(), "globalActionIds");
                if (global != null && !global.isEmpty()) {
                    return global;
                }
            }

            // 3) 原始 BPMN 文本回退（Flowable 有时不把 custom 命名空间子节点放进内存模型）
            return extractActionIdsFromBpmnXmlResource(task, userTask.getId());

        } catch (Exception e) {
            log.warn("Failed to extract actionIds for task {}: {}", task.getId(), e.getMessage());
            return extractActionIdsFromBpmnXmlResource(task, task.getTaskDefinitionKey());
        }
    }

    /**
     * 深度优先查找 name 为给定属性名（actionIds / globalActionIds）的 extension 节点。
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
            // 1) DOM 解析（最稳：任意命名空间与属性顺序）
            List<String> fromDom = parseActionIdsFromBpmnDom(xml, key, "actionIds");
            if (fromDom != null && !fromDom.isEmpty()) {
                return fromDom;
            }
            // 2) 正则（双引号 / 单引号）
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

    // ==================== DOM 解析方法 ====================

    private List<String> parseActionIdsFromBpmnDom(String xml, String taskDefinitionKey, String propertyName) {
        if (xml == null || taskDefinitionKey == null || !"actionIds".equals(propertyName)) {
            return null;
        }
        String value = findUserTaskPropertyValueDom(xml, taskDefinitionKey, propertyName);
        return value != null ? parseActionIds(value) : null;
    }

    /**
     * 在 userTask 子树中查找第一个带 name/value 的扩展节点，且 name 等于 propertyName。
     */
    private String findUserTaskPropertyValueDom(String xml, String taskDefinitionKey, String propertyName) {
        if (xml == null || taskDefinitionKey == null || propertyName == null) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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

    // ==================== 正则解析方法 ====================

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

    // ==================== 值解析 ====================

    /**
     * 解析 actionIds 字符串：将 "[id1,id2]" 或 JSON 数组转换为 List&lt;String&gt;
     */
    List<String> parseActionIds(String value) {
        try {
            if (value == null || value.isEmpty()) {
                return null;
            }

            String trimmed = value.trim();

            // JSON array: ["a","b"] 或 [1,2,3]（设计器存数字 ID）
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
}
