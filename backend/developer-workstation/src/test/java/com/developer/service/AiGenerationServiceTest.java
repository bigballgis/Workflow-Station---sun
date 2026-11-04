package com.developer.service;

import com.developer.entity.AiMessage;
import com.developer.entity.AiSession;
import com.developer.enums.*;
import com.developer.exception.AiGenerationException;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGatewayClient;
import com.developer.service.impl.AiGatewayClient;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.developer.service.impl.AiPromptBuilder;
import com.developer.service.impl.AiResponseParser;
import com.developer.service.impl.AiPromptBuilder;
import com.developer.service.impl.AiResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiGenerationService 单元测试
 * 会话管理、SSE 事件生成、对话历史组装
 */
@ExtendWith(MockitoExtension.class)
class AiGenerationServiceTest {

    @Mock
    private AiSessionRepository aiSessionRepository;

    @Mock
    private AiMessageRepository aiMessageRepository;

    @Mock
    private AiDocumentRepository aiDocumentRepository;

    @Mock
    private FunctionUnitRepository functionUnitRepository;

    @Mock
    private AiPromptBuilder aiPromptBuilder;

    @Mock
    private AiGatewayClient aiGatewayClient;

    @Mock
    private AiResponseParser aiResponseParser;

    @Mock
    private com.developer.service.impl.AiTableNameAvailability tableNameAvailability;

    private ObjectMapper objectMapper;

    private AiGenerationServiceImpl generationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        generationService = new AiGenerationServiceImpl(
                aiSessionRepository,
                aiMessageRepository,
                aiDocumentRepository,
                functionUnitRepository,
                objectMapper,
                aiPromptBuilder,
                aiGatewayClient,
                aiResponseParser,
                102400 // 100KB max context size
        );
        ReflectionTestUtils.setField(generationService, "aiCallTimeoutSeconds", 120);
        ReflectionTestUtils.setField(generationService, "tableNameAvailability", tableNameAvailability);
    }

    // ==================== Session Management ====================

    @Test
    void createSession_shouldCreateWithCorrectFields() {
        when(aiSessionRepository.save(any(AiSession.class))).thenAnswer(inv -> {
            AiSession s = inv.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(Instant.now());
            return s;
        });

        AiSession session = generationService.createSession(100L, "user1", AiMode.NEW);

        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals(100L, session.getFunctionUnitId());
        assertEquals("user1", session.getUserId());
        assertEquals(AiPhase.REQUIREMENTS, session.getCurrentPhase());
        assertEquals(AiMode.NEW, session.getMode());
        assertEquals(AiSessionStatus.ACTIVE, session.getStatus());

        ArgumentCaptor<AiSession> captor = ArgumentCaptor.forClass(AiSession.class);
        verify(aiSessionRepository).save(captor.capture());
        assertEquals(AiPhase.REQUIREMENTS, captor.getValue().getCurrentPhase());
    }

    @Test
    void restoreSession_notFound_shouldThrow() {
        UUID sessionUuid = UUID.randomUUID();
        when(aiSessionRepository.findBySessionId(sessionUuid)).thenReturn(Optional.empty());

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> generationService.restoreSession(sessionUuid.toString()));

        assertEquals("AI_SESSION_NOT_FOUND", ex.getErrorCode());
    }

    // ==================== SSE Emitter ====================

    @Test
    void createChatEmitter_shouldReturnEmitterWithTimeout() {
        SseEmitter emitter = generationService.createChatEmitter(1L, "user1");

        assertNotNull(emitter);
        // Dynamic timeout: aiCallTimeoutSeconds(120) * 2 * 1000 + 60_000 = 300_000
        assertEquals(300_000L, emitter.getTimeout());
    }

    // ==================== Conversation History ====================

    @Test
    void buildConversationHistory_shouldMapMessages() {
        UUID sessionId = UUID.randomUUID();

        List<AiMessage> messages = List.of(
                AiMessage.builder()
                        .sessionId(sessionId)
                        .role(AiMessageRole.USER)
                        .content("Please help me design an order management function")
                        .phase(AiPhase.REQUIREMENTS)
                        .createdAt(Instant.now())
                        .build(),
                AiMessage.builder()
                        .sessionId(sessionId)
                        .role(AiMessageRole.ASSISTANT)
                        .content("Sure, let me help you design it")
                        .phase(AiPhase.REQUIREMENTS)
                        .createdAt(Instant.now())
                        .build()
        );

        when(aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(messages);

        List<Map<String, String>> history = generationService.buildConversationHistory(sessionId);

        assertEquals(2, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("Please help me design an order management function", history.get(0).get("content"));
        assertEquals("assistant", history.get(1).get("role"));
        assertEquals("Sure, let me help you design it", history.get(1).get("content"));
    }

    // ==================== 语义校验失败后的定向重生成 ====================

    private Map<String, Object> callGeneration(String message) {
        return generationService.callAiModel(UUID.randomUUID(), message, AiPhase.GENERATION, AiMode.NEW,
                null, 87L, List.of(), "ALL", "token");
    }

    @Test
    void callAiModel_whenPlatformValidationRejectsOutput_regeneratesOnceWithTheViolation() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("status", 200));
        when(aiResponseParser.parse(any()))
                .thenThrow(new AiGenerationException("AI_ACTION_STAGE_BINDING_INVALID",
                        "action 'submit' references unknown userTask 'bpmn_start_event_1'"))
                .thenReturn(Map.of("reply", "fixed"));

        Map<String, Object> result = callGeneration("Generate it");

        assertEquals("fixed", result.get("reply"));

        ArgumentCaptor<Map<String, Object>> bodies = ArgumentCaptor.forClass(Map.class);
        verify(aiPromptBuilder, times(2)).build(bodies.capture());
        String repairMessage = (String) bodies.getAllValues().get(1).get("message");
        assertTrue(repairMessage.startsWith("Generate it"), repairMessage);
        assertTrue(repairMessage.contains("AI_ACTION_STAGE_BINDING_INVALID"), repairMessage);
        assertTrue(repairMessage.contains("bpmn_start_event_1"), repairMessage);
        // 授权模型推翻违规的 DESIGN 文档，否则它只会在两个冲突约束之间换一种折中
        assertTrue(repairMessage.contains("the platform rule wins"), repairMessage);
    }

    @Test
    void callAiModel_whenRepairPassAlsoFails_surfacesTheSecondViolationWithoutFurtherRetries() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("status", 200));
        when(aiResponseParser.parse(any()))
                .thenThrow(new AiGenerationException("AI_DESIGN_SELF_LOOP", "self-loop on 'verify'"))
                .thenThrow(new AiGenerationException("AI_BPMN_DISCONNECTED_NODES", "orphan gateway"));

        AiGenerationException ex = assertThrows(AiGenerationException.class, () -> callGeneration("Design it"));

        assertEquals("AI_BPMN_DISCONNECTED_NODES", ex.getErrorCode());
        verify(aiPromptBuilder, times(2)).build(any());
    }

    /** 纯模型侧失败(空回答/4xx)重发同一 prompt 没有意义，仍然一次就抛。 */
    @Test
    void callAiModel_whenGatewayReturnsEmptyResponse_isNotRegenerated() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("status", 200));
        when(aiResponseParser.parse(any()))
                .thenThrow(new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE", "empty"));

        AiGenerationException ex = assertThrows(AiGenerationException.class, () -> callGeneration("Generate it"));

        assertEquals("AI_GATEWAY_EMPTY_RESPONSE", ex.getErrorCode());
        verify(aiPromptBuilder, times(1)).build(any());
    }

    /** 表名全平台唯一：会写表的范围里被别的 FU 占用的表名，同样带原话定向重生成一次 */
    @Test
    void callAiModel_whenATableNameIsTakenByAnotherFunctionUnit_regeneratesOnceWithTheViolation() {
        Map<String, Object> taken = Map.of("tableDefinitions", List.of(Map.of("tableName", "leave_request")));
        Map<String, Object> renamed = Map.of("tableDefinitions", List.of(Map.of("tableName", "hr_leave_request")));
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("status", 200));
        when(aiResponseParser.parse(any()))
                .thenReturn(Map.of("reply", "v1", "generatedData", taken))
                .thenReturn(Map.of("reply", "v2", "generatedData", renamed));
        doThrow(new AiGenerationException("AI_TABLE_NAME_TAKEN", "Table name(s) [leave_request] are already used"))
                .when(tableNameAvailability).requireAvailable(87L, taken);

        Map<String, Object> result = callGeneration("Generate it");

        assertEquals("v2", result.get("reply"));
        ArgumentCaptor<Map<String, Object>> bodies = ArgumentCaptor.forClass(Map.class);
        verify(aiPromptBuilder, times(2)).build(bodies.capture());
        String repairMessage = (String) bodies.getAllValues().get(1).get("message");
        assertTrue(repairMessage.contains("Violation code: AI_TABLE_NAME_TAKEN"), repairMessage);
        assertTrue(repairMessage.contains("[leave_request]"), repairMessage);
    }

    /** AI Generate 的多选 scope 是逗号拼接（如 TABLES,FORMS），其 Apply 照写回传的表定义：同样要查 */
    @Test
    void callAiModel_withACommaJoinedScope_stillChecksTableNames() {
        Map<String, Object> data = Map.of("tableDefinitions", List.of(Map.of("tableName", "leave_request")));
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("status", 200));
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "ok", "generatedData", data));

        generationService.callAiModel(UUID.randomUUID(), "m", AiPhase.GENERATION, AiMode.MODIFY,
                null, 87L, List.of(), "TABLES,FORMS", "token");

        verify(tableNameAvailability).requireAvailable(87L, data);
    }
}
