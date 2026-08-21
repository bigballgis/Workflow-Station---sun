package com.developer.component.impl;

import com.developer.enums.FormScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the full set of form ↔ BPMN-node bindings out of a process's deployed XML.
 *
 * <p>Read-side counterpart of {@link com.developer.util.AiBpmnFormBindingWriter} (which writes
 * {@code formId}/{@code formName}/{@code formReadOnly} onto AI-generated nodes) and mirrors the
 * frontend parser {@code frontend/developer-workstation/src/utils/bpmnFormBindings.ts}
 * ({@code parseBpmnNodeFormBindings}) property-for-property, so a node the Bind Process Node
 * dialog shows as bound is exactly what this parser extracts.
 *
 * <p>Used by {@link ProcessDesignComponentImpl#save} to keep {@code dw_form_stage_bindings} — the
 * table user-portal's runtime Task Form resolution reads exclusively — synchronized with the BPMN
 * XML, which is otherwise the only place the Bind Process Node dialog persists its selection.
 */
@Component
@Slf4j
public class BpmnFormStageBindingParser {

    private static final Set<String> TASK_NAMES = Set.of(
            "task", "userTask", "serviceTask", "scriptTask", "manualTask",
            "sendTask", "receiveTask", "businessRuleTask");

    /** One binding to reconcile into {@code dw_form_stage_bindings}: (formId, stageId, scene) plus its attributes. */
    public record ParsedBinding(Long formId, String stageId, String stageName, boolean readOnly, FormScene scene) {
    }

    /**
     * Parses every task-like element's form bindings from the BPMN XML.
     *
     * <p>A single node can carry both a TASK binding ({@code formId}/{@code formName}/
     * {@code formReadOnly}) and a REQUEST binding ({@code requestFormId}/{@code requestFormName},
     * always read-only) at once — both are emitted as separate entries when present.
     *
     * @return empty list for blank/unparsable XML (fail-open: callers must not wipe existing
     *         bindings when the XML cannot be parsed at all)
     */
    public List<ParsedBinding> parse(String bpmnXml) {
        List<ParsedBinding> result = new ArrayList<>();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return result;
        }
        try {
            org.w3c.dom.Document document = parseSecurely(bpmnXml);
            NodeList elements = document.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < elements.getLength(); i++) {
                if (!(elements.item(i) instanceof Element element)) {
                    continue;
                }
                if (!TASK_NAMES.contains(localName(element))) {
                    continue;
                }
                String stageId = element.getAttribute("id");
                if (stageId == null || stageId.isBlank()) {
                    continue;
                }
                collectBindingsForTask(element, stageId, result);
            }
        } catch (Exception e) {
            log.warn("Could not parse BPMN XML for form-stage-binding sync: {}", e.getMessage());
            return List.of();
        }
        return result;
    }

    private void collectBindingsForTask(Element task, String stageId, List<ParsedBinding> out) {
        Long formId = null;
        String formName = null;
        boolean readOnly = false;
        Long requestFormId = null;

        NodeList descendants = task.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < descendants.getLength(); i++) {
            if (!(descendants.item(i) instanceof Element prop)) {
                continue;
            }
            String ln = localName(prop);
            if (!"property".equals(ln) && !"values".equals(ln)) {
                continue;
            }
            String name = prop.getAttribute("name");
            String value = prop.getAttribute("value");
            if (value == null || value.isBlank()) {
                continue;
            }
            switch (name) {
                case "formId" -> formId = parseLongOrNull(value);
                case "formName" -> formName = value;
                case "formReadOnly" -> readOnly = "true".equals(value);
                case "requestFormId" -> requestFormId = parseLongOrNull(value);
                default -> { /* actionIds / formScene: irrelevant to stage bindings */ }
            }
        }

        if (formId != null) {
            out.add(new ParsedBinding(formId, stageId, formName, readOnly, FormScene.TASK));
        }
        if (requestFormId != null) {
            // My Requests designs are read-only by definition (mirrors bpmnFormBindings.ts).
            out.add(new ParsedBinding(requestFormId, stageId, formName, true, FormScene.REQUEST));
        }
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private org.w3c.dom.Document parseSecurely(String xml) throws Exception {
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
}
