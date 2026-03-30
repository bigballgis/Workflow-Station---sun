package com.developer.component.impl;

import com.developer.dto.AiChatRequest;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.AiMessage;
import com.developer.entity.AiSession;
import com.developer.enums.*;
import com.developer.service.AiGenerationService;
import com.developer.service.AiLockService;
import com.developer.service.AiValidationService;
import com.developer.service.AiWriteService;
import net.jqwik.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiGenerationComponentImpl 属性测试
 *
 * Property 1: 首条消息始终加载上下文
 * Property 1b: 后续消息也始终加载上下文（N8N systemMessage 每次渲染需要最新数据）
 * Property 1c: 阶段切换时加载上下文
 * Validates: Requirements 1.1, 1.2
 */
class AiGenerationComponentImplPropertyTest {

    /**
     * Property 1: 首条消息始终加载上下文
     */
    @Property(tries = 100)
    @Label("Property 1: 首条消息始终加载上下文")
    void firstMessageAlwaysLoadsContext(@ForAll AiPhase phase, @ForAll AiMode mode) throws Exception {
        AiGenerationService aiGenerationService = mock(AiGenerationService.class);
        AiLockService aiLockService = mock(AiLockService.class);
        AiValidationService aiValidationService = mock(AiValidationService.class);
        AiWriteService aiWriteService = mock(AiWriteService.class);

        AiGenerationComponentImpl component = new AiGenerationComponentImpl(
                aiGenerationService, aiLockService, aiValidationService, aiWriteService, (Executor) Runnable::run,
                new com.fasterxml.jackson.databind.ObjectMapper());

        UUID sessionUuid = UUID.randomUUID();
        AiSession session = AiSession.builder()
                .sessionId(sessionUuid).functionUnitId(1L).userId("user1")
                .currentPhase(phase).mode(mode).status(AiSessionStatus.ACTIVE).build();

        when(aiGenerationService.createSession(anyLong(), anyString(), any())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER)
                        .content("test").phase(phase).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString()))
                .thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(anyLong()))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(anyLong(), any(), any()))
                .thenReturn(List.of());

        CountDownLatch latch = new CountDownLatch(1);
        when(aiGenerationService.callN8NWebhook(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any()))
                .thenReturn(Map.of("reply", "ok"));
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(null).message("hello")
                .phase(phase).mode(mode).build();

        component.chatStream(request, "user1");
        latch.await(5, TimeUnit.SECONDS);

        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, phase, mode);
    }

    /**
     * Property 1b: 后续消息（同阶段）也始终加载上下文和文档
     * N8N Agent 的 systemMessage 每次请求都重新渲染，需要最新的 context 和 existingDocuments
     */
    @Property(tries = 100)
    @Label("Property 1b: 后续消息也始终加载上下文和文档")
    void subsequentMessageAlsoLoadsContext(@ForAll AiPhase phase, @ForAll AiMode mode) throws Exception {
        AiGenerationService aiGenerationService = mock(AiGenerationService.class);
        AiLockService aiLockService = mock(AiLockService.class);
        AiValidationService aiValidationService = mock(AiValidationService.class);
        AiWriteService aiWriteService = mock(AiWriteService.class);

        AiGenerationComponentImpl component = new AiGenerationComponentImpl(
                aiGenerationService, aiLockService, aiValidationService, aiWriteService, (Executor) Runnable::run,
                new com.fasterxml.jackson.databind.ObjectMapper());

        UUID sessionUuid = UUID.randomUUID();
        AiSession session = AiSession.builder()
                .sessionId(sessionUuid).functionUnitId(1L).userId("user1")
                .currentPhase(phase).mode(mode).status(AiSessionStatus.ACTIVE).build();

        when(aiGenerationService.restoreSession(anyString())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER)
                        .content("test").phase(phase).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString()))
                .thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(anyLong()))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(anyLong(), any(), any()))
                .thenReturn(List.of());

        CountDownLatch latch = new CountDownLatch(1);
        when(aiGenerationService.callN8NWebhook(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any()))
                .thenReturn(Map.of("reply", "ok"));
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        // Subsequent message: sessionId is non-null, same phase
        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(sessionUuid.toString()).message("follow up")
                .phase(phase).mode(mode).build();

        component.chatStream(request, "user1");
        latch.await(5, TimeUnit.SECONDS);

        // Context and documents are ALWAYS loaded (N8N systemMessage re-renders each request)
        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, phase, mode);
    }

    /**
     * Property 1c: 阶段切换时加载上下文和文档
     */
    @Property(tries = 100)
    @Label("Property 1c: 阶段切换时加载上下文和文档")
    void phaseTransitionLoadsContext(@ForAll AiPhase sessionPhase, @ForAll AiPhase requestPhase, @ForAll AiMode mode) throws Exception {
        Assume.that(sessionPhase != requestPhase);

        AiGenerationService aiGenerationService = mock(AiGenerationService.class);
        AiLockService aiLockService = mock(AiLockService.class);
        AiValidationService aiValidationService = mock(AiValidationService.class);
        AiWriteService aiWriteService = mock(AiWriteService.class);

        AiGenerationComponentImpl component = new AiGenerationComponentImpl(
                aiGenerationService, aiLockService, aiValidationService, aiWriteService, (Executor) Runnable::run,
                new com.fasterxml.jackson.databind.ObjectMapper());

        UUID sessionUuid = UUID.randomUUID();
        AiSession session = AiSession.builder()
                .sessionId(sessionUuid).functionUnitId(1L).userId("user1")
                .currentPhase(sessionPhase).mode(mode).status(AiSessionStatus.ACTIVE).build();

        when(aiGenerationService.restoreSession(anyString())).thenReturn(session);
        when(aiGenerationService.saveMessage(any(), any(), anyString(), any()))
                .thenReturn(AiMessage.builder().sessionId(sessionUuid).role(AiMessageRole.USER)
                        .content("test").phase(requestPhase).build());
        when(aiGenerationService.createChatEmitter(anyLong(), anyString()))
                .thenReturn(new SseEmitter(120000L));
        when(aiGenerationService.serializeFunctionUnitContext(anyLong()))
                .thenReturn(FunctionUnitContextDTO.builder().functionUnitId(1L).name("test").build());
        when(aiGenerationService.getLatestDocuments(anyLong(), any(), any()))
                .thenReturn(List.of());

        CountDownLatch latch = new CountDownLatch(1);
        when(aiGenerationService.callN8NWebhook(any(), anyString(), any(), any(), any(), anyLong(), anyList(), any()))
                .thenReturn(Map.of("reply", "ok"));
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(aiGenerationService).completeChatEmitter(anyLong(), anyString());

        AiChatRequest request = AiChatRequest.builder()
                .functionUnitId(1L).sessionId(sessionUuid.toString()).message("next phase")
                .phase(requestPhase).mode(mode).build();

        component.chatStream(request, "user1");
        latch.await(5, TimeUnit.SECONDS);

        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService).getLatestDocuments(1L, requestPhase, mode);
    }
}
