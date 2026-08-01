package com.developer.service.impl;

import com.developer.exception.AiGenerationException;
import com.developer.service.AiPromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 DW 侧组装好的请求体渲染成一条送给 AI gateway 的 prompt。
 *
 * <p>本类源自 {@code GenAI/build_prompt.md}（原 Activepieces flow 的 "Build Prompt" 步骤）。提示词的
 * 内置默认值是 {@code src/main/resources/ai-prompts/*.txt}；历史 flow 文件仅供迁移参考。GENERATION 资源已包含
 * BPMN 约束块，修改提示词通常只需修改 txt，不用改 Java。</p>
 *
 * <p>实际取哪一份由 {@link AiPromptTemplateService} 决定：库里 {@code dw_ai_prompt_templates} 有该相位的
 * 覆盖值就用覆盖值（AI 面板的提示词管理弹窗写入），否则用内置默认值。每轮对话现取，所以在 UI 改完立即生效。</p>
 *
 * <p>入参就是 {@link AiGenerationServiceImpl} 原先 POST 给 AP webhook 的那个 body，
 * 因此移植前后模型看到的文本完全一致。</p>
 */
@Slf4j
@Component
public class AiPromptBuilder {

    private static final String DEFAULT_PHASE = "REQUIREMENTS";
    private static final String DEFAULT_MODE = "NEW";

    private final AiPromptTemplateService promptTemplateService;

    private final ObjectMapper objectMapper;

    public AiPromptBuilder(ObjectMapper objectMapper, AiPromptTemplateService promptTemplateService) {
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 渲染 prompt。
     *
     * @param body {@code AiGenerationServiceImpl#buildAiRequestBody} 产出的请求体
     * @return 送给 gateway 的完整 prompt 文本
     */
    public String build(Map<String, Object> body) {
        Map<String, Object> src = body != null ? body : Map.of();

        String phase = normalize(src.get("phase"), DEFAULT_PHASE);
        if (!promptTemplateService.phases().contains(phase)) {
            phase = DEFAULT_PHASE;
        }
        String mode = normalize(src.get("mode"), DEFAULT_MODE);

        List<String> parts = new ArrayList<>();
        parts.add(promptTemplateService.resolve(phase));
        parts.add("\n\n========== Session context (system-provided; do not expose this divider in your reply) ==========");
        parts.add("Phase: " + phase + " | Mode: " + mode);

        Object fuId = src.get("functionUnitId");
        parts.add("Function unit ID (functionUnitId): " + (fuId != null ? String.valueOf(fuId) : "0"));

        parts.add("\n## Current function unit data");
        String context = asText(src.get("context"));
        if (!context.isEmpty()) {
            parts.add(context);
        } else {
            parts.add("(Brand-new function unit — no component data yet. Proceed with analysis/design/generation as usual.)");
        }

        parts.add("\n## Existing documents (existingDocuments)");
        String documents = asText(src.get("existingDocuments"));
        parts.add(!documents.isEmpty() ? documents : "(none)");

        String schemaMetadata = asText(src.get("schemaMetadata"));
        if (!schemaMetadata.isEmpty()) {
            parts.add("\n## Schema metadata (schemaMetadata — generated data must follow these enum values and structures)");
            parts.add(schemaMetadata);
        }

        parts.add("\n## Conversation history (conversationHistory, newest last)");
        List<String> history = renderHistory(src.get("conversationHistory"));
        if (!history.isEmpty()) {
            parts.addAll(history);
        } else {
            parts.add("(no history — this is the first turn)");
        }

        parts.add("\n## Current user message (message)");
        parts.add(asText(src.get("message")));
        parts.add("\n========== End of context — complete the current phase task based on the information above ==========");

        return String.join("\n", parts);
    }

    /** 历史消息渲染为 {@code [ROLE] content} 行；role 缺失时按原实现落到 user。 */
    private List<String> renderHistory(Object raw) {
        List<String> lines = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return lines;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> msg)) {
                continue;
            }
            String role = firstNonEmpty(asText(msg.get("role")), asText(msg.get("type")), "user");
            String content = firstNonEmpty(asText(msg.get("content")), asText(msg.get("text")), "");
            lines.add("[" + role.toUpperCase() + "] " + content);
        }
        return lines;
    }

    /** 字符串原样返回；其它对象序列化为 JSON（context/schemaMetadata 可能是 Map）。 */
    private String asText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AiGenerationException("AI_PROMPT_BUILD_FAILED",
                    "Failed to serialize prompt section of type " + value.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String normalize(Object value, String fallback) {
        String s = value != null ? String.valueOf(value).trim() : "";
        return s.isEmpty() ? fallback : s.toUpperCase();
    }

    private static String firstNonEmpty(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isEmpty()) {
                return c;
            }
        }
        return "";
    }
}
