package com.developer.service.impl;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.dto.HermesMasterChatRequest;
import com.developer.entity.User;
import com.developer.exception.AiGenerationException;
import com.developer.repository.UserRepository;
import com.developer.service.AiGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HermesMasterChatServiceImplTest {

    @Mock
    private AiGatewayClient aiGatewayClient;

    @Mock
    private AiResponseParser aiResponseParser;

    @Mock
    private AiGenerationService aiGenerationService;

    @Mock
    private HermesMasterAutomationClient automationClient;

    @Mock
    private UserRepository userRepository;

    private HermesMasterChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = service(HermesMasterChatServiceImpl.Provider.GAIP);
    }

    private HermesMasterChatServiceImpl service(HermesMasterChatServiceImpl.Provider provider) {
        // 摘要器是纯函数，用真实实现
        return new HermesMasterChatServiceImpl(aiGatewayClient, aiResponseParser, aiGenerationService,
                new AiStudioContextDigest(), automationClient, userRepository, provider);
    }

    private HermesMasterChatRequest request(String message, Long functionUnitId) {
        HermesMasterChatRequest req = new HermesMasterChatRequest();
        req.setMessage(message);
        req.setFunctionUnitId(functionUnitId);
        return req;
    }

    private HermesMasterChatRequest.HistoryMessage historyMessage(String role, String content) {
        HermesMasterChatRequest.HistoryMessage m = new HermesMasterChatRequest.HistoryMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    private AiPromptBuilder.RenderedPrompt capturedPrompt(String amToken) {
        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), eq(amToken));
        return prompt.getValue();
    }

    @Test
    void chatWithoutFunctionUnitSkipsTheDesignDigestAndCarriesPageAndHistory() {
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "  Open Function Units first.  "));

        HermesMasterChatRequest req = request("How do I create a function unit?", null);
        req.setPage("FunctionUnits");
        req.setHistory(List.of(historyMessage("USER", "hi"), historyMessage("ASSISTANT", "hello")));

        assertEquals("Open Function Units first.", service.chat(req, "u1", "tok"));
        verifyNoInteractions(aiGenerationService);

        AiPromptBuilder.RenderedPrompt prompt = capturedPrompt("tok");
        assertTrue(prompt.system().contains("Hermes Master"));
        assertTrue(prompt.user().contains("DW page: FunctionUnits"));
        assertFalse(prompt.user().contains("Current function unit design"));
        assertTrue(prompt.user().contains("User: hi"));
        assertTrue(prompt.user().contains("Assistant: hello"));
        assertTrue(prompt.user().endsWith("User: How do I create a function unit?"));
    }

    @Test
    void chatOnAFunctionUnitGroundsThePromptInItsRealTableNames() {
        FunctionUnitContextDTO context = new FunctionUnitContextDTO();
        context.setTableDefinitions(List.of(Map.of("tableName", "purchase_order", "tableType", "MAIN")));
        when(aiGenerationService.serializeFunctionUnitContext(7L)).thenReturn(context);
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "ok"));

        service.chat(request("Review my tables", 7L), "u1", "tok");

        AiPromptBuilder.RenderedPrompt prompt = capturedPrompt("tok");
        assertTrue(prompt.user().contains("Current function unit design"));
        assertTrue(prompt.user().contains("purchase_order"));
    }

    @Test
    void chatContinuesWithoutTheDigestWhenTheContextCannotBeSerialized() {
        when(aiGenerationService.serializeFunctionUnitContext(7L))
                .thenThrow(new AiGenerationException("AI_CONTEXT_TOO_LARGE", "too large"));
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "ok"));

        assertEquals("ok", service.chat(request("Review my tables", 7L), "u1", "tok"));
        assertFalse(capturedPrompt("tok").user().contains("Current function unit design"));
    }

    @Test
    void chatDropsTheOldestHistoryFirstAndAlwaysKeepsTheLatestMessage() {
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "ok"));

        HermesMasterChatRequest req = request("latest question", null);
        req.setHistory(List.of(
                historyMessage("USER", "OLDEST " + "x".repeat(3990)),
                historyMessage("ASSISTANT", "y".repeat(4000)),
                historyMessage("USER", "z".repeat(4000)),
                historyMessage("ASSISTANT", "NEWEST " + "w".repeat(3990))));

        service.chat(req, "u1", "tok");

        String user = capturedPrompt("tok").user();
        assertFalse(user.contains("OLDEST"));
        assertTrue(user.contains("NEWEST"));
        assertTrue(user.endsWith("User: latest question"));
    }

    @Test
    void chatFailsExplicitlyWhenTheGatewayReturnsNoReplyText() {
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "   "));

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> service.chat(request("hello", null), "u1", "tok"));
        assertEquals("AI_GATEWAY_EMPTY_RESPONSE", e.getErrorCode());
    }

    @Test
    void automationSendsTheSameContextWithTheMessageAndTheUsersEmployeeId() {
        HermesMasterChatServiceImpl automation = service(HermesMasterChatServiceImpl.Provider.AUTOMATION);
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").employeeId(" 45349679 ").build()));
        when(automationClient.chat(eq("How do I create a function unit?"), any(), eq("45349679")))
                .thenReturn(" Open **Function Units**. ");

        HermesMasterChatRequest req = request("How do I create a function unit?", null);
        req.setPage("FunctionUnits");

        assertEquals("Open **Function Units**.", automation.chat(req, "u1", null));
        ArgumentCaptor<String> userInput = ArgumentCaptor.forClass(String.class);
        verify(automationClient).chat(eq("How do I create a function unit?"), userInput.capture(), eq("45349679"));
        assertTrue(userInput.getValue().startsWith("## Where the user is\nDW page: FunctionUnits"));
        assertTrue(userInput.getValue().endsWith("User: How do I create a function unit?"));
        verifyNoInteractions(aiGatewayClient);
    }

    @Test
    void automationFailsExplicitlyWhenTheUserHasNoEmployeeId() {
        HermesMasterChatServiceImpl automation = service(HermesMasterChatServiceImpl.Provider.AUTOMATION);
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").employeeId(" ").build()));

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> automation.chat(request("hello", null), "u1", null));
        assertEquals("HERMES_MASTER_NO_EMPLOYEE_ID", e.getErrorCode());
        verifyNoInteractions(automationClient, aiGatewayClient);
    }
}
