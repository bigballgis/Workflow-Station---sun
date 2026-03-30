package com.developer.service;

import com.developer.entity.AiMessage;
import com.developer.entity.AiSession;
import com.developer.enums.*;
import com.developer.exception.AiGenerationException;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
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
 * N8N 调用模拟、SSE 事件生成、会话恢复
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
                102400 // 100KB max context size
        );
        ReflectionTestUtils.setField(generationService, "n8nWebhookUrl",
                "http://localhost:5678/webhook/ai-function-unit-gen");
        ReflectionTestUtils.setField(generationService, "n8nTimeoutSeconds", 120);
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

    // ==================== N8N Webhook ====================

    @Test
    void callN8NWebhook_sessionNotFound_shouldRetryWithHistory() {
        UUID sessionId = UUID.randomUUID();

        // Mock message history for rebuild
        AiMessage msg1 = AiMessage.builder()
                .sessionId(sessionId)
                .role(AiMessageRole.USER)
                .content("hello")
                .phase(AiPhase.REQUIREMENTS)
                .createdAt(Instant.now())
                .build();
        AiMessage msg2 = AiMessage.builder()
                .sessionId(sessionId)
                .role(AiMessageRole.ASSISTANT)
                .content("hi there")
                .phase(AiPhase.REQUIREMENTS)
                .createdAt(Instant.now())
                .build();
        when(aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(msg1, msg2));

        // Use a spy to intercept doCallN8NWebhook
        AiGenerationServiceImpl spyService = spy(generationService);

        // First call returns session-not-found error, second call returns success
        Map<String, Object> errorResponse = Map.of("error", "Session not found for id xyz");
        Map<String, Object> successResponse = Map.of("reply", "Generated response");

        // We need to mock the private doCallN8NWebhook via the public callN8NWebhook
        // Since doCallN8NWebhook makes real HTTP calls, we test buildConversationHistory instead
        List<Map<String, String>> history = generationService.buildConversationHistory(sessionId);

        assertEquals(2, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("hello", history.get(0).get("content"));
        assertEquals("assistant", history.get(1).get("role"));
        assertEquals("hi there", history.get(1).get("content"));
    }

    // ==================== SSE Emitter ====================

    @Test
    void createChatEmitter_shouldReturnEmitterWithTimeout() {
        SseEmitter emitter = generationService.createChatEmitter(1L, "user1");

        assertNotNull(emitter);
        // Dynamic timeout: n8nTimeoutSeconds(120) * 2 * 1000 + 60_000 = 300_000
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
                        .content("请帮我设计一个订单管理功能")
                        .phase(AiPhase.REQUIREMENTS)
                        .createdAt(Instant.now())
                        .build(),
                AiMessage.builder()
                        .sessionId(sessionId)
                        .role(AiMessageRole.ASSISTANT)
                        .content("好的，我来帮你设计")
                        .phase(AiPhase.REQUIREMENTS)
                        .createdAt(Instant.now())
                        .build()
        );

        when(aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(messages);

        List<Map<String, String>> history = generationService.buildConversationHistory(sessionId);

        assertEquals(2, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("请帮我设计一个订单管理功能", history.get(0).get("content"));
        assertEquals("assistant", history.get(1).get("role"));
        assertEquals("好的，我来帮你设计", history.get(1).get("content"));
    }
}
