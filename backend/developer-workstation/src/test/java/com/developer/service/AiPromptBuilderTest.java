package com.developer.service;

import com.developer.service.impl.AiPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiPromptBuilder 单元测试 —— 对照 {@code GenAI/build_prompt.md} 的移植保真度。
 *
 * <p>重点盯三件事：三段系统提示词都能从 classpath 加载、上下文分节顺序与占位文案不走样、
 * 未知/缺失的 phase 落回 REQUIREMENTS。</p>
 */
class AiPromptBuilderTest {

    private final AiPromptBuilder builder = new AiPromptBuilder(new ObjectMapper());

    @Test
    void build_generationPhase_carriesSystemPromptAndBpmnConstraints() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", "GENERATION");
        body.put("mode", "NEW");
        body.put("message", "generate it");

        String prompt = builder.build(body);

        assertTrue(prompt.startsWith("You are a function-unit code generation assistant."));
        assertTrue(prompt.contains("---GENERATED_DATA_START---"));
        assertTrue(prompt.contains("\"fieldDefinitions\""), "字段名速查表必须在场，否则模型会输出 fields");
        assertTrue(prompt.contains("BPMN XML REQUIREMENTS (MANDATORY)"),
                "GENERATION 阶段必须无条件追加 BPMN 约束块");
        assertTrue(prompt.contains("Phase: GENERATION | Mode: NEW"));
    }

    @Test
    void build_requirementsAndDesignPromptsAreDistinct() {
        String requirements = builder.build(Map.of("phase", "REQUIREMENTS", "message", "x"));
        String design = builder.build(Map.of("phase", "DESIGN", "message", "x"));

        assertTrue(requirements.startsWith("You are a function-unit requirements analyst."));
        assertTrue(design.startsWith("You are a function-unit technical design assistant."));
        assertTrue(design.contains("---DESIGN_DOC_START---"));
    }

    @Test
    void build_unknownOrMissingPhase_fallsBackToRequirements() {
        String unknown = builder.build(Map.of("phase", "NOT_A_PHASE", "message", "x"));
        String missing = builder.build(Map.of("message", "x"));

        assertTrue(unknown.startsWith("You are a function-unit requirements analyst."));
        assertTrue(missing.contains("Phase: REQUIREMENTS | Mode: NEW"));
    }

    @Test
    void build_emptyContextAndDocuments_useBrandNewPlaceholders() {
        String prompt = builder.build(Map.of("phase", "REQUIREMENTS", "message", "hi"));

        assertTrue(prompt.contains("Function unit ID (functionUnitId): 0"));
        assertTrue(prompt.contains("(Brand-new function unit — no component data yet."));
        assertTrue(prompt.contains("## Existing documents (existingDocuments)\n(none)"));
        assertTrue(prompt.contains("(no history — this is the first turn)"));
        assertFalse(prompt.contains("## Schema metadata"), "未提供 schemaMetadata 时不应出现该分节");
    }

    @Test
    void build_populatedBody_rendersEverySectionInOrder() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", "DESIGN");
        body.put("mode", "MODIFY");
        body.put("functionUnitId", 50011L);
        body.put("context", "{\"name\":\"Order\"}");
        body.put("existingDocuments", "=== REQUIREMENTS ===\nsome doc");
        body.put("schemaMetadata", Map.of("formTypes", List.of("MAIN", "SUB")));
        body.put("conversationHistory", List.of(
                Map.of("role", "user", "content", "first ask"),
                Map.of("role", "assistant", "content", "first answer")));
        body.put("message", "now change it");

        String prompt = builder.build(body);

        assertTrue(prompt.contains("Function unit ID (functionUnitId): 50011"));
        assertTrue(prompt.contains("{\"name\":\"Order\"}"));
        assertTrue(prompt.contains("=== REQUIREMENTS ===\nsome doc"));
        assertTrue(prompt.contains("\"formTypes\":[\"MAIN\",\"SUB\"]"),
                "schemaMetadata 若是 Map 必须序列化成 JSON，而不是 toString");
        assertTrue(prompt.contains("[USER] first ask"));
        assertTrue(prompt.contains("[ASSISTANT] first answer"));
        assertTrue(prompt.endsWith("========== End of context — complete the current phase task based on the information above =========="));

        assertTrue(prompt.indexOf("## Current function unit data") < prompt.indexOf("## Existing documents"));
        assertTrue(prompt.indexOf("## Existing documents") < prompt.indexOf("## Schema metadata"));
        assertTrue(prompt.indexOf("## Schema metadata") < prompt.indexOf("## Conversation history"));
        assertTrue(prompt.indexOf("## Conversation history") < prompt.indexOf("## Current user message"));
    }
}
