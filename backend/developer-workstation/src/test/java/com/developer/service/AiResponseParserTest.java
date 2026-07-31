package com.developer.service;

import com.developer.exception.AiGenerationException;
import com.developer.service.impl.AiResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiResponseParser 单元测试 —— 对照 {@code GenAI/parse_response.md} 的移植保真度。
 *
 * <p>覆盖 AP 那边踩出来的每条规整规则：文档/标记提取、generatedData 三级解析、
 * 字段元数据转字符串、configJson 反序列化与非法即丢、BPMN Base64 解码与无任务节点拒绝。</p>
 */
class AiResponseParserTest {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    private final AiResponseParser parser = new AiResponseParser(new ObjectMapper());

    private Map<String, Object> gatewayResponse(int status, String content) {
        return Map.of("status", status, "body", Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private static String validBpmn() {
        return "<?xml version=\"1.0\"?><bpmn:definitions xmlns:bpmn=\"" + BPMN_NS + "\" "
                + "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" "
                + "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" "
                + "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\">"
                + "<bpmn:process id=\"p\" isExecutable=\"true\">"
                + "<bpmn:startEvent id=\"s\"/><bpmn:userTask id=\"t\"><bpmn:extensionElements>"
                + "<custom:properties xmlns:custom=\"http://custom.bpmn.io/schema\">"
                + "<custom:property name=\"assigneeType\" value=\"PROCESS_INITIATOR\"/>"
                + "</custom:properties></bpmn:extensionElements></bpmn:userTask><bpmn:endEvent id=\"e\"/>"
                + "<bpmn:sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"t\"/>"
                + "<bpmn:sequenceFlow id=\"f2\" sourceRef=\"t\" targetRef=\"e\"/>"
                + "</bpmn:process><bpmndi:BPMNDiagram id=\"d\"><bpmndi:BPMNPlane id=\"plane\" bpmnElement=\"p\">"
                + "<bpmndi:BPMNShape id=\"s_di\" bpmnElement=\"s\"><dc:Bounds x=\"0\" y=\"0\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>"
                + "<bpmndi:BPMNShape id=\"t_di\" bpmnElement=\"t\"><dc:Bounds x=\"100\" y=\"0\" width=\"100\" height=\"80\"/></bpmndi:BPMNShape>"
                + "<bpmndi:BPMNShape id=\"e_di\" bpmnElement=\"e\"><dc:Bounds x=\"260\" y=\"0\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>"
                + "<bpmndi:BPMNEdge id=\"f1_di\" bpmnElement=\"f1\"><di:waypoint x=\"36\" y=\"18\"/><di:waypoint x=\"100\" y=\"18\"/></bpmndi:BPMNEdge>"
                + "<bpmndi:BPMNEdge id=\"f2_di\" bpmnElement=\"f2\"><di:waypoint x=\"200\" y=\"18\"/><di:waypoint x=\"260\" y=\"18\"/></bpmndi:BPMNEdge>"
                + "</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>";
    }

    @Test
    void parse_requirementsDocument_isExtractedAndStrippedFromReply() {
        String content = "Here you go.\n---REQUIREMENTS_DOC_START---\n# Requirements Document\nbody\n"
                + "---REQUIREMENTS_DOC_END---\n---PHASE_COMPLETE---";

        Map<String, Object> result = parser.parse(gatewayResponse(200, content));

        assertEquals("REQUIREMENTS", result.get("documentType"));
        assertEquals("# Requirements Document\nbody", result.get("document"));
        assertEquals(Boolean.TRUE, result.get("phaseComplete"));
        assertEquals("Here you go.", result.get("reply"));
    }

    @Test
    void parse_designDocument_isExtracted() {
        String content = "---DESIGN_DOC_START---\n# Design Document\n---DESIGN_DOC_END---";

        Map<String, Object> result = parser.parse(gatewayResponse(200, content));

        assertEquals("DESIGN", result.get("documentType"));
        assertEquals("# Design Document", result.get("document"));
        assertEquals(Boolean.FALSE, result.get("phaseComplete"));
        assertEquals("", result.get("reply"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_generatedData_normalizesFieldMetadataToStrings() {
        String json = "{\"tableDefinitions\":[{\"tableName\":\"t\",\"fieldDefinitions\":["
                + "{\"fieldName\":\"f\",\"length\":50,\"precision\":10,\"scale\":2,\"defaultValue\":0}]}],"
                + "\"processDefinition\":{\"bpmnXml\":\"" + validBpmn().replace("\"", "\\\"") + "\"}}";
        Map<String, Object> result = parser.parse(gatewayResponse(200,
                "---GENERATED_DATA_START---\n" + json + "\n---GENERATED_DATA_END---"));

        Map<String, Object> data = (Map<String, Object>) result.get("generatedData");
        Map<String, Object> field = (Map<String, Object>) ((List<Map<String, Object>>)
                ((List<Map<String, Object>>) data.get("tableDefinitions")).get(0).get("fieldDefinitions")).get(0);

        assertEquals("50", field.get("length"));
        assertEquals("10", field.get("precision"));
        assertEquals("2", field.get("scale"));
        assertEquals("0", field.get("defaultValue"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_generatedData_coercesConfigJsonAndLegacyFormType() {
        String json = "{\"formDefinitions\":[{\"formName\":\"a\",\"formType\":\"TASK\",\"configJson\":\"{\\\"k\\\":1}\"},"
                + "{\"formName\":\"b\",\"formType\":\"MAIN\",\"configJson\":\"not json\"}],"
                + "\"actionDefinitions\":[{\"actionName\":\"x\",\"configJson\":\"[1,2]\"}]}";
        Map<String, Object> result = parser.parse(gatewayResponse(200,
                "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---"));

        Map<String, Object> data = (Map<String, Object>) result.get("generatedData");
        List<Map<String, Object>> forms = (List<Map<String, Object>>) data.get("formDefinitions");
        List<Map<String, Object>> actions = (List<Map<String, Object>>) data.get("actionDefinitions");

        assertEquals("TASK", forms.get(0).get("formType"), "TASK 是平台原生表单类型，必须保留用于阶段绑定");
        assertEquals(Map.of("k", 1), forms.get(0).get("configJson"));
        assertNull(forms.get(1).get("configJson"), "非法 configJson 丢成 null，而不是留着炸下游");
        assertNull(actions.get(0).get("configJson"), "JSON 数组不是对象，同样丢弃");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_generatedData_fallsBackToFencedJsonBlock() {
        String json = "{\"name\":\"Order\",\"processDefinition\":{\"bpmnXml\":\""
                + validBpmn().replace("\"", "\\\"") + "\"}}";
        Map<String, Object> result = parser.parse(gatewayResponse(200,
                "---GENERATED_DATA_START---\n```json\n" + json + "\n```\n---GENERATED_DATA_END---"));

        Map<String, Object> data = (Map<String, Object>) result.get("generatedData");
        assertNotNull(data);
        assertEquals("Order", data.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_base64Bpmn_isDecoded() {
        String encoded = Base64.getEncoder().encodeToString(validBpmn().getBytes(StandardCharsets.UTF_8));
        String json = "{\"processDefinition\":{\"bpmnXml\":\"" + encoded + "\"}}";

        Map<String, Object> result = parser.parse(gatewayResponse(200,
                "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---"));

        Map<String, Object> data = (Map<String, Object>) result.get("generatedData");
        String xml = (String) ((Map<String, Object>) data.get("processDefinition")).get("bpmnXml");
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("bpmn:userTask"));
    }

    @Test
    void parse_bpmnWithoutTaskNodes_isRejected() {
        String json = "{\"name\":\"Order\",\"processDefinition\":{\"bpmnXml\":\"not really bpmn\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> parser.parse(gatewayResponse(200,
                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_BPMN_NO_TASK_NODES", ex.getErrorCode());
    }

    @Test
    void parse_bpmnWithDisconnectedTask_isRejected() {
        String xml = validBpmn().replace("<bpmn:endEvent id=\"e\"/>",
                        "<bpmn:userTask id=\"orphan\"/><bpmn:endEvent id=\"e\"/>")
                        .replace("<bpmndi:BPMNShape id=\"e_di\"", "<bpmndi:BPMNShape id=\"orphan_di\" bpmnElement=\"orphan\"><dc:Bounds x=\"220\" y=\"100\" width=\"100\" height=\"80\"/></bpmndi:BPMNShape><bpmndi:BPMNShape id=\"e_di\"");
        String json = "{\"processDefinition\":{\"bpmnXml\":\"" + xml.replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                        () -> parser.parse(gatewayResponse(200,
                                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_BPMN_DISCONNECTED_NODES", ex.getErrorCode());
    }

    @Test
    void parse_bpmnWithoutDiagramEdge_isRejected() {
        String xml = validBpmn().replaceAll("<bpmndi:BPMNEdge id=\\\"f2_di\\\".*?</bpmndi:BPMNEdge>", "");
        String json = "{\"processDefinition\":{\"bpmnXml\":\"" + xml.replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                        () -> parser.parse(gatewayResponse(200,
                                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_BPMN_MISSING_DI", ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_taskFormWithExactStageBinding_isAccepted() {
        String xml = validBpmn().replace("<bpmn:userTask id=\"t\">",
                "<bpmn:userTask id=\"t\" name=\"Review Request\">");
        String json = "{\"formDefinitions\":[{\"formName\":\"review_form\",\"formType\":\"TASK\","
                + "\"configJson\":null,\"stageBindings\":[{\"stageId\":\"t\","
                + "\"stageName\":\"Review Request\",\"readOnly\":false}]}],"
                + "\"processDefinition\":{\"bpmnXml\":\"" + xml.replace("\"", "\\\"") + "\"}}";

        Map<String, Object> result = parser.parse(gatewayResponse(200,
                "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---"));

        Map<String, Object> data = (Map<String, Object>) result.get("generatedData");
        List<Map<String, Object>> forms = (List<Map<String, Object>>) data.get("formDefinitions");
        assertEquals("TASK", forms.get(0).get("formType"));
        assertEquals("t", ((List<Map<String, Object>>) forms.get(0).get("stageBindings"))
                .get(0).get("stageId"));
    }

    @Test
    void parse_userTaskWithoutFormStageBinding_isRejected() {
        String json = "{\"formDefinitions\":[],\"processDefinition\":{\"bpmnXml\":\""
                + validBpmn().replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> parser.parse(gatewayResponse(200,
                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_FORM_STAGE_BINDING_INVALID", ex.getErrorCode());
    }

    @Test
    void parse_taskFormWithoutAssigneeType_isRejected() {
        String xml = validBpmn().replace(
                "<custom:property name=\"assigneeType\" value=\"PROCESS_INITIATOR\"/>", "");
        String json = "{\"formDefinitions\":[{\"formName\":\"review_form\",\"formType\":\"TASK\","
                + "\"stageBindings\":[{\"stageId\":\"t\",\"stageName\":\"\",\"readOnly\":false}]}],"
                + "\"processDefinition\":{\"bpmnXml\":\"" + xml.replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> parser.parse(gatewayResponse(200,
                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_TASK_ASSIGNEE_INVALID", ex.getErrorCode());
    }

    @Test
    void parse_actionWithUnknownStageId_isRejected() {
        String json = "{\"formDefinitions\":[{\"formName\":\"review_form\",\"formType\":\"TASK\","
                + "\"stageBindings\":[{\"stageId\":\"t\",\"stageName\":\"\",\"readOnly\":false}]}],"
                + "\"actionDefinitions\":[{\"actionName\":\"approve\",\"actionType\":\"APPROVE\","
                + "\"stageIds\":[\"missing_task\"]}],\"processDefinition\":{\"bpmnXml\":\""
                + validBpmn().replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> parser.parse(gatewayResponse(200,
                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_ACTION_STAGE_BINDING_INVALID", ex.getErrorCode());
    }

    @Test
    void parse_bpmnWithDuplicateSourceAndTarget_isRejected() {
        String xml = validBpmn().replace("</bpmn:process>",
                "<bpmn:sequenceFlow id=\"f1_duplicate\" sourceRef=\"s\" targetRef=\"t\"/>"
                        + "</bpmn:process>")
                .replace("</bpmndi:BPMNPlane>",
                        "<bpmndi:BPMNEdge id=\"f1_duplicate_di\" bpmnElement=\"f1_duplicate\">"
                                + "<di:waypoint x=\"36\" y=\"20\"/><di:waypoint x=\"100\" y=\"20\"/>"
                                + "</bpmndi:BPMNEdge></bpmndi:BPMNPlane>");
        String json = "{\"processDefinition\":{\"bpmnXml\":\"" + xml.replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> parser.parse(gatewayResponse(200,
                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_BPMN_DISCONNECTED_NODES", ex.getErrorCode());
    }

    @Test
    void parse_bpmnWithZeroLengthDiagramEdge_isRejected() {
        String xml = validBpmn().replace(
                "<di:waypoint x=\"36\" y=\"18\"/><di:waypoint x=\"100\" y=\"18\"/>",
                "<di:waypoint x=\"36\" y=\"18\"/><di:waypoint x=\"36\" y=\"18\"/>");
        String json = "{\"processDefinition\":{\"bpmnXml\":\"" + xml.replace("\"", "\\\"") + "\"}}";

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> parser.parse(gatewayResponse(200,
                        "---GENERATED_DATA_START---" + json + "---GENERATED_DATA_END---")));

        assertEquals("AI_BPMN_MISSING_DI", ex.getErrorCode());
    }

    @Test
    void parse_httpError_surfacesGatewayMessage() {
        Map<String, Object> response = Map.of("status", 401, "body",
                Map.of("error", Map.of("message", "invalid token")));

        AiGenerationException ex = assertThrows(AiGenerationException.class, () -> parser.parse(response));

        assertEquals("AI_GATEWAY_HTTP_ERROR", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("401"));
        assertTrue(ex.getMessage().contains("invalid token"));
    }

    @Test
    void parse_emptyAssistantResponse_fails() {
        Map<String, Object> response = Map.of("status", 200, "body", Map.of("choices", List.of()));

        AiGenerationException ex = assertThrows(AiGenerationException.class, () -> parser.parse(response));

        assertEquals("AI_GATEWAY_EMPTY_RESPONSE", ex.getErrorCode());
    }
}
