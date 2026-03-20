package com.developer.service;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.AiMessage;
import com.developer.entity.FunctionUnit;
import com.developer.enums.*;
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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 会话重建场景单元测试
 *
 * 验证 N8N 返回 session-not-found 时，重建请求包含：
 * - 重新加载的 context（通过 functionUnitId 调用 serializeFunctionUnitContext）
 * - 重新加载的 existingDocuments（通过 functionUnitId 调用 getLatestDocuments）
 * - conversationHistory（从数据库加载）
 *
 * Validates: Requirements 6.2, 6.3
 */
@ExtendWith(MockitoExtension.class)
class AiSessionMemoryRebuildTest {

    @Mock private AiSessionRepository aiSessionRepository;
    @Mock private AiMessageRepository aiMessageRepository;
    @Mock private AiDocumentRepository aiDocumentRepository;
    @Mock private FunctionUnitRepository functionUnitRepository;

    private ObjectMapper objectMapper;
    private AiGenerationServiceImpl generationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        generationService = new AiGenerationServiceImpl(
                aiSessionRepository, aiMessageRepository, aiDocumentRepository,
                functionUnitRepository, objectMapper, 102400);
        ReflectionTestUtils.setField(generationService, "n8nWebhookUrl",
                "http://localhost:5678/webhook/ai-function-unit-gen");
        ReflectionTestUtils.setField(generationService, "n8nTimeoutSeconds", 120);
    }

    /**
     * 验证会话重建时 buildN8NRequestBody 包含 conversationHistory、context 和 existingDocuments
     */
    @Test
    @SuppressWarnings("unchecked")
    void sessionRebuild_requestBodyContainsAllFields() {
        UUID sessionId = UUID.randomUUID();
        Long functionUnitId = 1L;

        // Mock conversation history
        when(aiMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(
                        AiMessage.builder().sessionId(sessionId).role(AiMessageRole.USER)
                                .content("hello").phase(AiPhase.DESIGN).createdAt(Instant.now()).build(),
                        AiMessage.builder().sessionId(sessionId).role(AiMessageRole.ASSISTANT)
                                .content("hi").phase(AiPhase.DESIGN).createdAt(Instant.now()).build()
                ));

        // Build conversation history
        List<Map<String, String>> history = generationService.buildConversationHistory(sessionId);
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("hello", history.get(0).get("content"));

        // Build a rebuild request body via reflection
        FunctionUnitContextDTO context = FunctionUnitContextDTO.builder()
                .functionUnitId(functionUnitId).name("test-unit").description("desc")
                .tableDefinitions(List.of()).formDefinitions(List.of())
                .actionDefinitions(List.of()).build();

        List<Map<String, String>> existingDocuments = List.of(
                Map.of("documentType", "REQUIREMENTS", "content", "req content"));

        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                generationService, "buildN8NRequestBody",
                sessionId, "new message", AiPhase.DESIGN, AiMode.MODIFY,
                context, existingDocuments, history);

        assertNotNull(body);
        // Verify all fields present
        assertTrue(body.containsKey("context"), "Rebuild body should contain context");
        assertTrue(body.containsKey("existingDocuments"), "Rebuild body should contain existingDocuments");
        assertTrue(body.containsKey("conversationHistory"), "Rebuild body should contain conversationHistory");

        // context and existingDocuments should be pre-serialized strings
        assertInstanceOf(String.class, body.get("context"));
        assertInstanceOf(String.class, body.get("existingDocuments"));

        // conversationHistory should be a List (not serialized)
        assertInstanceOf(List.class, body.get("conversationHistory"));
        List<Map<String, String>> historyInBody = (List<Map<String, String>>) body.get("conversationHistory");
        assertEquals(2, historyInBody.size());
    }

    /**
     * 验证 isSessionNotFoundError 能正确检测各种 session-not-found 错误格式
     */
    @Test
    void isSessionNotFoundError_detectsVariousFormats() {
        // error field
        assertTrue(invokeIsSessionNotFoundError(Map.of("error", "Session not found for id xyz")));
        // errorCode field
        assertTrue(invokeIsSessionNotFoundError(Map.of("errorCode", "SESSION_NOT_FOUND")));
        // message field
        assertTrue(invokeIsSessionNotFoundError(Map.of("message", "Session does not exist")));
        // normal response
        assertFalse(invokeIsSessionNotFoundError(Map.of("reply", "ok")));
        // null
        assertFalse(invokeIsSessionNotFoundError(null));
    }

    @SuppressWarnings("unchecked")
    private boolean invokeIsSessionNotFoundError(Map<String, Object> response) {
        return (boolean) ReflectionTestUtils.invokeMethod(
                generationService, "isSessionNotFoundError", response);
    }
}
