package com.developer.service.impl;

import com.developer.dto.AiStudioChatRequest;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.exception.AiGenerationException;
import com.developer.repository.UserRepository;
import com.developer.service.AiGenerationService;
import com.developer.service.AiPromptTemplateService;
import com.developer.service.AiStudioChatService;
import com.developer.service.AiStudioChatService.StudioChatResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiStudioChatServiceImplTest {

    @Mock
    private AiGatewayClient aiGatewayClient;

    @Mock
    private AiResponseParser aiResponseParser;

    @Mock
    private AiGenerationService aiGenerationService;

    @Mock
    private FunctionUnitDocumentService documentService;

    @Mock
    private AiStudioAutomationClient automationClient;

    @Mock
    private AiPromptTemplateService promptTemplateService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiTableNameAvailability tableNameAvailability;

    private AiStudioChatServiceImpl service;

    @BeforeEach
    void setUp() {
        // 摘要器是纯函数，用真实实现：顺带覆盖"设计现状进 prompt"这条链路
        service = service(AiStudioChatServiceImpl.Provider.GAIP);
    }

    private AiStudioChatServiceImpl service(AiStudioChatServiceImpl.Provider provider) {
        return new AiStudioChatServiceImpl(aiGatewayClient, aiResponseParser, aiGenerationService,
                new AiStudioContextDigest(), documentService, automationClient, promptTemplateService,
                userRepository, tableNameAvailability, provider);
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

        StudioChatResult result = service.chat(request("TABLE_DESIGN", "How do I link the sub table?",
                List.of(historyMessage("USER", "hi"), historyMessage("ASSISTANT", "hello"))), "u1", "am-token");

        assertEquals("add a foreign key", result.reply());
        assertNull(result.proposal());
        // 顾问轮读上下文只为拼"当前设计现状"摘要；GENERATION 管线（callAiModel）依旧不碰
        verify(aiGenerationService).serializeFunctionUnitContext(1L);
        verify(aiGenerationService, never()).callAiModel(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), eq("am-token"));
        assertTrue(prompt.getValue().system().contains("TABLE_DESIGN"));
        assertTrue(prompt.getValue().system().contains("define main and sub tables"));
        assertTrue(prompt.getValue().user().contains("User: hi"));
        assertTrue(prompt.getValue().user().contains("Assistant: hello"));
        assertTrue(prompt.getValue().user().endsWith("User: How do I link the sub table?"));
    }

    @Test
    void advisoryChatCarriesRequirementsAndTheCurrentPhaseDesignSection() {
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "ok"));
        java.util.Map<com.developer.enums.AiDocumentType, String> docs =
                new java.util.EnumMap<>(com.developer.enums.AiDocumentType.class);
        docs.put(com.developer.enums.AiDocumentType.REQUIREMENTS, "## Data Requirements\nAmounts keep 4 decimals.");
        docs.put(com.developer.enums.AiDocumentType.DESIGN,
                "## Process Design\nApprove node.\n\n## Table Design\nTable orders.\n\n## Form Design\nOrder form.");
        when(documentService.latestContents(1L)).thenReturn(docs);

        service.chat(request("TABLE_DESIGN", "What precision?", null), "u1", "tok");

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), eq("tok"));
        String user = prompt.getValue().user();
        assertTrue(user.contains("Amounts keep 4 decimals."));
        assertTrue(user.contains("Function Unit Design document — Table Design section\nTable orders."));
        assertFalse(user.contains("Approve node."));
        assertFalse(user.contains("Order form."));
        assertTrue(user.endsWith("User: What precision?"));
    }

    @Test
    void advisoryChatFallsBackToTheWholeDesignWhenItHasNoPhaseSections() {
        Map<String, Object> httpResult = Map.of("status", 200);
        when(aiGatewayClient.chat(any(), eq("tok"))).thenReturn(httpResult);
        when(aiResponseParser.parse(httpResult)).thenReturn(Map.of("reply", "ok"));
        when(documentService.latestContents(1L)).thenReturn(
                Map.of(com.developer.enums.AiDocumentType.DESIGN, "# Old design\nFree text."));

        service.chat(request("TABLE_DESIGN", "hi", null), "u1", "tok");

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), eq("tok"));
        assertTrue(prompt.getValue().user().contains("## Function Unit Design document\n# Old design\nFree text."));
        assertFalse(prompt.getValue().user().contains("Requirements document"));
    }

    @Test
    void proposalPassesTheDocumentsToTheGenerationPipeline() {
        AiStudioChatRequest req = request("TABLE_DESIGN", "add amount", null);
        req.setPropose(true);
        when(documentService.latestContents(1L)).thenReturn(
                Map.of(com.developer.enums.AiDocumentType.REQUIREMENTS, "Amounts keep 4 decimals."));

        AiStudioChatService.ProposalDraft draft = service.prepareProposal(req, "u1");

        assertEquals(List.of(Map.of("documentType", "REQUIREMENTS", "content", "Amounts keep 4 decimals.")),
                draft.documents());
    }

    @Test
    void advisoryChatGroundsThePromptInTheCurrentDesignWithoutDumpingBpmn() {
        com.developer.dto.FunctionUnitContextDTO context = new com.developer.dto.FunctionUnitContextDTO();
        context.setTableDefinitions(List.of(Map.of(
                "tableName", "orders", "tableType", "MAIN", "tableDisplayName", "Orders",
                "fieldDefinitions", List.of(
                        Map.of("fieldName", "id", "dataType", "BIGINT", "isPrimaryKey", true),
                        Map.of("fieldName", "order_no", "dataType", "VARCHAR")),
                "foreignKeys", List.of())));
        context.setProcessDefinition(Map.of("bpmnXml",
                "<bpmn:definitions xmlns:bpmn=\"x\"><bpmn:process id=\"p\"><bpmn:userTask id=\"u1\" name=\"Review\"/></bpmn:process></bpmn:definitions>"));
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(context);
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("ok", true));
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "looks fine"));

        service.chat(request("TABLE_DESIGN", "what is wrong with my tables?", null), "u1", "t");

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt = ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), any());
        String user = prompt.getValue().user();
        assertTrue(user.startsWith("## Current design (TABLE_DESIGN)"));
        assertTrue(user.contains("orders (MAIN) \"Orders\": id:BIGINT [PK], order_no:VARCHAR"));
        // 表设计阶段不带流程；任何阶段都不把 BPMN 原文塞进对话
        assertFalse(user.contains("<bpmn:"));
        assertFalse(user.contains("Process nodes"));
        assertTrue(user.endsWith("User: what is wrong with my tables?"));
    }

    @Test
    void advisoryChatFallsBackToNoDigestWhenTheContextCannotBeSerialized() {
        when(aiGenerationService.serializeFunctionUnitContext(1L))
                .thenThrow(new AiGenerationException("AI_CONTEXT_TOO_LARGE", "too big"));
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("ok", true));
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "still answering"));

        StudioChatResult result = service.chat(request("TABLE_DESIGN", "hello", null), "u1", "t");

        assertEquals("still answering", result.reply());
        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt = ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), any());
        assertFalse(prompt.getValue().user().contains("## Current design"));
        assertTrue(prompt.getValue().user().startsWith("User: hello"));
    }

    @Test
    void historyEntryWithProposalIsRenderedIntoTranscriptAndCapped() {
        AiStudioChatRequest.HistoryMessage proposed = historyMessage("ASSISTANT", "Here is the proposed change.");
        proposed.setProposalScope("EMAIL_TEMPLATES");
        // 有序 Map：name 必须排在超长 subject 前面，才不会被截断掉（Map.of 的迭代顺序不固定）
        Map<String, Object> template = new java.util.LinkedHashMap<>();
        template.put("name", "Order Shipped");
        template.put("subject", "x".repeat(7000));
        proposed.setProposal(Map.of("emailTemplates", List.of(template)));
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of("ok", true));
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "sure"));

        service.chat(request("EMAIL_TEMPLATES", "change its subject",
                List.of(historyMessage("USER", "add a template"), proposed)), "u1", "t");

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt = ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), any());
        String user = prompt.getValue().user();
        assertTrue(user.contains("[Proposed change, scope=EMAIL_TEMPLATES"));
        assertTrue(user.contains("\"name\":\"Order Shipped\""));
        assertTrue(user.contains("…(truncated)"));
        assertTrue(user.length() < AiStudioChatServiceImpl.HISTORY_PROPOSAL_CHAR_CAP + 1500,
                "proposal JSON is capped, not dumped in full");
        assertTrue(user.endsWith("User: change its subject"));
    }

    @Test
    void chatWithoutHistorySendsBareMessage() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of());
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "ok"));

        service.chat(request("PROCESS_DESIGN", "explain gateways", null), "u1", "t");

        ArgumentCaptor<AiPromptBuilder.RenderedPrompt> prompt =
                ArgumentCaptor.forClass(AiPromptBuilder.RenderedPrompt.class);
        verify(aiGatewayClient).chat(prompt.capture(), eq("t"));
        assertEquals("User: explain gateways", prompt.getValue().user());
    }

    @Test
    void blankReplyFailsExplicitly() {
        when(aiGatewayClient.chat(any(), any())).thenReturn(Map.of());
        when(aiResponseParser.parse(any())).thenReturn(Map.of("reply", "   "));

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> service.chat(request("VALIDATION", "ready?", null), "u1", "t"));
        assertEquals("AI_GATEWAY_EMPTY_RESPONSE", ex.getErrorCode());
    }

    @Test
    void unknownPhaseFailsExplicitly() {
        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> service.chat(request("NOT_A_PHASE", "hi", null), "u1", "t"));
        assertEquals("AI_STUDIO_UNKNOWN_PHASE", ex.getErrorCode());
    }

    @Test
    void proposeRunsGenerationPipelineWithPhaseScope() {
        AiStudioChatRequest req = request("TABLE_DESIGN", "add an audit sub table", null);
        req.setPropose(true);

        FunctionUnitContextDTO context = new FunctionUnitContextDTO();
        List<Map<String, Object>> tables = List.of(Map.of("tableName", "audit"));
        // 模型在 scoped 轮次里顺手带上的范围外切片必须被裁掉，否则 Apply 会撞唯一约束
        Map<String, Object> generated = Map.of(
                "tableDefinitions", tables,
                "processDefinition", Map.of("bpmnXml", "should-be-stripped"));
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(context);
        when(aiGenerationService.determineMode(1L)).thenReturn(AiMode.MODIFY);
        when(aiGenerationService.callAiModel(any(),
                org.mockito.ArgumentMatchers.argThat((String msg) -> msg != null
                        && msg.startsWith("User: add an audit sub table")
                        && msg.contains("Regenerate ONLY the 'TABLES' slice")),
                eq(AiPhase.GENERATION), eq(AiMode.MODIFY), eq(context), eq(1L), eq(List.of()),
                eq("TABLES"), eq("tok")))
                .thenReturn(Map.of("reply", "Added an audit sub table.", "generatedData", generated));

        StudioChatResult result = service.chat(req, "u1", "tok");

        assertEquals("Added an audit sub table.", result.reply());
        assertEquals(Map.of("tableDefinitions", tables), result.proposal());
        assertEquals("TABLES", result.proposalScope());
        verifyNoInteractions(aiGatewayClient);
    }

    @Test
    void oneClickGeneratesTheWholeCoreDesignAndDropsTheSlicesItMustNotProduce() {
        AiStudioChatRequest req = request("PROCESS_DESIGN", "leave request with manager approval", null);
        FunctionUnitContextDTO context = new FunctionUnitContextDTO();
        List<Map<String, Object>> tables = List.of(Map.of("tableName", "leave_request"));
        Map<String, Object> process = Map.of("bpmnXml", "<bpmn/>");
        Map<String, Object> generated = Map.of(
                "name", "Renamed by the model",
                "tableDefinitions", tables,
                "processDefinition", process,
                "emailConnections", List.of(Map.of("name", "hr@example.com")),
                "mainTableViews", List.of(Map.of("viewName", "All")));
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(context);
        when(aiGenerationService.determineMode(1L)).thenReturn(AiMode.NEW);
        when(documentService.latestContents(1L)).thenReturn(Map.of());
        when(aiGenerationService.callAiModel(any(),
                org.mockito.ArgumentMatchers.argThat((String msg) -> msg != null
                        && msg.startsWith("User: leave request with manager approval")
                        && msg.contains("Generate the COMPLETE function unit design")
                        && msg.contains("processDefinition")),
                eq(AiPhase.GENERATION), eq(AiMode.NEW), eq(context), eq(1L), eq(List.of()),
                eq("ALL"), eq("tok")))
                .thenReturn(Map.of("reply", "Generated.", "generatedData", generated));

        AiStudioChatService.ProposalDraft draft = service.prepareOneClick(req);
        StudioChatResult result = service.runProposal(draft, "tok");

        assertEquals("ALL", draft.scope());
        assertEquals("PROCESS_DESIGN", draft.phase());
        assertEquals(Map.of("tableDefinitions", tables, "processDefinition", process), result.proposal());
        assertEquals("ALL", result.proposalScope());
    }

    @Test
    void proposeOnEmailPhaseUsesUpsertScopeAndKeepsOnlyItsSlice() {
        AiStudioChatRequest req = request("EMAIL_TEMPLATES", "add an approval notification", null);
        req.setPropose(true);

        FunctionUnitContextDTO context = new FunctionUnitContextDTO();
        List<Map<String, Object>> templates = List.of(Map.of("name", "Approved", "subject", "s", "bodyHtml", "<p/>"));
        Map<String, Object> generated = Map.of(
                "emailTemplates", templates,
                "emailConnections", List.of(Map.of("name", "should-be-stripped@x.com")));
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(context);
        when(aiGenerationService.determineMode(1L)).thenReturn(AiMode.MODIFY);
        when(aiGenerationService.callAiModel(any(),
                org.mockito.ArgumentMatchers.argThat((String msg) -> msg != null
                        && msg.contains("Regenerate ONLY the 'EMAIL_TEMPLATES' slice")
                        && msg.contains("applied as an upsert keyed by name")),
                eq(AiPhase.GENERATION), eq(AiMode.MODIFY), eq(context), eq(1L), eq(List.of()),
                eq("EMAIL_TEMPLATES"), eq("tok")))
                .thenReturn(Map.of("reply", "Added a template.", "generatedData", generated));

        StudioChatResult result = service.chat(req, "u1", "tok");

        assertEquals(Map.of("emailTemplates", templates), result.proposal());
        assertEquals("EMAIL_TEMPLATES", result.proposalScope());
    }

    @Test
    void connectionsAndMonitorsPhasesMapToTheirOwnScopes() {
        assertEquals(java.util.Set.of("emailConnections"), AiStudioChatServiceImpl.allowedSlices("CONNECTIONS"));
        assertEquals(java.util.Set.of("emailMonitorRules"), AiStudioChatServiceImpl.allowedSlices("EMAIL_MONITORS"));
        assertTrue(AiStudioChatServiceImpl.allowedSlices("ALL").contains("emailTemplates"));
        assertEquals(java.util.Set.of("mainTableViews"), AiStudioChatServiceImpl.allowedSlices("VIEWS"));
        assertTrue(AiStudioChatServiceImpl.UPSERT_SCOPES.contains("VIEWS"));
        assertEquals(java.util.Set.of("serviceTaskBindings"), AiStudioChatServiceImpl.allowedSlices("SERVICE_TASK_BINDINGS"));
        assertTrue(AiStudioChatServiceImpl.UPSERT_SCOPES.contains("SERVICE_TASK_BINDINGS"));
        // 非 upsert scope 的提案消息不带 upsert 提示
        assertFalse(AiStudioChatServiceImpl.UPSERT_SCOPES.contains("TABLES"));
    }

    @Test
    void proposeOnUnsupportedPhaseFailsExplicitly() {
        AiStudioChatRequest req = request("VALIDATION", "change something", null);
        req.setPropose(true);

        AiGenerationException ex = assertThrows(AiGenerationException.class, () -> service.chat(req, "u1", "t"));
        assertEquals("AI_STUDIO_PROPOSAL_UNSUPPORTED_PHASE", ex.getErrorCode());
    }

    @Test
    void proposeWithNeitherReplyNorDataFailsExplicitly() {
        AiStudioChatRequest req = request("PROCESS_DESIGN", "change flow", null);
        req.setPropose(true);

        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(new FunctionUnitContextDTO());
        when(aiGenerationService.determineMode(1L)).thenReturn(AiMode.NEW);
        when(aiGenerationService.callAiModel(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of());

        AiGenerationException ex = assertThrows(AiGenerationException.class, () -> service.chat(req, "u1", "t"));
        assertEquals("AI_STUDIO_PROPOSAL_EMPTY", ex.getErrorCode());
    }

    // ==================== Content Organizer (provider AUTOMATION) ====================

    private void employee(String employeeId) {
        when(userRepository.findById("u1")).thenReturn(java.util.Optional.of(
                com.developer.entity.User.builder().id("u1").employeeId(employeeId).build()));
    }

    @Test
    void agentProposalSendsTheSectionedRequestAndTrimsToTheScope() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatRequest req = request("TABLE_DESIGN", "add amount",
                List.of(historyMessage("USER", "earlier question")));
        req.setPropose(true);
        employee(" 45349679 ");
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(new FunctionUnitContextDTO());
        when(aiGenerationService.getSchemaMetadata()).thenReturn(Map.of("tableTypes", List.of("MAIN")));
        when(promptTemplateService.resolve("GENERATION")).thenReturn("FIELD SPEC");
        when(documentService.latestContents(1L)).thenReturn(Map.of());

        AiStudioChatService.ProposalDraft draft = co.prepareProposal(req, "u1");

        assertEquals("45349679", draft.staffId());
        assertTrue(draft.message().startsWith("## MODE: PROPOSE\n## PHASE: TABLE_DESIGN\n## SCOPE: TABLES\n"));
        assertTrue(draft.message().contains("## FORMAT SPECIFICATION\n"));
        assertTrue(draft.message().contains("FIELD SPEC"));
        assertTrue(draft.message().contains("## CONVERSATION\nUser: earlier question"));
        assertTrue(draft.message().endsWith("## USER REQUEST\nadd amount"));

        when(automationClient.chat(draft.message(), "45349679")).thenReturn("reply with block");
        when(aiResponseParser.parseText("reply with block")).thenReturn(new java.util.HashMap<>(Map.of(
                "reply", "Tables designed.",
                "generatedData", Map.of("tableDefinitions", List.of(Map.of("tableName", "t")),
                        "processDefinition", Map.of("bpmnXml", "<x/>")))));

        StudioChatResult result = co.runProposal(draft, "tok");

        assertEquals("TABLES", result.proposalScope());
        assertEquals(java.util.Set.of("tableDefinitions"), result.proposal().keySet());
        assertEquals("Tables designed.", result.reply());
        verifyNoInteractions(aiGatewayClient);
        verify(aiGenerationService, never()).callAiModel(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void agentProposalRepairsARepairableViolationOnce() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.ProposalDraft draft = new AiStudioChatService.ProposalDraft(
                1L, "PROCESS_DESIGN", "PROCESS", "## MODE: PROPOSE", null, AiMode.NEW, List.of(), "45349679");
        when(automationClient.chat("## MODE: PROPOSE", "45349679")).thenReturn("bad");
        when(aiResponseParser.parseText("bad")).thenThrow(
                new AiGenerationException("AI_BPMN_MISSING_DI", "Task_1 has no shape"));
        ArgumentCaptor<String> repair = ArgumentCaptor.forClass(String.class);
        when(automationClient.chat(org.mockito.ArgumentMatchers.startsWith("## MODE: PROPOSE\n\n"), eq("45349679")))
                .thenReturn("good");
        when(aiResponseParser.parseText("good")).thenReturn(new java.util.HashMap<>(Map.of(
                "reply", "fixed", "generatedData", Map.of("processDefinition", Map.of("bpmnXml", "<x/>")))));

        StudioChatResult result = co.runProposal(draft, "tok");

        assertEquals("PROCESS", result.proposalScope());
        verify(automationClient, org.mockito.Mockito.times(2)).chat(repair.capture(), eq("45349679"));
        assertTrue(repair.getAllValues().get(1).contains("Violation code: AI_BPMN_MISSING_DI"));
    }

    /** 表名全平台唯一：TABLES 提案里被别的 FU 占用的表名作为可修违规，带原话重问 CO 一次 */
    @Test
    void agentTablesProposalWithATakenTableNameIsAskedOnceMoreWithTheViolation() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.ProposalDraft draft = new AiStudioChatService.ProposalDraft(
                1L, "TABLE_DESIGN", "TABLES", "## MODE: PROPOSE", null, AiMode.NEW, List.of(), "45349679");
        Map<String, Object> taken = Map.of("tableDefinitions", List.of(Map.of("tableName", "leave_request")));
        Map<String, Object> renamed = Map.of("tableDefinitions", List.of(Map.of("tableName", "hr_leave_request")));
        when(automationClient.chat("## MODE: PROPOSE", "45349679")).thenReturn("taken");
        when(aiResponseParser.parseText("taken")).thenReturn(new java.util.HashMap<>(Map.of(
                "reply", "v1", "generatedData", taken)));
        org.mockito.Mockito.doThrow(new AiGenerationException(AiTableNameAvailability.TABLE_NAME_TAKEN,
                        AiTableNameAvailability.describe(List.of("leave_request"))))
                .when(tableNameAvailability).requireAvailable(1L, taken);
        when(automationClient.chat(org.mockito.ArgumentMatchers.startsWith("## MODE: PROPOSE\n\n"), eq("45349679")))
                .thenReturn("renamed");
        when(aiResponseParser.parseText("renamed")).thenReturn(new java.util.HashMap<>(Map.of(
                "reply", "v2", "generatedData", renamed)));
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);

        StudioChatResult result = co.runProposal(draft, "tok");

        verify(automationClient, org.mockito.Mockito.times(2)).chat(sent.capture(), eq("45349679"));
        assertTrue(sent.getAllValues().get(1).contains("Violation code: " + AiTableNameAvailability.TABLE_NAME_TAKEN));
        assertTrue(sent.getAllValues().get(1).contains("[leave_request]"));
        verify(tableNameAvailability).requireAvailable(1L, renamed);
        assertEquals(renamed.get("tableDefinitions"), result.proposal().get("tableDefinitions"));
    }

    /** 不写表的 scope 不查表名：表切片会被裁掉，查了只会白白重问 */
    @Test
    void agentProposalOutsideTheTableScopesDoesNotCheckTableNames() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.ProposalDraft draft = new AiStudioChatService.ProposalDraft(
                1L, "FORM_DESIGN", "FORMS", "## MODE: PROPOSE", null, AiMode.NEW, List.of(), "45349679");
        when(automationClient.chat("## MODE: PROPOSE", "45349679")).thenReturn("forms");
        when(aiResponseParser.parseText("forms")).thenReturn(new java.util.HashMap<>(Map.of(
                "reply", "ok", "generatedData", Map.of(
                        "formDefinitions", List.of(Map.of("formName", "f")),
                        "tableDefinitions", List.of(Map.of("tableName", "leave_request"))))));

        StudioChatResult result = co.runProposal(draft, "tok");

        verifyNoInteractions(tableNameAvailability);
        assertEquals(java.util.Set.of("formDefinitions"), result.proposal().keySet());
    }

    /** parseText 对没有标记块的回答：generatedData 为 null，说明文字原样保留（含模型误用的代码块）。 */
    private static Map<String, Object> parsedWithoutBlock(String reply) {
        Map<String, Object> parsed = new java.util.HashMap<>();
        parsed.put("reply", reply);
        parsed.put("generatedData", null);
        return parsed;
    }

    private static final String CODE_BLOCK_REPLY = "Process drawn.\n```json\n{\"processDefinition\":{}}\n```";

    @Test
    void agentProposalWithoutADataBlockIsAskedOnceMoreForTheMarkerBlock() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.ProposalDraft draft = new AiStudioChatService.ProposalDraft(
                1L, "PROCESS_DESIGN", "PROCESS", "## MODE: PROPOSE", null, AiMode.NEW, List.of(), "45349679");
        when(automationClient.chat("## MODE: PROPOSE", "45349679")).thenReturn("code block");
        when(aiResponseParser.parseText("code block")).thenReturn(parsedWithoutBlock(CODE_BLOCK_REPLY));
        when(automationClient.chat(org.mockito.ArgumentMatchers.startsWith("## MODE: PROPOSE\n\n"), eq("45349679")))
                .thenReturn("marker block");
        when(aiResponseParser.parseText("marker block")).thenReturn(new java.util.HashMap<>(Map.of(
                "reply", "Process drawn.", "generatedData", Map.of("processDefinition", Map.of("bpmnXml", "<x/>")))));
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);

        StudioChatResult result = co.runProposal(draft, "tok");

        assertEquals("PROCESS", result.proposalScope());
        assertEquals(Map.of("processDefinition", Map.of("bpmnXml", "<x/>")), result.proposal());
        verify(automationClient, org.mockito.Mockito.times(2)).chat(sent.capture(), eq("45349679"));
        String repair = sent.getAllValues().get(1);
        assertTrue(repair.contains("---GENERATED_DATA_START---"));
        assertTrue(repair.contains("not inside a code block"));
        assertTrue(!repair.contains("Violation code:"), "no validator violation to feed back");
    }

    @Test
    void agentProposalStillWithoutADataBlockAfterTheRepairKeepsTheReplyOnlyResult() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.ProposalDraft draft = new AiStudioChatService.ProposalDraft(
                1L, "PROCESS_DESIGN", "PROCESS", "## MODE: PROPOSE", null, AiMode.NEW, List.of(), "45349679");
        when(automationClient.chat(org.mockito.ArgumentMatchers.anyString(), eq("45349679"))).thenReturn("code block");
        when(aiResponseParser.parseText("code block")).thenReturn(parsedWithoutBlock(CODE_BLOCK_REPLY));

        StudioChatResult result = co.runProposal(draft, "tok");

        assertEquals(null, result.proposal());
        assertEquals(null, result.proposalScope());
        assertEquals(CODE_BLOCK_REPLY, result.reply());
        verify(automationClient, org.mockito.Mockito.times(2)).chat(any(), eq("45349679"));
    }

    @Test
    void agentOneClickStepStillWithoutADataBlockAfterTheRepairFailsWithTheOriginalError() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.AgentOneClickDraft draft = new AiStudioChatService.AgentOneClickDraft(
                1L, Map.of(), "{}", "FIELD SPEC", List.of(), "", "leave request", "45349679");
        when(automationClient.chat(org.mockito.ArgumentMatchers.anyString(), eq("45349679"))).thenReturn("code block");
        when(aiResponseParser.parseText("code block")).thenReturn(parsedWithoutBlock(CODE_BLOCK_REPLY));
        List<com.developer.dto.AiStudioProposalJobResponse.Progress> progress = new java.util.ArrayList<>();

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> co.runAgentOneClick(draft, progress::add));

        assertEquals(AiStudioOneClickOrchestrator.STEP_NO_DATA, e.getErrorCode());
        assertEquals("Step 1/5 (Process Design): Content Organizer returned no data: " + CODE_BLOCK_REPLY,
                e.getMessage());
        verify(automationClient, org.mockito.Mockito.times(2)).chat(any(), eq("45349679"));
        assertEquals(1, progress.size());
    }

    @Test
    void agentOneClickReasksAStepWithoutADataBlockWithoutExtraProgressAndCarriesOn() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.AgentOneClickDraft draft = new AiStudioChatService.AgentOneClickDraft(
                1L, Map.of(), "{}", "FIELD SPEC", List.of(), "", "leave request", "45349679");
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        when(automationClient.chat(messages.capture(), eq("45349679"))).thenAnswer(invocation -> {
            String message = invocation.getArgument(0);
            if (message.contains("AUTOMATIC CORRECTION REQUEST")) return "t2";
            String phase = message.substring("## MODE: PROPOSE\n## PHASE: ".length(), message.indexOf("\n## SCOPE"));
            return phase;
        });
        when(aiResponseParser.parseText(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String reply = invocation.getArgument(0);
            Map<String, Object> data = switch (reply) {
                case "PROCESS_DESIGN" -> Map.of("processDefinition", Map.of("bpmnXml", "<x/>"));
                case "TABLE_DESIGN" -> null;
                case "t2" -> Map.of("tableDefinitions", List.of(Map.of("tableName", "t")), "tableRelations", List.of());
                case "FORM_DESIGN" -> Map.of("formDefinitions", List.of());
                case "DECISION_DESIGN" -> Map.of("decisionDefinitions", List.of());
                default -> Map.of("actionDefinitions", List.of());
            };
            Map<String, Object> parsed = parsedWithoutBlock("ok " + reply);
            parsed.put("generatedData", data);
            return parsed;
        });
        when(aiResponseParser.checkCrossSlices(any())).thenReturn(List.of());
        List<com.developer.dto.AiStudioProposalJobResponse.Progress> progress = new java.util.ArrayList<>();

        StudioChatResult result = co.runAgentOneClick(draft, progress::add);

        assertEquals(List.of(Map.of("tableName", "t")), result.proposal().get("tableDefinitions"));
        assertEquals(6, messages.getAllValues().size());
        assertTrue(messages.getAllValues().get(2).contains("## PHASE: TABLE_DESIGN"));
        assertTrue(messages.getAllValues().get(2).contains("not inside a code block"));
        assertEquals(List.of(1, 2, 3, 4, 5), progress.stream()
                .map(com.developer.dto.AiStudioProposalJobResponse.Progress::step).toList());
    }

    @Test
    void agentOneClickStoppedDuringAStepWithoutADataBlockDoesNotAskAgain() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.AgentOneClickDraft draft = new AiStudioChatService.AgentOneClickDraft(
                1L, Map.of(), "{}", "FIELD SPEC", List.of(), "", "leave request", "45349679");
        when(automationClient.chat(org.mockito.ArgumentMatchers.anyString(), eq("45349679"))).thenAnswer(invocation -> {
            Thread.currentThread().interrupt(); // Stop 在 CO 调用期间到达
            return "code block";
        });
        when(aiResponseParser.parseText("code block")).thenReturn(parsedWithoutBlock(CODE_BLOCK_REPLY));
        try {
            AiGenerationException e = assertThrows(AiGenerationException.class,
                    () -> co.runAgentOneClick(draft, p -> { }));

            assertEquals("AI_STUDIO_PROPOSAL_CANCELLED", e.getErrorCode());
            assertTrue(e.getMessage().startsWith("Step 1/5 (Process Design): "));
            verify(automationClient, org.mockito.Mockito.times(1)).chat(any(), eq("45349679"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void agentAdvisoryReplyWithACodeBlockIsReturnedAsIsWithoutAskingAgain() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        employee("45349679");
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(new FunctionUnitContextDTO());
        when(aiGenerationService.getSchemaMetadata()).thenReturn(Map.of());
        when(documentService.latestContents(1L)).thenReturn(Map.of());
        when(automationClient.chat(org.mockito.ArgumentMatchers.anyString(), eq("45349679")))
                .thenReturn(CODE_BLOCK_REPLY);

        StudioChatResult result = co.chat(request("PROCESS_DESIGN", "what would the JSON look like?", null), "u1", "t");

        assertEquals(CODE_BLOCK_REPLY, result.reply());
        assertEquals(null, result.proposal());
        verify(automationClient, org.mockito.Mockito.times(1)).chat(any(), eq("45349679"));
        verifyNoInteractions(aiResponseParser);
    }

    @Test
    void agentPathWithoutAnEmployeeIdFailsBeforeCallingAnything() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        employee(" ");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> co.chat(request("TABLE_DESIGN", "hi", null), "u1", "t"));

        assertEquals("AI_STUDIO_NO_EMPLOYEE_ID", ex.getErrorCode());
        verifyNoInteractions(automationClient, aiGatewayClient);
    }

    @Test
    void agentAdvisoryChatAsksTheAgentSettingWithoutTheFormatSpecification() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        employee("45349679");
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(new FunctionUnitContextDTO());
        when(aiGenerationService.getSchemaMetadata()).thenReturn(Map.of());
        when(documentService.latestContents(1L)).thenReturn(Map.of());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        when(automationClient.chat(message.capture(), eq("45349679"))).thenReturn("  Use a SUB table.  ");

        StudioChatResult result = co.chat(request("VALIDATION", "ready?", null), "u1", "t");

        assertEquals("Use a SUB table.", result.reply());
        assertTrue(message.getValue().startsWith("## MODE: ADVISORY\n## PHASE: VALIDATION\n## SCOPE: NONE\n"));
        assertFalse(message.getValue().contains("## FORMAT SPECIFICATION"));
        verifyNoInteractions(aiGatewayClient, promptTemplateService);
    }

    @Test
    void agentOneClickIsPreparedOnTheRequestThreadWithTheEmployeeIdAndTheWholeDesign() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatRequest req = request("PROCESS_DESIGN", "leave request",
                List.of(historyMessage("USER", "earlier question")));
        employee(" 45349679 ");
        FunctionUnitContextDTO context = new FunctionUnitContextDTO();
        context.setFunctionUnitId(1L);
        context.setTableDefinitions(List.of(Map.of("tableName", "old_table")));
        when(aiGenerationService.serializeFunctionUnitContext(1L)).thenReturn(context);
        when(aiGenerationService.getSchemaMetadata()).thenReturn(Map.of("tableTypes", List.of("MAIN")));
        when(promptTemplateService.resolve("GENERATION")).thenReturn("FIELD SPEC");
        when(documentService.latestContents(1L)).thenReturn(Map.of());

        AiStudioChatService.AgentOneClickDraft draft = co.prepareAgentOneClick(req, "u1").orElseThrow();

        assertEquals("45349679", draft.staffId());
        assertEquals(List.of(Map.of("tableName", "old_table")), draft.design().get("tableDefinitions"));
        assertEquals("{\"tableTypes\":[\"MAIN\"]}", draft.schemaMetadata());
        assertEquals("FIELD SPEC", draft.formatSpec());
        assertEquals("User: earlier question", draft.conversation());
        assertEquals("leave request", draft.userRequest());
        verifyNoInteractions(automationClient, aiGatewayClient);
    }

    @Test
    void gatewayProviderKeepsTheSingleCallOneClick() {
        assertTrue(service.prepareAgentOneClick(request("PROCESS_DESIGN", "x", null), "u1").isEmpty());
        verifyNoInteractions(userRepository, aiGenerationService, automationClient);
    }

    @Test
    void agentOneClickWithoutAnEmployeeIdFailsBeforeSerializingTheDesign() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        employee(" ");

        AiGenerationException ex = assertThrows(AiGenerationException.class,
                () -> co.prepareAgentOneClick(request("PROCESS_DESIGN", "x", null), "u1"));

        assertEquals("AI_STUDIO_NO_EMPLOYEE_ID", ex.getErrorCode());
        verifyNoInteractions(aiGenerationService, automationClient);
    }

    @Test
    void agentOneClickAsksContentOrganizerOncePerPhaseAndRepairsACrossSliceViolationOnTheFailingStep() {
        AiStudioChatServiceImpl co = service(AiStudioChatServiceImpl.Provider.AUTOMATION);
        AiStudioChatService.AgentOneClickDraft draft = new AiStudioChatService.AgentOneClickDraft(
                1L, Map.of(), "{}", "FIELD SPEC", List.of(), "", "leave request", "45349679");
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        java.util.Map<String, String> replyByPhase = Map.of("PROCESS_DESIGN", "p", "TABLE_DESIGN", "t",
                "FORM_DESIGN", "f", "DECISION_DESIGN", "d", "ACTION_DESIGN", "a");
        when(automationClient.chat(messages.capture(), eq("45349679"))).thenAnswer(invocation -> {
            String message = invocation.getArgument(0);
            if (message.contains("AUTOMATIC CORRECTION REQUEST")) return "f2";
            String phase = message.substring("## MODE: PROPOSE\n## PHASE: ".length(), message.indexOf("\n## SCOPE"));
            return replyByPhase.get(phase);
        });
        when(aiResponseParser.parseText(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String reply = invocation.getArgument(0);
            Map<String, Object> data = switch (reply) {
                case "p" -> Map.of("processDefinition", Map.of("bpmnXml", "<x/>"));
                case "t" -> Map.of("tableDefinitions", List.of(), "tableRelations", List.of());
                case "f" -> Map.of("formDefinitions", List.of(Map.of("formName", "bad")));
                case "f2" -> Map.of("formDefinitions", List.of(Map.of("formName", "good")));
                case "d" -> Map.of("decisionDefinitions", List.of());
                default -> Map.of("actionDefinitions", List.of(Map.of("actionName", "submit")));
            };
            return new java.util.HashMap<>(Map.of("reply", "ok " + reply, "generatedData", data));
        });
        when(aiResponseParser.checkCrossSlices(any())).thenAnswer(invocation -> {
            Map<String, Object> data = invocation.getArgument(0);
            if (String.valueOf(data.get("formDefinitions")).contains("bad")) {
                throw new AiGenerationException("AI_FORM_STAGE_BINDING_INVALID", "unbound bpmn:userTask nodes");
            }
            return List.of();
        });
        List<com.developer.dto.AiStudioProposalJobResponse.Progress> progress = new java.util.ArrayList<>();

        StudioChatResult result = co.runAgentOneClick(draft, progress::add);

        assertEquals("ALL", result.proposalScope());
        assertEquals(List.of(Map.of("formName", "good")), result.proposal().get("formDefinitions"));
        assertEquals(6, messages.getAllValues().size());
        assertTrue(messages.getAllValues().get(3).contains("Violation code: AI_FORM_STAGE_BINDING_INVALID"));
        assertEquals(5, progress.size());
        verifyNoInteractions(aiGatewayClient);
    }
}
