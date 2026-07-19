package com.developer.service;

import com.developer.dto.AiChatSseEvent;
import com.developer.repository.AiDocumentRepository;
import com.developer.repository.AiMessageRepository;
import com.developer.repository.AiSessionRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.service.impl.AiGenerationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for SSE emitter management in AiGenerationServiceImpl.
 */
class AiSseEmitterManagementTest {

    private AiGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiGenerationServiceImpl(
                mock(AiSessionRepository.class),
                mock(AiMessageRepository.class),
                mock(AiDocumentRepository.class),
                mock(FunctionUnitRepository.class),
                new ObjectMapper(),
                102400);
        ReflectionTestUtils.setField(service, "aiWebhookTimeoutSeconds", 120);
    }

    @Test
    void createChatEmitter_shouldReturnNonNullEmitter() {
        SseEmitter emitter = service.createChatEmitter(1L, "user1");
        assertThat(emitter).isNotNull();
        // Dynamic timeout: aiWebhookTimeoutSeconds * 2 * 1000 + 60_000 = 120 * 2 * 1000 + 60_000 = 300_000
        assertThat(emitter.getTimeout()).isEqualTo(300_000L);
    }

    @Test
    void createEventEmitter_shouldReturnNonNullEmitter() {
        SseEmitter emitter = service.createEventEmitter(1L, "user1");
        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(300_000L);
    }

    @Test
    void completeChatEmitter_shouldCompleteAndRemoveEmitter() {
        service.createChatEmitter(1L, "user1");
        // Should not throw
        service.completeChatEmitter(1L, "user1");
        // Sending after completion should log warning but not throw
        service.sendChatEvent(1L, "user1",
                AiChatSseEvent.builder().eventType("token").data("test").build());
    }

    @Test
    void removeChatEmitter_shouldRemoveWithoutCompleting() {
        service.createChatEmitter(1L, "user1");
        service.removeChatEmitter(1L, "user1");
        // Sending after removal should log warning but not throw
        service.sendChatEvent(1L, "user1",
                AiChatSseEvent.builder().eventType("token").data("test").build());
    }

    @Test
    void removeEventEmitter_shouldRemoveSpecificUser() {
        service.createEventEmitter(1L, "user1");
        service.createEventEmitter(1L, "user2");
        service.removeEventEmitter(1L, "user1");
        // Sending should still reach user2 but not user1
        // No exception expected
        service.sendEventNotification(1L,
                AiChatSseEvent.builder().eventType("write_success").data("ok").build());
    }

    @Test
    void sendEventNotification_withNoEmitters_shouldNotThrow() {
        // No emitters registered for functionUnitId=999
        service.sendEventNotification(999L,
                AiChatSseEvent.builder().eventType("force_unlock_request").data("test").build());
    }

    @Test
    void completeChatEmitter_withNoEmitter_shouldNotThrow() {
        // No emitter registered
        service.completeChatEmitter(999L, "nonexistent");
    }

    @Test
    void removeEventEmitter_withNoEmitter_shouldNotThrow() {
        // No emitter registered
        service.removeEventEmitter(999L, "nonexistent");
    }

    @Test
    void createMultipleChatEmitters_forDifferentUsers_shouldBeIndependent() {
        SseEmitter emitter1 = service.createChatEmitter(1L, "user1");
        SseEmitter emitter2 = service.createChatEmitter(1L, "user2");
        assertThat(emitter1).isNotSameAs(emitter2);

        // Removing one should not affect the other
        service.removeChatEmitter(1L, "user1");
        // user2's emitter should still be reachable (no exception)
        service.sendChatEvent(1L, "user2",
                AiChatSseEvent.builder().eventType("done").data("{}").build());
    }

    @Test
    void createChatEmitter_replacesExistingEmitter() {
        SseEmitter first = service.createChatEmitter(1L, "user1");
        SseEmitter second = service.createChatEmitter(1L, "user1");
        assertThat(second).isNotSameAs(first);
    }
}
