package com.developer.controller;

import com.developer.component.AiGenerationComponent;
import com.developer.dto.*;
import com.developer.enums.AiMessageRole;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import com.developer.exception.AiExceptionHandler;
import com.developer.exception.AiLockConflictException;
import com.developer.exception.AiValidationFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit-level controller tests for AiGenerationController using standalone MockMvc.
 * Uses MockitoExtension with manual MockMvc setup to avoid full Spring context loading.
 */
@ExtendWith(MockitoExtension.class)
class AiGenerationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiGenerationComponent aiGenerationComponent;

    /** 控制器构造参数之一；缺了它 @InjectMocks 传 null，未认证分支上会 NPE 而不是抛业务异常。 */
    @Mock
    private I18nService i18nService;

    @InjectMocks
    private AiGenerationController aiGenerationController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(aiGenerationController)
                .setControllerAdvice(new AiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        // 控制器取当前用户走 SecurityContextUtils（只认 UserPrincipal），下面各用例仍在发的
        // X-User-Id 头早就不是身份来源了。standalone MockMvc 不跑安全过滤器，这里手工种上。
        UserPrincipal principal = UserPrincipal.builder()
                .userId("user1")
                .username("user1")
                .roles(List.of("DEVELOPER"))
                .permissions(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatStream_shouldReturnSseEmitter() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L)
                .message("hello")
                .phase(AiPhase.REQUIREMENTS)
                .mode(AiMode.NEW)
                .build();

        when(aiGenerationComponent.chatStream(any(AiChatRequest.class), anyString(), anyString()))
                .thenReturn(new SseEmitter(120_000L));

        mockMvc.perform(post("/ai-generation/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "user1")
                        .header("X-AM-Token", "am-token-for-test")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // AMToken 必须原样透传到编排层——丢了它 gateway 调用就没有凭证。
        verify(aiGenerationComponent).chatStream(any(AiChatRequest.class), anyString(), eq("am-token-for-test"));
    }

    @Test
    void eventStream_shouldReturnSseEmitter() throws Exception {
        when(aiGenerationComponent.registerEventEmitter(eq(1L), anyString()))
                .thenReturn(new SseEmitter(300_000L));

        mockMvc.perform(get("/ai-generation/events/1")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isOk());
    }

    @Test
    void acquireLock_shouldReturnLockInfo() throws Exception {
        LockInfoResponse lockInfo = LockInfoResponse.builder()
                .functionUnitId(1L)
                .userId("user1")
                .userName("Test User")
                .lockedAt(Instant.now())
                .locked(true)
                .build();

        when(aiGenerationComponent.acquireLock(eq(1L), anyString()))
                .thenReturn(lockInfo);

        mockMvc.perform(post("/ai-generation/lock/1")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.functionUnitId").value(1))
                .andExpect(jsonPath("$.data.userId").value("user1"))
                .andExpect(jsonPath("$.data.locked").value(true));
    }

    @Test
    void releaseLock_shouldReturn200() throws Exception {
        doNothing().when(aiGenerationComponent).releaseLock(eq(1L), anyString());

        mockMvc.perform(delete("/ai-generation/lock/1")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void requestForceUnlock_shouldReturn200() throws Exception {
        doNothing().when(aiGenerationComponent).requestForceUnlock(eq(1L), anyString());

        mockMvc.perform(post("/ai-generation/lock/1/force-unlock-request")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void respondForceUnlock_shouldReturn200() throws Exception {
        doNothing().when(aiGenerationComponent).respondForceUnlock(eq(1L), anyString(), eq(true));

        ForceUnlockResponseRequest request = ForceUnlockResponseRequest.builder()
                .accept(true)
                .build();

        mockMvc.perform(post("/ai-generation/lock/1/force-unlock-response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "user1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getSessions_shouldReturnSessionList() throws Exception {
        AiSessionResponse session = AiSessionResponse.builder()
                .sessionId("sess-1")
                .functionUnitId(1L)
                .currentPhase(AiPhase.REQUIREMENTS)
                .mode(AiMode.NEW)
                .status(AiSessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(aiGenerationComponent.getSessions(eq(1L)))
                .thenReturn(List.of(session));

        mockMvc.perform(get("/ai-generation/sessions")
                        .param("functionUnitId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sessionId").value("sess-1"))
                .andExpect(jsonPath("$.data[0].functionUnitId").value(1));
    }

    @Test
    void getMessages_shouldReturnPagedMessages() throws Exception {
        AiMessageResponse msg = AiMessageResponse.builder()
                .id(1L)
                .sessionId("sess-1")
                .role(AiMessageRole.USER)
                .content("hello")
                .phase(AiPhase.REQUIREMENTS)
                .createdAt(Instant.now())
                .build();

        Page<AiMessageResponse> page = new PageImpl<>(
                List.of(msg), PageRequest.of(0, 20), 1);

        when(aiGenerationComponent.getMessages(eq("sess-1"), any()))
                .thenReturn(page);

        mockMvc.perform(get("/ai-generation/sessions/sess-1/messages")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].sessionId").value("sess-1"))
                .andExpect(jsonPath("$.data.content[0].role").value("USER"));
    }

    @Test
    void applyGeneratedData_shouldReturn200() throws Exception {
        doNothing().when(aiGenerationComponent)
                .applyGeneratedData(eq(1L), any(ApplyGeneratedDataRequest.class), anyString());

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId("sess-1")
                .generatedData(AiGeneratedData.builder().name("test").build())
                .build();

        mockMvc.perform(post("/ai-generation/1/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "user1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void acquireLock_whenConflict_shouldReturn409() throws Exception {
        LockInfoResponse existingLock = LockInfoResponse.builder()
                .functionUnitId(1L)
                .userId("other-user")
                .userName("Other User")
                .lockedAt(Instant.now())
                .locked(true)
                .build();

        when(aiGenerationComponent.acquireLock(eq(1L), anyString()))
                .thenThrow(new AiLockConflictException(existingLock));

        mockMvc.perform(post("/ai-generation/lock/1")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_LOCK_CONFLICT"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void applyGeneratedData_whenValidationFails_shouldReturn422() throws Exception {
        List<AiValidationError> errors = List.of(
                AiValidationError.builder()
                        .errorType("ENUM_INVALID")
                        .fieldPath("tableDefinitions[0].tableType")
                        .description("Invalid table type")
                        .build()
        );

        doThrow(new AiValidationFailedException(errors))
                .when(aiGenerationComponent)
                .applyGeneratedData(eq(1L), any(ApplyGeneratedDataRequest.class), anyString());

        ApplyGeneratedDataRequest request = ApplyGeneratedDataRequest.builder()
                .sessionId("sess-1")
                .generatedData(AiGeneratedData.builder().name("test").build())
                .build();

        mockMvc.perform(post("/ai-generation/1/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "user1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.errors").isArray());
    }
}
