package com.developer.util;

import com.developer.entity.ActionDefinition;
import com.developer.exception.AiGenerationException;
import com.platform.common.bpmn.BpmnActionBindings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the BPMN user-task action bindings ({@code actionIds}/{@code actionNames}, and the process-level
 * {@code globalActionIds}/{@code globalActionNames}) in step with every AI write.
 *
 * <p>BPMN is the only place the action ↔ user-task relation is persisted: {@code ActionDefinition} has
 * no stage column. Every AI write that rebuilds actions (new database ids) or replaces the process
 * (new XML without numeric ids) therefore has to carry the relation across itself, or tasks end up with
 * no buttons — runtime reads {@code actionIds} only and has no name fallback.</p>
 *
 * <p>Identity across the write is the action name ({@code uk_action_name_fu}), the same key the model
 * uses. Per action, the final placement is:</p>
 * <ul>
 *   <li>declared in this write with non-empty {@code stageIds} → exactly those user tasks (the proposal
 *       is authoritative for that action, including removing it from tasks it no longer lists);</li>
 *   <li>otherwise → wherever it was before: the bindings the written BPMN itself carries, or — when it
 *       carries none at all (fresh model output) — the pre-write BPMN's binding on the same user-task id
 *       and at process level;</li>
 *   <li>names with no surviving action are dropped — no stale id is ever written back.</li>
 * </ul>
 * <p>User tasks are matched by id only, never by task name.</p>
 */
@Slf4j
public final class AiBpmnActionBindingWriter {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CUSTOM_NAMESPACE = "http://custom.bpmn.io/schema";
    /** 流程属性面板写入的容器命名空间（customModdle 里的 custom 前缀）。 */
    private static final String PLATFORM_NAMESPACE = "http://workflow.platform/schema/custom";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiBpmnActionBindingWriter() {
    }

    /**
     * Bindings of the BPMN as it was before the write, by action name.
     *
     * @param byTask    user-task id → action names (tasks without a readable binding are absent)
     * @param global    process-level action names, {@code null} when the process carries none
     * @param namesById action id → name as of before the write; resolves old ids in the written BPMN
     */
    public record Snapshot(Map<String, List<String>> byTask, List<String> global, Map<String, String> namesById) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), null, Map.of());
    }

    /** Final placement by action name: every user task of the BPMN (possibly empty), plus the process-level list. */
    public record Plan(Map<String, List<String>> byTask, List<String> global) {
    }

    /** Reads the pre-write bindings. {@code bpmnXml} must already be decoded; blank means "no process yet". */
    public static Snapshot capture(String bpmnXml, List<ActionDefinition> actions) {
        Map<String, String> namesById = new LinkedHashMap<>();
        if (actions != null) {
            for (ActionDefinition action : actions) {
                if (action != null && action.getId() != null && action.getActionName() != null) {
                    namesById.put(String.valueOf(action.getId()), action.getActionName());
                }
            }
        }
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return new Snapshot(Map.of(), null, Map.copyOf(namesById));
        }
        Document document = parseOrFail(bpmnXml);
        Snapshot own = readBindings(document, namesById);
        return new Snapshot(own.byTask(), own.global(), Map.copyOf(namesById));
    }

    /**
     * Computes the final placement without touching the XML — shared by the write and the proposal preview.
     *
     * @param availableNames names of the actions that will exist after the write
     */
    public static Plan plan(String bpmnXml, Snapshot before, Collection<String> availableNames,
                            List<Map<String, Object>> generatedActions) {
        return plan(parseOrFail(bpmnXml), before, Set.copyOf(availableNames), generatedActions);
    }

    /** User-task ids each action declares through {@code stageIds} in this write (only non-empty declarations). */
    public static Map<String, List<String>> declaredStages(List<Map<String, Object>> generatedActions) {
        Map<String, List<String>> declared = new LinkedHashMap<>();
        if (generatedActions == null) {
            return declared;
        }
        for (Map<String, Object> generated : generatedActions) {
            if (generated == null || !(generated.get("actionName") instanceof String name)
                    || !(generated.get("stageIds") instanceof List<?> stageIds)) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            for (Object stageId : stageIds) {
                if (stageId instanceof String id && !id.isBlank() && !ids.contains(id.trim())) {
                    ids.add(id.trim());
                }
            }
            if (!ids.isEmpty()) {
                declared.put(name, ids);
            }
        }
        return declared;
    }

    /**
     * Rewrites the action bindings of {@code bpmnXml} (decoded) to the {@link #plan} against the persisted actions.
     * Returns the input unchanged when nothing differs.
     */
    public static String rebind(String bpmnXml, Snapshot before, List<ActionDefinition> actions,
                                List<Map<String, Object>> generatedActions) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return bpmnXml;
        }
        Map<String, ActionDefinition> persistedByName = new LinkedHashMap<>();
        if (actions != null) {
            for (ActionDefinition action : actions) {
                if (action != null && action.getId() != null && action.getActionName() != null) {
                    persistedByName.put(action.getActionName(), action);
                }
            }
        }
        try {
            Document document = parseSecurely(bpmnXml);
            Plan plan = plan(document, before, persistedByName.keySet(), generatedActions);
            boolean changed = false;
            for (Element task : userTasks(document)) {
                List<ActionDefinition> target = plan.byTask().getOrDefault(task.getAttribute("id"), List.of())
                        .stream().map(persistedByName::get).toList();
                changed |= writeBinding(document, task, "actionIds", "actionNames", target);
            }
            if (plan.global() != null) {
                List<ActionDefinition> target = plan.global().stream().map(persistedByName::get).toList();
                for (Element process : elements(document, "process")) {
                    changed |= writeBinding(document, process, "globalActionIds", "globalActionNames", target);
                }
            }
            return changed ? serialize(document) : bpmnXml;
        } catch (Exception e) {
            // 同 AiBpmnFormBindingWriter：绑定失败意味着 actionIds 没注入、运行时任务没按钮，
            // 是要人看的故障，不能降级成 warn 级的 400；栈同样先落一次。
            log.error("Failed to bind AI-generated actions to BPMN user tasks", e);
            throw new AiGenerationException("AI_BPMN_BINDING_FAILED",
                    "Failed to bind AI-generated actions to BPMN user tasks: " + e.getMessage());
        }
    }

    private static Plan plan(Document document, Snapshot before, Set<String> available,
                             List<Map<String, Object>> generatedActions) {
        Snapshot written = readBindings(document, before.namesById());
        // A BPMN that carries any binding of its own (designer-saved, an undo snapshot) is taken as complete;
        // only one that carries none at all (fresh model output, which may not contain ids) inherits the
        // pre-write bindings by user-task id.
        Snapshot inherited = written.byTask().isEmpty() && written.global() == null ? before : written;
        Map<String, List<String>> declared = declaredStages(generatedActions);
        declared.keySet().retainAll(available);

        Map<String, List<String>> byTask = new LinkedHashMap<>();
        for (Element task : userTasks(document)) {
            String taskId = task.getAttribute("id");
            List<String> names = new ArrayList<>();
            declared.forEach((name, stageIds) -> {
                if (stageIds.contains(taskId)) names.add(name);
            });
            List<String> carried = inherited.byTask().get(taskId);
            if (carried != null) {
                for (String name : carried) {
                    if (!declared.containsKey(name) && available.contains(name) && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
            byTask.put(taskId, List.copyOf(names));
        }

        List<String> carriedGlobal = inherited.global();
        List<String> global = carriedGlobal == null ? null : carriedGlobal.stream()
                .filter(name -> available.contains(name) && !declared.containsKey(name)).distinct().toList();
        return new Plan(byTask, global);
    }

    /**
     * Readable bindings by name. A scope whose ids resolve to nothing (unknown ids and no paired names)
     * counts as unbound, so the pre-write binding can still be carried; an explicit empty list counts as bound.
     */
    private static Snapshot readBindings(Document document, Map<String, String> namesById) {
        Map<String, List<String>> byTask = new LinkedHashMap<>();
        for (Element task : userTasks(document)) {
            List<String> names = resolveNames(task, "actionIds", "actionNames", namesById);
            if (names != null) byTask.put(task.getAttribute("id"), names);
        }
        List<String> global = null;
        for (Element process : elements(document, "process")) {
            List<String> names = resolveNames(process, "globalActionIds", "globalActionNames", namesById);
            if (names != null) global = names;
        }
        return new Snapshot(byTask, global, namesById);
    }

    private static List<String> resolveNames(Element scope, String idsKey, String namesKey,
                                             Map<String, String> namesById) {
        Map<String, BpmnActionBindings.Property> properties = BpmnActionBindings.properties(scope);
        BpmnActionBindings.Property idsProperty = properties.get(idsKey);
        if (idsProperty == null) {
            return null;
        }
        List<String> ids;
        List<String> pairedNames;
        try {
            ids = BpmnActionBindings.ids(idsProperty.value());
            BpmnActionBindings.Property namesProperty = properties.get(namesKey);
            pairedNames = namesProperty == null ? List.of() : BpmnActionBindings.names(namesProperty.value());
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring unreadable {} on BPMN element '{}': {}", idsKey, scope.getAttribute("id"), e.getMessage());
            return null;
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String name = namesById.get(ids.get(i));
            if (name == null && pairedNames.size() == ids.size()) {
                name = pairedNames.get(i);
            }
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : names;
    }

    /** @return whether the element changed */
    private static boolean writeBinding(Document document, Element scope, String idsKey, String namesKey,
                                        List<ActionDefinition> actions) throws Exception {
        List<String> names = actions.stream().map(ActionDefinition::getActionName).toList();
        List<Element> existing = actionProperties(scope, Set.of(idsKey, namesKey));
        if (actions.isEmpty() ? existing.isEmpty() : sameBinding(scope, idsKey, namesKey, actions, existing)) {
            return false;
        }
        for (Element property : existing) {
            property.getParentNode().removeChild(property);
        }
        if (!actions.isEmpty()) {
            Element properties = ensureCustomProperties(document, scope);
            List<Long> ids = actions.stream().map(ActionDefinition::getId).toList();
            appendProperty(document, properties, idsKey, OBJECT_MAPPER.writeValueAsString(ids));
            appendProperty(document, properties, namesKey, OBJECT_MAPPER.writeValueAsString(names));
        }
        log.info("AI write rebound {} on BPMN element '{}' to {}", idsKey, scope.getAttribute("id"), names);
        return true;
    }

    /** Exactly one ids and one names property, already holding this list — leave the XML untouched. */
    private static boolean sameBinding(Element scope, String idsKey, String namesKey,
                                       List<ActionDefinition> actions, List<Element> existing) {
        Map<String, BpmnActionBindings.Property> current = BpmnActionBindings.properties(scope);
        if (existing.size() != 2 || !current.containsKey(idsKey) || !current.containsKey(namesKey)) {
            return false;
        }
        try {
            return actions.stream().map(action -> String.valueOf(action.getId())).toList()
                    .equals(BpmnActionBindings.ids(current.get(idsKey).value()))
                    && actions.stream().map(ActionDefinition::getActionName).toList()
                    .equals(BpmnActionBindings.names(current.get(namesKey).value()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Document parseOrFail(String bpmnXml) {
        try {
            return parseSecurely(bpmnXml);
        } catch (Exception e) {
            throw new AiGenerationException("AI_BPMN_BINDING_FAILED",
                    "Failed to read action bindings from BPMN: " + e.getMessage());
        }
    }

    private static Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static List<Element> userTasks(Document document) {
        return elements(document, "userTask");
    }

    private static List<Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    private static Element ensureCustomProperties(Document document, Element scope) {
        Element root = document.getDocumentElement();
        if (!root.hasAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "custom")) {
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:custom", CUSTOM_NAMESPACE);
        }

        Element extensionElements = directChild(scope, "extensionElements");
        if (extensionElements == null) {
            String prefix = scope.getPrefix();
            String qualifiedName = prefix == null || prefix.isBlank()
                    ? "extensionElements" : prefix + ":extensionElements";
            extensionElements = document.createElementNS(BPMN_NAMESPACE, qualifiedName);
            scope.insertBefore(extensionElements, scope.getFirstChild());
        }

        Element properties = customProperties(extensionElements);
        if (properties == null) {
            properties = document.createElementNS(CUSTOM_NAMESPACE, "custom:properties");
            extensionElements.appendChild(properties);
        }
        return properties;
    }

    /**
     * 取（或不取）设计器认得的属性容器。
     *
     * <p>只认 {@code http://custom.bpmn.io/schema} 与 {@code http://workflow.platform/schema/custom}
     * 两个命名空间——前端 {@code bpmnExtensions.getExtensionProperties()} 只从这两种容器收属性。
     * 早先这里按 localName 匹配，于是模型输出的 {@code <camunda:properties>} 被当成可复用容器，
     * formId/actionIds 全被塞进去：XML 里数据俱全，设计器面板却一片空白，运行时反而正常
     * （引擎按 localName 递归扫），是最难查的那种"看着没绑上"。</p>
     */
    private static Element customProperties(Element extensionElements) {
        NodeList children = extensionElements.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element
                    && "properties".equals(localName(element))
                    && (CUSTOM_NAMESPACE.equals(element.getNamespaceURI())
                        || PLATFORM_NAMESPACE.equals(element.getNamespaceURI()))) {
                return element;
            }
        }
        return null;
    }

    private static Element directChild(Element parent, String expectedLocalName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && expectedLocalName.equals(localName(element))) {
                return element;
            }
        }
        return null;
    }

    /**
     * Every action-binding property directly under this element's own {@code extensionElements}, in any
     * container — a stale copy left in a second container would otherwise win or shadow the rewrite.
     * Nested scopes (user tasks inside a process) are not descended into.
     */
    private static List<Element> actionProperties(Element scope, Set<String> keys) {
        List<Element> result = new ArrayList<>();
        Element extensionElements = directChild(scope, "extensionElements");
        if (extensionElements == null) {
            return result;
        }
        NodeList descendants = extensionElements.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < descendants.getLength(); i++) {
            if (descendants.item(i) instanceof Element property
                    && ("property".equals(localName(property)) || "values".equals(localName(property)))
                    && keys.contains(property.getAttribute("name"))) {
                result.add(property);
            }
        }
        return result;
    }

    private static void appendProperty(Document document, Element properties, String name, String value) {
        Element property = document.createElementNS(CUSTOM_NAMESPACE, "custom:property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        properties.appendChild(property);
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
