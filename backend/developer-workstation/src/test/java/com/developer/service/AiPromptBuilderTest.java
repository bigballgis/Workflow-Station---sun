package com.developer.service;

import com.developer.repository.AiPromptTemplateRepository;
import com.developer.service.impl.AiPromptBuilder;
import com.developer.service.impl.AiPromptTemplateServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiPromptBuilder 单元测试 —— 对照 {@code GenAI/build_prompt.md} 的移植保真度。
 *
 * <p>重点盯四件事：三段系统提示词都能从 classpath 加载、提示词与会话上下文分别落到 system / user 两段、
 * 上下文分节顺序与占位文案不走样、未知/缺失的 phase 落回 REQUIREMENTS。</p>
 *
 * <p>提示词覆盖表这里刻意 mock 成空（没有任何覆盖行），因此走的正是内置默认值那条路径。</p>
 */
class AiPromptBuilderTest {

    private final AiPromptBuilder builder = new AiPromptBuilder(new ObjectMapper(), builtInOnlyTemplates());

    /** 覆盖表为空的 AiPromptTemplateService：resolve() 返回 classpath 里的内置提示词。 */
    private static AiPromptTemplateServiceImpl builtInOnlyTemplates() {
        AiPromptTemplateRepository repository = mock(AiPromptTemplateRepository.class);
        when(repository.findByPhase(anyString())).thenReturn(Optional.empty());
        return new AiPromptTemplateServiceImpl(repository);
    }

    @Test
    void build_generationPhase_carriesSystemPromptAndBpmnConstraints() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", "GENERATION");
        body.put("mode", "NEW");
        body.put("message", "generate it");

        AiPromptBuilder.RenderedPrompt prompt = builder.build(body);
        String system = prompt.system();

        assertTrue(system.startsWith("You are a function-unit code generation assistant."));
        assertTrue(system.contains("---GENERATED_DATA_START---"));
        assertTrue(system.contains("\"fieldDefinitions\""), "字段名速查表必须在场，否则模型会输出 fields");
        assertTrue(system.contains("\"decisionDefinitions\""));
        assertTrue(system.contains("\"tableRelations\""));
        assertTrue(system.contains("\"explanations\""));
        assertTrue(system.contains("DRAFT|CANCEL"), "动作枚举必须包含当前平台的 DRAFT 类型");
        assertFalse(system.contains("N8N_ACTION"), "已弃用的 n8n 动作不得继续暴露给 AI");
        assertTrue(system.contains("DECISION_TABLE"));
        assertTrue(system.contains("\"stageIds\""));
        assertTrue(system.contains("ROLLBACK is a Flowable state-change action"));
        assertTrue(system.contains("custom extension property named assigneeType"));
        assertTrue(system.contains("Do not generate duplicate sequence flows"));
        assertTrue(system.contains("Every bpmn:userTask must be bound by exactly one TASK form"));
        assertTrue(system.contains("BPMN XML REQUIREMENTS (MANDATORY)"),
                "GENERATION 阶段必须无条件追加 BPMN 约束块");
        assertTrue(prompt.user().contains("Phase: GENERATION | Mode: NEW"));
    }

    /**
     * 相位提示词进 system，会话上下文进 user，两边不互相渗漏。
     *
     * <p>渗漏的后果是隐性的：提示词若混进 user 段，就和用户可控文本同权重，
     * 平台约束被一句"忽略上面的规则"顶掉的门槛会低很多。</p>
     */
    @Test
    void build_splitsPhasePromptIntoSystemAndContextIntoUser() {
        AiPromptBuilder.RenderedPrompt prompt = builder.build(
                Map.of("phase", "DESIGN", "mode", "MODIFY", "message", "now change it"));

        assertTrue(prompt.system().startsWith("You are a function-unit technical design assistant."));
        assertFalse(prompt.system().contains("========== Session context"),
                "会话上下文不得混进 system 段");
        assertFalse(prompt.system().contains("now change it"), "用户消息不得混进 system 段");

        assertTrue(prompt.user().startsWith("========== Session context"));
        assertFalse(prompt.user().contains("You are a function-unit technical design assistant."),
                "相位提示词不得重复出现在 user 段");
        assertTrue(prompt.user().contains("Phase: DESIGN | Mode: MODIFY"));
        assertTrue(prompt.user().contains("now change it"));
    }

    @Test
    void build_requirementsAndDesignPromptsAreDistinct() {
        String requirements = builder.build(Map.of("phase", "REQUIREMENTS", "message", "x")).system();
        String design = builder.build(Map.of("phase", "DESIGN", "message", "x")).system();

        assertTrue(requirements.startsWith("You are a function-unit requirements analyst."));
        assertTrue(requirements.contains("Form Stage Binding"));
        assertTrue(requirements.contains("every Start-to-End process path"));
        assertTrue(design.startsWith("You are a function-unit technical design assistant."));
        assertTrue(design.contains("---DESIGN_DOC_START---"));
        assertTrue(design.contains("Process Node Matrix"));
        assertTrue(design.contains("Sequence Flow Matrix"));
    }

    /** 相位逐轮重取：同一个 builder 连着跑三轮，system 段必须跟着 phase 换，不能粘在第一轮上。 */
    @Test
    void build_consecutiveTurns_switchSystemPromptWithThePhase() {
        String first = builder.build(Map.of("phase", "REQUIREMENTS", "message", "x")).system();
        String second = builder.build(Map.of("phase", "DESIGN", "message", "x")).system();
        String third = builder.build(Map.of("phase", "GENERATION", "message", "x")).system();

        assertTrue(first.startsWith("You are a function-unit requirements analyst."));
        assertTrue(second.startsWith("You are a function-unit technical design assistant."));
        assertTrue(third.startsWith("You are a function-unit code generation assistant."));
    }

    @Test
    void build_unknownOrMissingPhase_fallsBackToRequirements() {
        AiPromptBuilder.RenderedPrompt unknown = builder.build(Map.of("phase", "NOT_A_PHASE", "message", "x"));
        AiPromptBuilder.RenderedPrompt missing = builder.build(Map.of("message", "x"));

        assertTrue(unknown.system().startsWith("You are a function-unit requirements analyst."));
        assertTrue(missing.user().contains("Phase: REQUIREMENTS | Mode: NEW"));
    }

    @Test
    void build_emptyContextAndDocuments_useBrandNewPlaceholders() {
        String prompt = builder.build(Map.of("phase", "REQUIREMENTS", "message", "hi")).user();

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

        String prompt = builder.build(body).user();

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
