package com.developer.service.impl;

import com.developer.dto.AiStudioChatRequest;
import com.developer.exception.AiGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStudioChatServiceImplTest {

    @Mock
    private AiGatewayClient aiGatewayClient;

    @Mock
    private AiResponseParser aiResponseParser;

    private AiStudioChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStudioChatServiceImpl(aiGatewayClient, aiResponseParser);
    }

    private AiStudioChatRequest request(String phase, String message,
                                        List<AiStudioChatRequest.HistoryMessage> history) {
        AiStudioChatRequest req = new AiStudioChatRequest();
        req.setFunctionUnitId(1L);
        req.setPhase(phase);
        req.setMessage(message);
        req.setHistory(history);
        return req;
    }

    private AiStudioChatRequest.HistoryMessage historyMessage(String role, String content) {
        AiStudioChatRequest.HistoryMessage m = new AiStudioChatRequest.HistoryMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void chatBuildsPhasePromptAndTranscriptAndReturnsTrimmedReply() {
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("am-token"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "  add a foreign key  "));

        String reply = service.chat(request("TABLE_DESIGN", "How do I link the sub table?",
                List.of(historyMessage("USER", "hi"), historyMessage("ASSISTANT", "hello"))), "am-token");

        assertEquals("add a foreign key", reply);

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        org.mockito.Mockito.verify(aiGatewayClient).chat(prompt.capture(), eq("am-token"));
        assertTrue(prompt.getValue().system().contains("TABLE_DESIGN"));
        assertTrue(prompt.getValue().system().contains("define main and sub tables"));
        assertTrue(prompt.getValue().user().contains("User: hi"));
        assertTrue(prompt.getValue().user().contains("Assistant: hello"));
        assertTrue(prompt.getValue().user().endsWith("User: How do I link the sub table?"));
    }

    @Test
    void chatWithoutHistorySendsBareMessage() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of());
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "ok"));

        service.chat(request("PROCESS_DESIGN", "explain gateways", null), "t");

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        org.mockito.Mockito.verify(aiGatewayClient).chat(prompt.capture(), eq("t"));
        assertEquals("User: explain gateways", prompt.getValue().user());
    }

    @Test
    void blankReplyFailsExplicitly() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of());
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "   "));

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> service.chat(request("VALIDATION", "ready?", null), "t"));
        assertEquals("AI_GATEWAY_EMPTY_RESPONSE", ex.getErrorCode());
    }

    @Test
    void unknownPhaseFailsExplicitly() {
        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> service.chat(request("NOT_A_PHASE", "hi", null), "t"));
        assertEquals("AI_STUDIO_UNKNOWN_PHASE", ex.getErrorCode());
    }
}
