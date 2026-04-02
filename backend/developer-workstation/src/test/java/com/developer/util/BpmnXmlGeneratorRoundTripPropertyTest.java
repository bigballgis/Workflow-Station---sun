package com.developer.util;

import net.jqwik.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for BPMN XML round-trip consistency.
 *
 * Feature: multi-instance-task-dispatch, Property 15: BPMN XML 往返一致性
 *
 * For any valid MultiInstanceConfig, serializing to XML, parsing back to a config object,
 * and serializing again should produce semantically equivalent XML (same elements, attributes, and values).
 *
 * **Validates: Requirements 8.4**
 */
public class BpmnXmlGeneratorRoundTripPropertyTest {

    /**
     * Feature: multi-instance-task-dispatch, Property 15
     *
     * For any randomly generated valid MultiInstanceConfig, the round-trip process
     * (config → XML → config → XML) should produce semantically equivalent XML.
     *
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: multi-instance-task-dispatch, Property 15: BPMN XML 往返一致性")
    void roundTripPreservesSemanticEquivalence(
            @ForAll("validMultiInstanceConfigs") BpmnXmlGenerator.MultiInstanceConfig config) throws Exception {

        // Step 1: Generate XML from original config
        String xml1 = BpmnXmlGenerator.generateMultiInstanceSubProcess(config);

        // Step 2: Parse XML back to config object
        BpmnXmlGenerator.MultiInstanceConfig parsedConfig = parseMultiInstanceConfig(xml1);

        // Step 3: Generate XML from parsed config
        String xml2 = BpmnXmlGenerator.generateMultiInstanceSubProcess(parsedConfig);

        // Step 4: Verify semantic equivalence
        assertSemanticEquivalence(xml1, xml2, config);
    }

    /**
     * Parse BPMN XML to extract MultiInstanceConfig
     */
    private BpmnXmlGenerator.MultiInstanceConfig parseMultiInstanceConfig(String xml) throws Exception {
        Document doc = parseXmlSecurely(xml);

        // Extract subProcess element
        NodeList subProcesses = doc.getElementsByTagName("bpmn:subProcess");
        assertThat(subProcesses.getLength())
                .as("XML should contain exactly one subProcess element")
                .isEqualTo(1);
        Element subProcess = (Element) subProcesses.item(0);

        // Extract subTableId from subProcess id attribute
        String subProcessId = subProcess.getAttribute("id");
        String subTableId = subProcessId.replace("MultiInstance_SubTable_", "");

        // Extract subTableDisplayName from subProcess name attribute
        String subProcessName = subProcess.getAttribute("name");
        String subTableDisplayName = subProcessName.replace("多实例-", "");

        // Extract multiInstanceLoopCharacteristics
        NodeList miLoopChars = subProcess.getElementsByTagName("bpmn:multiInstanceLoopCharacteristics");
        assertThat(miLoopChars.getLength())
                .as("SubProcess should contain multiInstanceLoopCharacteristics")
                .isEqualTo(1);
        Element miLoopChar = (Element) miLoopChars.item(0);

        // Extract isSequential attribute
        String isSequentialStr = miLoopChar.getAttribute("isSequential");
        BpmnXmlGenerator.ExecutionMode executionMode = "true".equals(isSequentialStr)
                ? BpmnXmlGenerator.ExecutionMode.SEQUENTIAL
                : BpmnXmlGenerator.ExecutionMode.PARALLEL;

        // Extract collection variable name
        String collectionVar = getElementText(miLoopChar, "flowable:collection");

        // Extract element variable name
        String elementVar = getElementText(miLoopChar, "flowable:elementVariable");

        // Extract completion condition (optional)
        String completionCondition = null;
        NodeList completionConditions = miLoopChar.getElementsByTagName("bpmn:completionCondition");
        if (completionConditions.getLength() > 0) {
            completionCondition = completionConditions.item(0).getTextContent().trim();
        }

        // Extract userTask
        NodeList userTasks = subProcess.getElementsByTagName("bpmn:userTask");
        assertThat(userTasks.getLength())
                .as("SubProcess should contain at least one userTask")
                .isGreaterThanOrEqualTo(1);
        Element userTask = (Element) userTasks.item(0);

        // Extract task name
        String taskName = userTask.getAttribute("name");

        // Extract custom properties
        String subTableName = getCustomProperty(userTask, "subTableName");
        String assigneeField = getCustomProperty(userTask, "assigneeField");
        String formId = getCustomProperty(userTask, "formId");

        // Build and return config
        return BpmnXmlGenerator.MultiInstanceConfig.builder()
                .subTableId(subTableId)
                .subTableName(subTableName)
                .subTableDisplayName(subTableDisplayName)
                .assigneeField(assigneeField)
                .taskName(taskName)
                .executionMode(executionMode)
                .completionCondition(completionCondition)
                .formId(formId != null && !formId.isEmpty() ? formId : null)
                .collectionVariableName(collectionVar)
                .elementVariableName(elementVar)
                .build();
    }

    /**
     * Assert semantic equivalence between two XML strings
     */
    private void assertSemanticEquivalence(String xml1, String xml2,
                                            BpmnXmlGenerator.MultiInstanceConfig originalConfig) throws Exception {
        Document doc1 = parseXmlSecurely(xml1);
        Document doc2 = parseXmlSecurely(xml2);

        // Compare subProcess attributes
        Element subProcess1 = (Element) doc1.getElementsByTagName("bpmn:subProcess").item(0);
        Element subProcess2 = (Element) doc2.getElementsByTagName("bpmn:subProcess").item(0);

        assertThat(subProcess2.getAttribute("id"))
                .as("SubProcess ID should be preserved")
                .isEqualTo(subProcess1.getAttribute("id"));

        assertThat(subProcess2.getAttribute("name"))
                .as("SubProcess name should be preserved")
                .isEqualTo(subProcess1.getAttribute("name"));

        // Compare multiInstanceLoopCharacteristics
        Element miLoop1 = (Element) subProcess1.getElementsByTagName("bpmn:multiInstanceLoopCharacteristics").item(0);
        Element miLoop2 = (Element) subProcess2.getElementsByTagName("bpmn:multiInstanceLoopCharacteristics").item(0);

        assertThat(miLoop2.getAttribute("isSequential"))
                .as("isSequential attribute should be preserved")
                .isEqualTo(miLoop1.getAttribute("isSequential"));

        // Compare collection and element variables
        assertThat(getElementText(miLoop2, "flowable:collection"))
                .as("Collection variable name should be preserved")
                .isEqualTo(getElementText(miLoop1, "flowable:collection"));

        assertThat(getElementText(miLoop2, "flowable:elementVariable"))
                .as("Element variable name should be preserved")
                .isEqualTo(getElementText(miLoop1, "flowable:elementVariable"));

        // Compare completion condition
        String completionCondition1 = getCompletionCondition(miLoop1);
        String completionCondition2 = getCompletionCondition(miLoop2);
        assertThat(completionCondition2)
                .as("Completion condition should be preserved")
                .isEqualTo(completionCondition1);

        // Compare userTask
        Element userTask1 = (Element) subProcess1.getElementsByTagName("bpmn:userTask").item(0);
        Element userTask2 = (Element) subProcess2.getElementsByTagName("bpmn:userTask").item(0);

        assertThat(userTask2.getAttribute("id"))
                .as("UserTask ID should be preserved")
                .isEqualTo(userTask1.getAttribute("id"));

        assertThat(userTask2.getAttribute("name"))
                .as("UserTask name should be preserved")
                .isEqualTo(userTask1.getAttribute("name"));

        // Compare custom properties
        assertThat(getCustomProperty(userTask2, "assigneeType"))
                .as("assigneeType property should be preserved")
                .isEqualTo(getCustomProperty(userTask1, "assigneeType"));

        assertThat(getCustomProperty(userTask2, "subTableId"))
                .as("subTableId property should be preserved")
                .isEqualTo(getCustomProperty(userTask1, "subTableId"));

        assertThat(getCustomProperty(userTask2, "subTableName"))
                .as("subTableName property should be preserved")
                .isEqualTo(getCustomProperty(userTask1, "subTableName"));

        assertThat(getCustomProperty(userTask2, "assigneeField"))
                .as("assigneeField property should be preserved")
                .isEqualTo(getCustomProperty(userTask1, "assigneeField"));

        assertThat(getCustomProperty(userTask2, "rowIdVariable"))
                .as("rowIdVariable property should be preserved")
                .isEqualTo(getCustomProperty(userTask1, "rowIdVariable"));

        String formId1 = getCustomProperty(userTask1, "formId");
        String formId2 = getCustomProperty(userTask2, "formId");
        assertThat(formId2)
                .as("formId property should be preserved")
                .isEqualTo(formId1);

        // Compare internal structure (startEvent, endEvent, sequenceFlows)
        assertThat(subProcess2.getElementsByTagName("bpmn:startEvent").getLength())
                .as("Should have same number of startEvents")
                .isEqualTo(subProcess1.getElementsByTagName("bpmn:startEvent").getLength());

        assertThat(subProcess2.getElementsByTagName("bpmn:endEvent").getLength())
                .as("Should have same number of endEvents")
                .isEqualTo(subProcess1.getElementsByTagName("bpmn:endEvent").getLength());

        assertThat(subProcess2.getElementsByTagName("bpmn:sequenceFlow").getLength())
                .as("Should have same number of sequenceFlows")
                .isEqualTo(subProcess1.getElementsByTagName("bpmn:sequenceFlow").getLength());
    }

    /**
     * Get text content of a child element by tag name
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * Get completion condition text (handles optional element)
     */
    private String getCompletionCondition(Element miLoop) {
        NodeList conditions = miLoop.getElementsByTagName("bpmn:completionCondition");
        if (conditions.getLength() > 0) {
            return conditions.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * Get custom property value from userTask
     */
    private String getCustomProperty(Element userTask, String propertyName) {
        NodeList properties = userTask.getElementsByTagName("custom:property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if (propertyName.equals(property.getAttribute("name"))) {
                return property.getAttribute("value");
            }
        }
        return null;
    }

    /**
     * Parse XML securely to prevent XXE attacks
     * Wraps the XML fragment in a proper BPMN document with namespace declarations
     */
    private Document parseXmlSecurely(String xml) throws Exception {
        // Wrap the XML fragment in a proper BPMN document with namespace declarations
        String wrappedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
                "xmlns:flowable=\"http://flowable.org/bpmn\" " +
                "xmlns:custom=\"http://custom.namespace\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                xml +
                "</bpmn:definitions>";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // Security features to prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(wrappedXml)));
    }

    // ==================== Providers ====================

    /**
     * Generate valid MultiInstanceConfig objects with random but valid values
     */
    @Provide
    Arbitrary<BpmnXmlGenerator.MultiInstanceConfig> validMultiInstanceConfigs() {
        // Combine required fields
        Arbitrary<RequiredFields> requiredFields = Combinators.combine(
                subTableIds(),
                subTableNames(),
                subTableDisplayNames(),
                assigneeFields(),
                taskNames(),
                executionModes()
        ).as(RequiredFields::new);

        // Combine optional fields
        Arbitrary<OptionalFields> optionalFields = Combinators.combine(
                optionalCompletionConditions(),
                optionalFormIds(),
                optionalCollectionVariableNames(),
                optionalElementVariableNames()
        ).as(OptionalFields::new);

        return Combinators.combine(requiredFields, optionalFields)
                .as((required, optional) ->
                        BpmnXmlGenerator.MultiInstanceConfig.builder()
                                .subTableId(required.subTableId)
                                .subTableName(required.subTableName)
                                .subTableDisplayName(required.displayName)
                                .assigneeField(required.assigneeField)
                                .taskName(required.taskName)
                                .executionMode(required.executionMode)
                                .completionCondition(optional.completionCondition)
                                .formId(optional.formId)
                                .collectionVariableName(optional.collectionVar)
                                .elementVariableName(optional.elementVar)
                                .build()
                );
    }

    // Helper records for grouping parameters
    private record RequiredFields(
            String subTableId,
            String subTableName,
            String displayName,
            String assigneeField,
            String taskName,
            BpmnXmlGenerator.ExecutionMode executionMode
    ) {}

    private record OptionalFields(
            String completionCondition,
            String formId,
            String collectionVar,
            String elementVar
    ) {}

    private Arbitrary<String> subTableIds() {
        return Arbitraries.integers().between(1, 9999)
                .map(String::valueOf);
    }

    private Arbitrary<String> subTableNames() {
        return Combinators.combine(
                Arbitraries.of("fu", "tbl", "data", "sub"),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).map(String::toLowerCase)
        ).as((prefix, suffix) -> prefix + "_" + suffix);
    }

    private Arbitrary<String> subTableDisplayNames() {
        return Arbitraries.of(
                "参与人列表",
                "审批步骤",
                "评审人",
                "任务列表",
                "项目明细",
                "费用明细",
                "物料清单",
                "人员信息",
                "设备列表",
                "文档清单"
        );
    }

    private Arbitrary<String> assigneeFields() {
        return Arbitraries.of(
                "assignee_user_id",
                "approver_id",
                "reviewer_id",
                "handler_id",
                "owner_id",
                "processor_id",
                "responsible_user_id"
        );
    }

    private Arbitrary<String> taskNames() {
        return Arbitraries.of(
                "填写参会信息",
                "审批",
                "评审",
                "完成任务",
                "处理项目",
                "填写费用",
                "确认物料",
                "补充信息",
                "设备检查",
                "文档审核"
        );
    }

    private Arbitrary<BpmnXmlGenerator.ExecutionMode> executionModes() {
        return Arbitraries.of(
                BpmnXmlGenerator.ExecutionMode.PARALLEL,
                BpmnXmlGenerator.ExecutionMode.SEQUENTIAL
        );
    }

    private Arbitrary<String> optionalCompletionConditions() {
        Arbitrary<String> conditions = Arbitraries.of(
                "${nrOfCompletedInstances == nrOfInstances}",
                "${nrOfCompletedInstances >= 3}",
                "${nrOfCompletedInstances > nrOfInstances / 2}",
                "${nrOfActiveInstances == 0}"
        );
        return Arbitraries.frequencyOf(
                Tuple.of(3, conditions),
                Tuple.of(7, Arbitraries.just((String) null))
        );
    }

    private Arbitrary<String> optionalFormIds() {
        Arbitrary<String> formIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "form_" + s);
        return Arbitraries.frequencyOf(
                Tuple.of(3, formIds),
                Tuple.of(7, Arbitraries.just((String) null))
        );
    }

    private Arbitrary<String> optionalCollectionVariableNames() {
        Arbitrary<String> varNames = Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .withChars('_')
                .ofMinLength(5)
                .ofMaxLength(30)
                .map(s -> "collection_" + s);
        return Arbitraries.frequencyOf(
                Tuple.of(2, varNames),
                Tuple.of(8, Arbitraries.just((String) null))
        );
    }

    private Arbitrary<String> optionalElementVariableNames() {
        Arbitrary<String> varNames = Arbitraries.of(
                "currentItem",
                "item",
                "element",
                "row",
                "record"
        );
        return Arbitraries.frequencyOf(
                Tuple.of(2, varNames),
                Tuple.of(8, Arbitraries.just((String) null))
        );
    }
}
