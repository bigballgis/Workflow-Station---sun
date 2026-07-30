package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 AI gateway 的 OpenAI 兼容响应解析成 DW 内部契约
 * {@code {reply, document, documentType, phaseComplete, generatedData}}。
 *
 * <p>本类是 {@code GenAI/parse_response.md}（原 Activepieces flow 的 "Parse Response" 步骤）的 Java 移植，
 * 逐条保留了那边踩出来的规整规则：length/precision/scale/defaultValue 一律转字符串（
 * {@code AiWriteService} 按 String 解析）、configJson 字符串反序列化且非法即丢、formType TASK→MAIN、
 * BPMN 的 Base64 解码与确定性兜底 XML，以及"兜底 XML 无任务节点则拒绝"这条 fail-loud 规则。</p>
 *
 * <p>与 JS 版的两处有意差异（均为修正，不是行为漂移）：
 * <ul>
 *   <li>JS 的 {@code ```json?} 正则实际匹配的是 {@code ```jso} + 可选 n，纯 {@code ```} 围栏漏网；这里改为 {@code ```(?:json)?}。</li>
 *   <li>JS 在 choices 为空且 HTTP&lt;400 时会把整个响应信封 {@code JSON.stringify} 当成回答塞进对话；
 *       这里显式抛 {@code AI_GATEWAY_EMPTY_RESPONSE}。</li>
 * </ul></p>
 */
@Slf4j
@Component
public class AiResponseParser {

    private static final Pattern REQUIREMENTS_DOC =
            Pattern.compile("---REQUIREMENTS_DOC_START---(.*?)---REQUIREMENTS_DOC_END---", Pattern.DOTALL);
    private static final Pattern DESIGN_DOC =
            Pattern.compile("---DESIGN_DOC_START---(.*?)---DESIGN_DOC_END---", Pattern.DOTALL);
    private static final Pattern GENERATED_DATA =
            Pattern.compile("---GENERATED_DATA_START---(.*?)(?:---GENERATED_DATA_END---|$)", Pattern.DOTALL);
    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL);
    private static final Pattern BASE64_ONLY = Pattern.compile("^[A-Za-z0-9+/=\\s]+$");
    private static final Pattern BPMN_DEFINITIONS = Pattern.compile("<(?:bpmn:)?definitions(?:\\s|>)");
    private static final Pattern BPMN_PROCESS = Pattern.compile("<(?:bpmn:)?process(?:\\s|>)");
    private static final Pattern BPMN_START_EVENT = Pattern.compile("<(?:bpmn:)?startEvent(?:\\s|>)");
    private static final Pattern BPMN_END_EVENT = Pattern.compile("<(?:bpmn:)?endEvent(?:\\s|>)");
    private static final Pattern BPMN_WORK_NODE = Pattern.compile("<(?:bpmn:)?(?:userTask|serviceTask|task)(?:\\s|>)");
    private static final String BPMN_MODEL_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String PHASE_COMPLETE_MARKER = "---PHASE_COMPLETE---";
    private static final char BOM = '\uFEFF';

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param httpResult {@link AiGatewayClient#chat} 返回的 {@code {status, body}}
     * @return DW 内部契约 map（value 可为 null，故用 LinkedHashMap）
     */
    public Map<String, Object> parse(Map<String, Object> httpResult) {
        String reply = extractAssistantText(httpResult);

        String document = null;
        String documentType = null;

        Matcher req = REQUIREMENTS_DOC.matcher(reply);
        if (req.find()) {
            document = req.group(1).trim();
            documentType = "REQUIREMENTS";
        }
        Matcher design = DESIGN_DOC.matcher(reply);
        if (design.find()) {
            document = design.group(1).trim();
            documentType = "DESIGN";
        }

        boolean phaseComplete = reply.contains(PHASE_COMPLETE_MARKER);

        Map<String, Object> generatedData = null;
        Matcher gen = GENERATED_DATA.matcher(reply);
        if (gen.find()) {
            generatedData = parseGeneratedData(gen.group(1).trim());
        }
        if (generatedData != null) {
            normalizeFieldMetadata(generatedData);
            normalizeConfigJson(generatedData);
            normalizeBpmn(generatedData);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", stripMarkers(reply));
        result.put("document", document);
        result.put("documentType", documentType);
        result.put("phaseComplete", phaseComplete);
        result.put("generatedData", generatedData);
        return result;
    }

    // ==================== 响应信封 ====================

    @SuppressWarnings("unchecked")
    private String extractAssistantText(Map<String, Object> httpResult) {
        int status = httpResult.get("status") instanceof Number n ? n.intValue() : 0;
        Object bodyObj = httpResult.get("body");
        Map<String, Object> body = bodyObj instanceof Map ? (Map<String, Object>) bodyObj : Map.of();

        Object choicesObj = body.get("choices");
        List<Object> choices = choicesObj instanceof List ? (List<Object>) choicesObj : List.of();

        String content = null;
        if (!choices.isEmpty() && choices.get(0) instanceof Map<?, ?> first
                && first.get("message") instanceof Map<?, ?> message
                && message.get("content") instanceof String text) {
            content = text;
        }

        if (content != null && !content.isBlank()) {
            return content;
        }
        if (status >= 400) {
            throw new AiGenerationException("AI_GATEWAY_HTTP_ERROR",
                    "AI gateway request failed with HTTP " + status + errorDetail(body));
        }
        throw new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE",
                "AI gateway returned an empty assistant response (HTTP " + status + ")");
    }

    /** 从 gateway 错误体里挖出可读文案：先 message，再 error.message / error.code。 */
    private String errorDetail(Map<String, Object> body) {
        if (body.get("message") instanceof String m && !m.isBlank()) {
            return ": " + m;
        }
        if (body.get("error") instanceof Map<?, ?> error) {
            if (error.get("message") instanceof String em && !em.isBlank()) {
                return ": " + em;
            }
            if (error.get("code") instanceof String ec && !ec.isBlank()) {
                return ": " + ec;
            }
        }
        return "";
    }

    // ==================== generatedData ====================

    /** 三级解析：整段 JSON → ```json 围栏 → 首尾大括号切片。三级都失败返回 null（前端表现为无预览数据）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGeneratedData(String raw) {
        Map<String, Object> direct = tryReadObject(raw);
        if (direct != null) {
            return direct;
        }
        Matcher fence = JSON_FENCE.matcher(raw);
        if (fence.find()) {
            Map<String, Object> fenced = tryReadObject(fence.group(1).trim());
            if (fenced != null) {
                return fenced;
            }
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start != -1 && end > start) {
            Map<String, Object> sliced = tryReadObject(raw.substring(start, end + 1));
            if (sliced != null) {
                return sliced;
            }
        }
        log.warn("GENERATED_DATA block present but not parseable as JSON ({} chars)", raw.length());
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryReadObject(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * AiWriteService 按 String 解析字段的长度/精度/默认值元数据，
     * 而模型经常直接输出 JSON 数字——在进入预览/写入前统一转成字符串。
     */
    @SuppressWarnings("unchecked")
    private void normalizeFieldMetadata(Map<String, Object> generatedData) {
        if (!(generatedData.get("tableDefinitions") instanceof List<?> tables)) {
            return;
        }
        for (Object tableObj : tables) {
            if (!(tableObj instanceof Map<?, ?> table)
                    || !(table.get("fieldDefinitions") instanceof List<?> fields)) {
                continue;
            }
            for (Object fieldObj : fields) {
                if (!(fieldObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> field = (Map<String, Object>) fieldObj;
                for (String key : List.of("length", "precision", "scale")) {
                    Object value = field.get(key);
                    if (value != null && !(value instanceof String)) {
                        field.put(key, String.valueOf(value));
                    }
                }
                Object defaultValue = field.get("defaultValue");
                if (defaultValue != null && !(defaultValue instanceof String)) {
                    field.put("defaultValue", defaultValue instanceof Map || defaultValue instanceof List
                            ? writeJson(defaultValue)
                            : String.valueOf(defaultValue));
                }
            }
        }
    }

    /** configJson 允许模型以 JSON 字符串给出；非法或非对象一律丢成 null，避免下游 ClassCastException。 */
    @SuppressWarnings("unchecked")
    private void normalizeConfigJson(Map<String, Object> generatedData) {
        if (generatedData.get("formDefinitions") instanceof List<?> forms) {
            for (Object formObj : forms) {
                if (!(formObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> form = (Map<String, Object>) formObj;
                form.put("configJson", coerceConfigJson(form.get("configJson")));
                if ("TASK".equals(form.get("formType"))) {
                    form.put("formType", "MAIN");
                }
            }
        }
        if (generatedData.get("actionDefinitions") instanceof List<?> actions) {
            for (Object actionObj : actions) {
                if (!(actionObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> action = (Map<String, Object>) actionObj;
                action.put("configJson", coerceConfigJson(action.get("configJson")));
            }
        }
    }

    private Object coerceConfigJson(Object value) {
        Object resolved = value;
        if (resolved instanceof String s) {
            resolved = tryReadObject(s);
        }
        return resolved instanceof Map ? resolved : null;
    }

    // ==================== BPMN ====================

    /**
     * 平台校验器要的是裸 BPMN XML。模型偶尔给 Base64 或伪 BPMN：能解就解，
     * 结构不合法就换成确定性的 Start→End 骨架；但骨架没有任务节点，最后一步会显式拒绝——
     * 宁可让这一轮生成失败重来，也不要写进去一条跑不动的流程。
     */
    private void normalizeBpmn(Map<String, Object> generatedData) {
        if (!(generatedData.get("processDefinition") instanceof Map<?, ?> processDefinition)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> process = (Map<String, Object>) processDefinition;

        String xml = process.get("bpmnXml") instanceof String s ? s : null;
        if (xml != null) {
            xml = stripBom(xml).trim();
            if (!xml.isEmpty() && xml.charAt(0) != '<' && BASE64_ONLY.matcher(xml).matches()) {
                String decoded = tryDecodeBase64(xml);
                if (decoded != null && !decoded.isEmpty() && decoded.charAt(0) == '<') {
                    xml = decoded;
                }
            }
        }

        boolean structurallyValid = xml != null
                && xml.contains(BPMN_MODEL_NAMESPACE)
                && BPMN_DEFINITIONS.matcher(xml).find()
                && BPMN_PROCESS.matcher(xml).find()
                && BPMN_START_EVENT.matcher(xml).find()
                && BPMN_END_EVENT.matcher(xml).find();

        if (!structurallyValid) {
            String rawName = generatedData.get("name") instanceof String n && !n.isBlank()
                    ? n.trim() : "Generated Process";
            xml = buildFallbackBpmn(rawName);
        }

        if (!BPMN_WORK_NODE.matcher(xml).find()) {
            throw new AiGenerationException("AI_BPMN_NO_TASK_NODES",
                    "AI generated invalid BPMN with no task nodes. Regenerate the preview; "
                            + "empty Start-to-End fallback is rejected.");
        }
        process.put("bpmnXml", xml);
    }

    private static String buildFallbackBpmn(String rawName) {
        String processId = rawName.toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (processId.isEmpty()) {
            processId = "generated_process";
        }
        processId = processId + "_process";
        String processName = rawName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:bpmn=\"" + BPMN_MODEL_NAMESPACE + "\" "
                + "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" "
                + "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" "
                + "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" "
                + "id=\"Definitions_1\" targetNamespace=\"http://platform.local/bpmn\">\n"
                + "  <bpmn:process id=\"" + processId + "\" name=\"" + processName + "\" isExecutable=\"true\">\n"
                + "    <bpmn:startEvent id=\"StartEvent_1\" name=\"Start\"><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:startEvent>\n"
                + "    <bpmn:endEvent id=\"EndEvent_1\" name=\"End\"><bpmn:incoming>Flow_1</bpmn:incoming></bpmn:endEvent>\n"
                + "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"EndEvent_1\"/>\n"
                + "  </bpmn:process>\n"
                + "  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\"><bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"" + processId + "\">\n"
                + "    <bpmndi:BPMNShape id=\"StartEvent_1_di\" bpmnElement=\"StartEvent_1\"><dc:Bounds x=\"152\" y=\"102\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>\n"
                + "    <bpmndi:BPMNShape id=\"EndEvent_1_di\" bpmnElement=\"EndEvent_1\"><dc:Bounds x=\"302\" y=\"102\" width=\"36\" height=\"36\"/></bpmndi:BPMNShape>\n"
                + "    <bpmndi:BPMNEdge id=\"Flow_1_di\" bpmnElement=\"Flow_1\"><di:waypoint x=\"188\" y=\"120\"/><di:waypoint x=\"302\" y=\"120\"/></bpmndi:BPMNEdge>\n"
                + "  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>\n"
                + "</bpmn:definitions>";
    }

    private static String tryDecodeBase64(String xml) {
        try {
            byte[] decoded = Base64.getDecoder().decode(xml.replaceAll("\\s", ""));
            return stripBom(new String(decoded, StandardCharsets.UTF_8)).trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** BOM 写成转义序列：裸 U+FEFF 在源码里不可见，编辑器一保存就可能被吃掉。 */
    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == BOM ? s.substring(1) : s;
    }

    // ==================== 回复正文 ====================

    /** 对话气泡里不该出现协议标记，逐个剥掉后返回。 */
    private static String stripMarkers(String reply) {
        String cleaned = REQUIREMENTS_DOC.matcher(reply).replaceAll("");
        cleaned = DESIGN_DOC.matcher(cleaned).replaceAll("");
        cleaned = GENERATED_DATA.matcher(cleaned).replaceAll("");
        return cleaned.replace(PHASE_COMPLETE_MARKER, "").trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AiGenerationException("AI_RESPONSE_PARSE_FAILED",
                    "Failed to re-serialize a generated defaultValue: " + e.getMessage());
        }
    }
}
