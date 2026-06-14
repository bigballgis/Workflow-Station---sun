package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.entity.ActionDefinition;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.util.XmlEncodingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 功能单元完整性校验协作类。
 * 负责发布前的结构校验：必备流程/主表/流程表单、BPMN-DMN 交叉引用、DECISION_TABLE 动作配置。
 */
@Component
@RequiredArgsConstructor
@Slf4j
class FunctionUnitValidator {

    /**
     * 校验功能单元完整性，结果写入传入的 ValidationResult。
     */
    void validate(FunctionUnit functionUnit, ValidationResult result) {
        // Check process definition exists
        if (functionUnit.getProcessDefinition() == null) {
            result.addWarning("MISSING_PROCESS", "Function unit has no process definition", null);
        }

        // Check primary table exists
        boolean hasMainTable = functionUnit.getTableDefinitions().stream()
                .anyMatch(t -> t.getTableType() == com.developer.enums.TableType.MAIN);
        if (!hasMainTable) {
            result.addWarning("MISSING_MAIN_TABLE", "Function unit has no main table", null);
        }

        // Check process form exists
        boolean hasProcessForm = functionUnit.getFormDefinitions().stream()
                .anyMatch(f -> f.getFormType() == com.developer.enums.FormType.PROCESS);
        if (!hasProcessForm) {
            result.addWarning("MISSING_PROCESS_FORM", "Function unit has no process form", null);
        }

        // BPMN-DMN cross-reference validation
        validateBpmnDmnCrossReferences(functionUnit, result);

        // DECISION_TABLE action config validation
        validateDecisionTableActions(functionUnit, result);
    }

    /**
     * BPMN-DMN cross-reference validation
     * Ensure BPMN decision keys exist in same function unit decision definitions
     */
    private void validateBpmnDmnCrossReferences(FunctionUnit functionUnit, ValidationResult result) {
        List<DecisionDefinition> decisions = functionUnit.getDecisionDefinitions();

        // No decision errors when there are no DecisionDefinitions
        if (decisions == null || decisions.isEmpty()) {
            return;
        }

        // Cannot cross-validate without process definition
        if (functionUnit.getProcessDefinition() == null) {
            return;
        }

        String bpmnXml = functionUnit.getProcessDefinition().getBpmnXml();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return;
        }

        // Decode BPMN XML (may be Base64)
        String decodedBpmnXml = XmlEncodingUtil.smartDecode(bpmnXml);

        // Extract decision keys from DMN service tasks in BPMN XML
        Set<String> referencedKeys = extractDmnReferenceKeys(decodedBpmnXml, functionUnit.getId());

        // Build set of existing decision definition keys
        Set<String> definedKeys = new HashSet<>();
        for (DecisionDefinition decision : decisions) {
            definedKeys.add(decision.getDecisionKey());
        }

        // Check BPMN decision keys exist in DecisionDefinition list
        for (String referencedKey : referencedKeys) {
            if (!definedKeys.contains(referencedKey)) {
                result.addError("INVALID_DECISION_REFERENCE",
                        "BPMN process references decision key '" + referencedKey + "' which does not exist in this function unit",
                        referencedKey);
            } else {
                decisions.stream()
                        .filter(d -> referencedKey.equals(d.getDecisionKey()))
                        .findFirst()
                        .ifPresent(d -> {
                            if (d.getDmnXml() == null || d.getDmnXml().isBlank()) {
                                result.addError("EMPTY_DMN_XML",
                                        "BPMN references decision key '" + referencedKey + "' but its DMN XML is empty",
                                        referencedKey);
                            }
                        });
            }
        }

        // Check for DecisionDefinitions not referenced by BPMN
        for (String definedKey : definedKeys) {
            if (!referencedKeys.contains(definedKey)) {
                result.addWarning("UNREFERENCED_DECISION",
                        "Decision definition '" + definedKey + "' is not referenced by any BPMN service task",
                        definedKey);
            }
        }
    }

    /**
     * DECISION_TABLE action config validation
     * When ActionType is DECISION_TABLE, validate config_json has decisionKey, inputMappings, outputMappings,
     * and decisionKey references a DecisionDefinition in the same function unit.
     */
    private void validateDecisionTableActions(FunctionUnit functionUnit, ValidationResult result) {
        List<ActionDefinition> actions = functionUnit.getActionDefinitions();
        if (actions == null || actions.isEmpty()) {
            return;
        }

        // Build set of existing decision definition keys
        Set<String> definedDecisionKeys = new HashSet<>();
        List<DecisionDefinition> decisions = functionUnit.getDecisionDefinitions();
        if (decisions != null) {
            for (DecisionDefinition decision : decisions) {
                definedDecisionKeys.add(decision.getDecisionKey());
            }
        }

        for (ActionDefinition action : actions) {
            if (action.getActionType() != com.developer.enums.ActionType.DECISION_TABLE) {
                continue;
            }

            Map<String, Object> config = action.getConfigJson();
            String actionName = action.getActionName();

            if (config == null || config.isEmpty()) {
                result.addError("MISSING_DECISION_CONFIG",
                        "DECISION_TABLE action '" + actionName + "' has empty config_json",
                        actionName);
                continue;
            }

            // Validate required field: decisionKey
            Object decisionKeyObj = config.get("decisionKey");
            boolean hasDecisionKey = decisionKeyObj instanceof String dk && !dk.isBlank();
            if (!hasDecisionKey) {
                result.addError("MISSING_DECISION_KEY",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'decisionKey'",
                        actionName);
            }

            // Validate required field: inputMappings
            if (!config.containsKey("inputMappings")) {
                result.addError("MISSING_INPUT_MAPPINGS",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'inputMappings'",
                        actionName);
            }

            // Validate required field: outputMappings
            if (!config.containsKey("outputMappings")) {
                result.addError("MISSING_OUTPUT_MAPPINGS",
                        "DECISION_TABLE action '" + actionName + "' config_json is missing required field 'outputMappings'",
                        actionName);
            }

            // Validate decisionKey references DecisionDefinition in same function unit
            if (hasDecisionKey) {
                String decisionKey = (String) decisionKeyObj;
                if (!definedDecisionKeys.contains(decisionKey)) {
                    result.addError("INVALID_DECISION_REFERENCE",
                            "DECISION_TABLE action '" + actionName + "' references decision key '" + decisionKey + "' which does not exist in this function unit",
                            actionName);
                } else if (decisions != null) {
                    decisions.stream()
                            .filter(d -> decisionKey.equals(d.getDecisionKey()))
                            .findFirst()
                            .ifPresent(d -> {
                                if (d.getDmnXml() == null || d.getDmnXml().isBlank()) {
                                    result.addError("EMPTY_DMN_XML",
                                            "DECISION_TABLE action '" + actionName + "' references decision '" + decisionKey + "' which has no DMN XML content",
                                            actionName);
                                }
                            });
                }
            }
        }
    }

    /**
     * Extract decisionTableReferenceKey from DMN service tasks in BPMN XML
     * Supports two formats:
     * 1. Attribute: flowable:decisionTableReferenceKey="key"
     * 2. Extension element: flowable:field name="decisionTableReferenceKey" > flowable:string
     */
    private Set<String> extractDmnReferenceKeys(String bpmnXml, Long functionUnitId) {
        Set<String> keys = new HashSet<>();
        try {
            Document document = parseXmlSecurely(bpmnXml);

            // Find all serviceTask elements
            NodeList serviceTasks = document.getElementsByTagNameNS("*", "serviceTask");
            for (int i = 0; i < serviceTasks.getLength(); i++) {
                Element serviceTask = (Element) serviceTasks.item(i);

                // Check DMN service task (flowable:type="dmn")
                if (!isDmnServiceTask(serviceTask)) {
                    continue;
                }

                // Try attribute for decisionTableReferenceKey
                String key = extractKeyFromAttribute(serviceTask);
                if (key == null || key.isBlank()) {
                    // Try extension element extraction
                    key = extractKeyFromExtensionElements(serviceTask);
                }

                if (key != null && !key.isBlank()) {
                    keys.add(key.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse BPMN XML for DMN cross-reference validation, functionUnitId={}: {}",
                    functionUnitId, e.getMessage());
        }
        return keys;
    }

    /**
     * Check whether serviceTask is DMN type
     */
    private boolean isDmnServiceTask(Element serviceTask) {
        // Check type attribute under all namespace prefixes
        var attributes = serviceTask.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            if ("type".equals(attr.getLocalName()) && "dmn".equals(attr.getNodeValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract decisionTableReferenceKey from serviceTask attributes
     */
    private String extractKeyFromAttribute(Element serviceTask) {
        var attributes = serviceTask.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            if ("decisionTableReferenceKey".equals(attr.getLocalName())) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    /**
     * Extract decisionTableReferenceKey from extension elements
     * Format: {@code <flowable:field name="decisionTableReferenceKey"><flowable:string>key</flowable:string></flowable:field>}
     */
    private String extractKeyFromExtensionElements(Element serviceTask) {
        NodeList extensionElements = serviceTask.getElementsByTagNameNS("*", "extensionElements");
        for (int i = 0; i < extensionElements.getLength(); i++) {
            Element extElem = (Element) extensionElements.item(i);
            NodeList fields = extElem.getElementsByTagNameNS("*", "field");
            for (int j = 0; j < fields.getLength(); j++) {
                Element field = (Element) fields.item(j);
                if ("decisionTableReferenceKey".equals(field.getAttribute("name"))) {
                    // Read value from flowable:string child
                    NodeList stringElements = field.getElementsByTagNameNS("*", "string");
                    if (stringElements.getLength() > 0) {
                        return stringElements.item(0).getTextContent().trim();
                    }
                    // Read value from flowable:expression child
                    NodeList exprElements = field.getElementsByTagNameNS("*", "expression");
                    if (exprElements.getLength() > 0) {
                        return exprElements.item(0).getTextContent().trim();
                    }
                }
            }
        }
        return null;
    }

    /**
     * XXE-safe XML parsing
     */
    private Document parseXmlSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
