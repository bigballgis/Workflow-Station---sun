package com.developer.component.impl;

import com.developer.dto.AiChatRequest;
import com.developer.dto.AiChatSseEvent;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.AiMessage;
import com.developer.entity.AiSession;
import com.developer.enums.*;
import com.developer.exception.AiGenerationException;
import com.developer.service.AiGenerationService;
import com.developer.service.AiLockService;
import com.developer.service.AiValidationService;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.service.AiWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiGenerationComponentImpl 单元测试
 *
 * 验证 chatStream 编排逻辑：
 * - 首条消息加载 context 和 existingDocuments
 * - 非首条消息也加载 context 和 existingDocuments（AI webhook systemMessage 每次渲染）
 * - 阶段切换时加载 context 和 existingDocuments
 * - serializeFunctionUnitContext 异常时发送 SSE error 事件
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
@ExtendWith(MockitoExtension.class)
class AiGenerationComponentImplTest {

    /** 每用户 AMToken：由 controller 从 X-AM-Token 头/cookie 取出后透传给编排层。 */
    private static final String AM_TOKEN = "am-token-for-test";

    @Mock private AiGenerationService aiGenerationService;
    @Mock private AiLockService aiLockService;
    @Mock private AiValidationService aiValidationService;
    @Mock private AiWriteService aiWriteService;
    @Mock private FunctionUnitWorkspaceAccessService functionUnitWorkspaceAccessService;

    private AiGenerationComponentImpl component;
    private UUID sessionUuid;
    private AiSession session;

    @BeforeEach
    void setUp() {
        // Use synchronous executor to make SSE orchestration deterministic in unit tests.
        Executor executor = Runnable::run;
        component = new AiGenerationComponentImpl(aiGenerationService, aiLockService, aiValidationService, aiWriteService,
                functionUnitWorkspaceAccessService, executor, new com.fasterxml.jackson.databind.ObjectMapper());
        sessionUuid = UUID.randomUUID();
        session = AiSession.builder()
                .sessionId(sessionUuid).functionUnitId(1L).userId("user1")
                .currentPhase(AiPhase.REQUIREMENTS).mode(AiMode.NEW).status(AiSessionStatus.ACTIVE).build();
    }

    /**
     * 首条消息 + NEW 模式：应加载 context 和 existingDocuments
     * Validates: Requirements 1.2
     */
    @Test
    void chatStream_firstMessage_newMode_loadsContextAndDocuments() throws Exception {
        when(aiGenerationService.createSession(anyLong(), anyString(), any())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER).content("test").phase(AiPhase.REQUIREMENTS).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString())).thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(1L, AiPhase.REQUIREMENTS, AiMode.NEW)).thenReturn(List.of());
        when(aiGenerationService.callAiModel(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any(), any()))
                .thenReturn(Map.of("reply", "ok"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(null).message("hello").phase(AiPhase.REQUIREMENTS).mode(AiMode.NEW).build();
        component.chatStream(request, "user1", AM_TOKEN);
        latch.await(5, TimeUnit.SECONDS);

        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, AiPhase.REQUIREMENTS, AiMode.NEW);
    }

    /**
     * 首条消息 + MODIFY 模式：应加载 context 和 existingDocuments
     * Validates: Requirements 1.1
     */
    @Test
    void chatStream_firstMessage_modifyMode_loadsContextAndDocuments() throws Exception {
        session.setMode(AiMode.MODIFY);
        when(aiGenerationService.createSession(anyLong(), anyString(), any())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER).content("test").phase(AiPhase.DESIGN).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString())).thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(1L, AiPhase.DESIGN, AiMode.MODIFY))
                .thenReturn(List.of(Map.of("documentType", "REQUIREMENTS", "content", "req doc")));
        when(aiGenerationService.callAiModel(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any(), any()))
                .thenReturn(Map.of("reply", "ok"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(null).message("hello").phase(AiPhase.DESIGN).mode(AiMode.MODIFY).build();
        component.chatStream(request, "user1", AM_TOKEN);
        latch.await(5, TimeUnit.SECONDS);

        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, AiPhase.DESIGN, AiMode.MODIFY);
    }

    /**
     * 阶段切换（sessionId 非 null，但 phase 不同）：应重新加载 context 和 existingDocuments
     * Validates: Requirements 1.1, 1.2 (phase transition)
     */
    @Test
    void chatStream_phaseTransition_reloadsContextAndDocuments() throws Exception {
        // Session is at REQUIREMENTS phase
        when(aiGenerationService.restoreSession(anyString())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER).content("test").phase(AiPhase.DESIGN).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString())).thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(1L, AiPhase.DESIGN, AiMode.NEW))
                .thenReturn(List.of(Map.of("documentType", "REQUIREMENTS", "content", "req doc")));
        when(aiGenerationService.callAiModel(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any(), any()))
                .thenReturn(Map.of("reply", "ok"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        // Request with DESIGN phase, but session.currentPhase is REQUIREMENTS → phase transition
        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(sessionUuid.toString()).message("start design").phase(AiPhase.DESIGN).mode(AiMode.NEW).build();
        component.chatStream(request, "user1", AM_TOKEN);
        latch.await(5, TimeUnit.SECONDS);

        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, AiPhase.DESIGN, AiMode.NEW);
    }

    /**
     * 非首条消息（sessionId 非 null，同阶段）：也应加载 context 和 existingDocuments
     * AI webhook systemMessage 每次请求都重新渲染，需要最新数据
     */
    @Test
    void chatStream_subsequentMessage_alsoLoadsContextAndDocuments() throws Exception {
        when(aiGenerationService.restoreSession(anyString())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER).content("test").phase(AiPhase.REQUIREMENTS).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString())).thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(1L, AiPhase.REQUIREMENTS, AiMode.NEW)).thenReturn(List.of());
        when(aiGenerationService.callAiModel(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any(), any()))
                .thenReturn(Map.of("reply", "ok"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(sessionUuid.toString()).message("follow up").phase(AiPhase.REQUIREMENTS).mode(AiMode.NEW).build();
        component.chatStream(request, "user1", AM_TOKEN);
        latch.await(5, TimeUnit.SECONDS);

        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, AiPhase.REQUIREMENTS, AiMode.NEW);
    }

    /**
     * serializeFunctionUnitContext 抛出异常时：降级为 null context，仍然调用 AI webhook
     * Validates: Requirements 1.3 (graceful degradation)
     */
    @Test
    void chatStream_serializeContextThrows_degradesGracefully() throws Exception {
        when(aiGenerationService.createSession(anyLong(), anyString(), any())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER).content("test").phase(AiPhase.REQUIREMENTS).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString())).thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenThrow(new AiGenerationException("AI_FUNCTION_UNIT_NOT_FOUND", "功能单元不存在"));
        // AI webhook should still be called with null context and empty documents
        when(aiGenerationService.callAiModel(any(), anyString(), any(), any(), isNull(), anyLong(), anyList(), any(), any()))
                .thenReturn(Map.of("reply", "ok"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; }).when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(null).message("hello").phase(AiPhase.REQUIREMENTS).mode(AiMode.NEW).build();
        component.chatStream(request, "user1", AM_TOKEN);
        latch.await(5, TimeUnit.SECONDS);

        // Verify AI webhook was still called (graceful degradation, not failure)
        verify(aiGenerationService).callAiModel(any(), eq("hello"), eq(AiPhase.REQUIREMENTS), eq(AiMode.NEW),
                isNull(), eq(1L), eq(List.of()), isNull(), eq(AM_TOKEN));
    }

    @Test
    void normalizeTableRelations_rewritesManyToOneAsSwappedOneToMany() {
        Map<String, Object> manyToOne = new LinkedHashMap<>();
        manyToOne.put("sourceTableName", "Package");
        manyToOne.put("sourceFieldName", "shipment_id");
        manyToOne.put("relationType", "MANY_TO_ONE");
        manyToOne.put("targetTableName", "ExpressShipment");
        manyToOne.put("targetFieldName", "id");
        Map<String, Object> untouched = new LinkedHashMap<>();
        untouched.put("sourceTableName", "ExpressShipment");
        untouched.put("sourceFieldName", "id");
        untouched.put("relationType", "ONE_TO_MANY");
        untouched.put("targetTableName", "Package");
        untouched.put("targetFieldName", "shipment_id");
        List<Map<String, Object>> relations = new ArrayList<>(Arrays.asList(manyToOne, null, untouched));

        AiGenerationComponentImpl.normalizeTableRelations(relations);

        assertEquals("ONE_TO_MANY", manyToOne.get("relationType"));
        assertEquals("ExpressShipment", manyToOne.get("sourceTableName"));
        assertEquals("id", manyToOne.get("sourceFieldName"));
        assertEquals("Package", manyToOne.get("targetTableName"));
        assertEquals("shipment_id", manyToOne.get("targetFieldName"));
        // Valid entries are untouched
        assertEquals("ONE_TO_MANY", untouched.get("relationType"));
        assertEquals("ExpressShipment", untouched.get("sourceTableName"));
        // Null list is a no-op (no exception)
        AiGenerationComponentImpl.normalizeTableRelations(null);
    }

    @Test
    void normalizeCrossFieldRules_defaultsMissingTargetFieldToLastRuleField() {
        Map<String, Object> missingTarget = new LinkedHashMap<>();
        missingTarget.put("fields", Arrays.asList("start_date", "end_date"));
        missingTarget.put("operator", "date-after");
        missingTarget.put("message", "End date must be after start date");
        Map<String, Object> blankTarget = new LinkedHashMap<>();
        blankTarget.put("fields", Arrays.asList("min_amount", "max_amount"));
        blankTarget.put("targetField", " ");
        Map<String, Object> explicitTarget = new LinkedHashMap<>();
        explicitTarget.put("fields", Arrays.asList("a", "b"));
        explicitTarget.put("targetField", "a");
        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("crossFieldRules", new ArrayList<>(Arrays.asList(missingTarget, blankTarget, explicitTarget, null)));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("formName", "F1");
        form.put("configJson", configJson);
        Map<String, Object> formWithoutConfig = new LinkedHashMap<>();
        formWithoutConfig.put("formName", "F2");
        formWithoutConfig.put("configJson", null);

        AiGenerationComponentImpl.normalizeCrossFieldRules(
                new ArrayList<>(Arrays.asList(form, formWithoutConfig, null)));

        assertEquals("end_date", missingTarget.get("targetField"));
        assertEquals("max_amount", blankTarget.get("targetField"));
        // Explicit values are untouched
        assertEquals("a", explicitTarget.get("targetField"));
        // Null list is a no-op (no exception)
        AiGenerationComponentImpl.normalizeCrossFieldRules(null);
    }
}
