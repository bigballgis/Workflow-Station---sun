package com.developer.service.impl;

import com.developer.dto.AiStudioProposalJobResponse.Progress;
import com.developer.exception.AiGenerationException;
import com.developer.service.AiStudioChatService.AgentOneClickDraft;
import com.developer.service.AiStudioChatService.StudioChatResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStudioOneClickOrchestratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, Object> PROCESS = Map.of("bpmnXml", "<definitions>new</definitions>");
    private static final List<Map<String, Object>> TABLES = List.of(Map.of("tableName", "leave_request"));
    private static final List<Map<String, Object>> FORMS = List.of(Map.of("formName", "leave_form"));
    private static final List<Map<String, Object>> ACTIONS = List.of(Map.of("actionName", "submit"));

    /** 一份已有设计：每个核心切片都有旧值，再加一个一键生成不碰的切片 */
    private static AgentOneClickDraft draft() {
        Map<String, Object> design = new LinkedHashMap<>();
        design.put("functionUnitId", 7);
        design.put("processDefinition", Map.of("bpmnXml", "<definitions>old</definitions>"));
        design.put("tableDefinitions", List.of(Map.of("tableName", "old_table")));
        design.put("tableRelations", List.of(Map.of("sourceTableName", "old_table")));
        design.put("formDefinitions", List.of(Map.of("formName", "old_form")));
        design.put("decisionDefinitions", List.of(Map.of("decisionKey", "old_decision")));
        design.put("actionDefinitions", List.of(Map.of("actionName", "old_action")));
        design.put("mainTableViews", List.of(Map.of("viewName", "kept")));
        return new AgentOneClickDraft(7L, design, "{\"enums\":{}}", "FORMAT", List.of(), "User: earlier",
                "Leave request with manager approval", "45349679");
    }

    private static Map<String, Object> answer(String reply, Map<String, Object> data) {
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("reply", reply);
        parsed.put("generatedData", data);
        return parsed;
    }

    private static Deque<Map<String, Object>> happyAnswers() {
        return new ArrayDeque<>(List.of(
                answer("Process drawn.", Map.of("processDefinition", PROCESS,
                        // 专家多给的切片必须被裁掉：表单此时还不该出现
                        "formDefinitions", List.of(Map.of("formName", "stray")))),
                answer("Tables done.", Map.of("tableDefinitions", TABLES, "tableRelations", List.of())),
                answer("Forms done.", Map.of("formDefinitions", FORMS)),
                // 没有决策表：空数组
                answer("No decision table is needed.", Map.of("decisionDefinitions", List.of())),
                answer("Actions done.", Map.of("actionDefinitions", ACTIONS))));
    }

    /**
     * 假 CO：按顺序给出回答，记下每条请求；回答没有数据块、或 accept 抛出可修违规时取下一条回答重试一次，
     * 合计只重试一次（与 askAgent 同一语义）。
     */
    private static class FakeAgent implements AiStudioOneClickOrchestrator.Agent {
        final Deque<Map<String, Object>> answers;
        final List<String> messages = new ArrayList<>();
        Runnable onAsk = () -> { };

        FakeAgent(Deque<Map<String, Object>> answers) {
            this.answers = answers;
        }

        @Override
        public Map<String, Object> ask(String message, UnaryOperator<Map<String, Object>> accept) {
            messages.add(message);
            onAsk.run();
            Map<String, Object> first = answers.removeFirst();
            if (first.get("generatedData") == null) {
                messages.add("REPAIR:NO_DATA_BLOCK");
                return accept.apply(answers.removeFirst());
            }
            try {
                return accept.apply(first);
            } catch (AiGenerationException e) {
                if (!AiGenerationServiceImpl.REPAIRABLE_ERROR_CODES.contains(e.getErrorCode())) throw e;
                messages.add("REPAIR:" + e.getErrorCode());
                return accept.apply(answers.removeFirst());
            }
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-03T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static AiStudioOneClickOrchestrator orchestrator(FakeAgent agent,
                                                            Function<Map<String, Object>, List<String>> check,
                                                            Clock clock) {
        return orchestrator(agent, check, design -> { }, clock);
    }

    private static AiStudioOneClickOrchestrator orchestrator(FakeAgent agent,
                                                            Function<Map<String, Object>, List<String>> check,
                                                            Consumer<Map<String, Object>> tableNameCheck,
                                                            Clock clock) {
        return new AiStudioOneClickOrchestrator(agent, check, tableNameCheck, Duration.ofMinutes(30),
                Duration.ofSeconds(310), clock);
    }

    private static AiStudioOneClickOrchestrator orchestrator(FakeAgent agent) {
        return orchestrator(agent, data -> List.of(), Clock.systemUTC());
    }

    private static String section(String message, String name) {
        int start = message.indexOf("## " + name + "\n") + name.length() + 4;
        int end = message.indexOf("\n## ", start);
        return message.substring(start, end < 0 ? message.length() : end);
    }

    private static String header(String message, String name) {
        int start = message.indexOf("## " + name + ": ") + name.length() + 5;
        return message.substring(start, message.indexOf('\n', start));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> currentDesign(String message) throws Exception {
        return JSON.readValue(section(message, "CURRENT DESIGN"), Map.class);
    }

    @Test
    void asksTheFivePhasesInOrderAsProposeRequestsAndMergesTheirSlicesIntoOneAllProposal() {
        FakeAgent agent = new FakeAgent(happyAnswers());
        List<Progress> progress = new ArrayList<>();

        StudioChatResult result = orchestrator(agent).run(draft(), progress::add);

        assertEquals(5, agent.messages.size());
        assertEquals(List.of("PROCESS_DESIGN", "TABLE_DESIGN", "FORM_DESIGN", "DECISION_DESIGN", "ACTION_DESIGN"),
                agent.messages.stream().map(m -> header(m, "PHASE")).toList());
        assertEquals(List.of("PROCESS", "TABLES", "FORMS", "DECISIONS", "ACTIONS"),
                agent.messages.stream().map(m -> header(m, "SCOPE")).toList());
        assertTrue(agent.messages.stream().allMatch(m -> header(m, "MODE").equals("PROPOSE")));
        assertTrue(agent.messages.get(0).contains("## FORMAT SPECIFICATION"));
        assertTrue(section(agent.messages.get(2), "USER REQUEST").startsWith("Leave request with manager approval"));
        assertTrue(section(agent.messages.get(2), "USER REQUEST").contains("This is step 3 of 5"));
        assertEquals("User: earlier", section(agent.messages.get(4), "CONVERSATION"));

        assertEquals(List.of(new Progress(1, 5, "PROCESS_DESIGN"), new Progress(2, 5, "TABLE_DESIGN"),
                new Progress(3, 5, "FORM_DESIGN"), new Progress(4, 5, "DECISION_DESIGN"),
                new Progress(5, 5, "ACTION_DESIGN")), progress);

        assertEquals("ALL", result.proposalScope());
        assertEquals(AiStudioChatServiceImpl.ONE_CLICK_SLICES, result.proposal().keySet());
        assertEquals(PROCESS, result.proposal().get("processDefinition"));
        assertEquals(TABLES, result.proposal().get("tableDefinitions"));
        assertEquals(FORMS, result.proposal().get("formDefinitions"));
        assertEquals(List.of(), result.proposal().get("decisionDefinitions"));
        assertEquals(ACTIONS, result.proposal().get("actionDefinitions"));
        assertTrue(result.reply().startsWith("### 1/5 Process Design\nProcess drawn."));
        assertTrue(result.reply().contains("\n\n### 4/5 Decision Design\nNo decision table is needed."));
    }

    @Test
    void eachStepSeesThisRunsUpstreamSlicesItsOwnOldSliceAndEmptyDownstreamSlices() throws Exception {
        FakeAgent agent = new FakeAgent(happyAnswers());

        orchestrator(agent).run(draft(), p -> { });

        Map<String, Object> processStep = currentDesign(agent.messages.get(0));
        assertEquals(Map.of("bpmnXml", "<definitions>old</definitions>"), processStep.get("processDefinition"));
        assertEquals(List.of(), processStep.get("tableDefinitions"));
        assertEquals(List.of(), processStep.get("actionDefinitions"));

        Map<String, Object> formStep = currentDesign(agent.messages.get(2));
        assertEquals(PROCESS, formStep.get("processDefinition"));
        assertEquals(TABLES, formStep.get("tableDefinitions"));
        assertEquals(List.of(), formStep.get("tableRelations"));
        assertEquals(List.of(Map.of("formName", "old_form")), formStep.get("formDefinitions"));
        assertEquals(List.of(), formStep.get("decisionDefinitions"));
        assertEquals(List.of(), formStep.get("actionDefinitions"));
        // 一键生成不碰的切片与目录原样带上
        assertEquals(List.of(Map.of("viewName", "kept")), formStep.get("mainTableViews"));

        Map<String, Object> actionStep = currentDesign(agent.messages.get(4));
        assertEquals(FORMS, actionStep.get("formDefinitions"));
        assertEquals(List.of(), actionStep.get("decisionDefinitions"));
        assertEquals(List.of(Map.of("actionName", "old_action")), actionStep.get("actionDefinitions"));

        // 流程步多给的表单被裁掉了：表单步之前它仍是空的下游
        assertEquals(List.of(), currentDesign(agent.messages.get(1)).get("formDefinitions"));
    }

    @Test
    void crossSliceCheckRunsOnTheMergedDesignAtTheFormAndActionStepsAndItsRepairsAreKept() {
        List<String> checkedAt = new ArrayList<>();
        Function<Map<String, Object>, List<String>> check = data -> {
            checkedAt.add(data.containsKey("actionDefinitions") ? "actions" : "forms");
            assertEquals(FORMS, data.get("formDefinitions"));
            if (data.containsKey("actionDefinitions")) {
                // 守门规则就地修正动作（R3/R4）：修正后的结果就是提案
                data.put("actionDefinitions", List.of(Map.of("actionName", "repaired")));
                return List.of("repaired something");
            }
            return List.of();
        };
        FakeAgent agent = new FakeAgent(happyAnswers());

        StudioChatResult result = orchestrator(agent, check, Clock.systemUTC()).run(draft(), p -> { });

        assertEquals(List.of("forms", "actions"), checkedAt);
        assertEquals(List.of(Map.of("actionName", "repaired")), result.proposal().get("actionDefinitions"));
    }

    @Test
    void aCrossSliceViolationIsRaisedThroughTheAgentSoTheStepIsAskedOnceMore() {
        Deque<Map<String, Object>> answers = happyAnswers();
        List<Map<String, Object>> list = new ArrayList<>(answers);
        list.set(2, answer("Forms v1.", Map.of("formDefinitions", List.of(Map.of("formName", "bad")))));
        list.add(3, answer("Forms v2.", Map.of("formDefinitions", FORMS)));
        Function<Map<String, Object>, List<String>> check = data -> {
            if (String.valueOf(data.get("formDefinitions")).contains("bad")) {
                throw new AiGenerationException("AI_FORM_STAGE_BINDING_INVALID", "unbound bpmn:userTask nodes");
            }
            return List.of();
        };
        FakeAgent agent = new FakeAgent(new ArrayDeque<>(list));

        StudioChatResult result = orchestrator(agent, check, Clock.systemUTC()).run(draft(), p -> { });

        assertEquals("REPAIR:AI_FORM_STAGE_BINDING_INVALID", agent.messages.get(3));
        assertEquals(FORMS, result.proposal().get("formDefinitions"));
        assertTrue(result.reply().contains("Forms v2."));
    }

    /** 表名全平台唯一：表步产出被占用的表名时，表专家带原话重做这一步，后面的步骤看到的是改过名的表 */
    @Test
    void aTakenTableNameIsRaisedAtTheTableStepSoTheTableExpertRenamesItBeforeLaterSteps() throws Exception {
        List<Map<String, Object>> list = new ArrayList<>(happyAnswers());
        List<Map<String, Object>> renamed = List.of(Map.of("tableName", "hr_leave_request"));
        list.add(2, answer("Tables renamed.", Map.of("tableDefinitions", renamed, "tableRelations", List.of())));
        List<Object> checkedTables = new ArrayList<>();
        Consumer<Map<String, Object>> tableNameCheck = design -> {
            checkedTables.add(design.get("tableDefinitions"));
            if (String.valueOf(design.get("tableDefinitions")).contains("=leave_request")) {
                throw new AiGenerationException(AiTableNameAvailability.TABLE_NAME_TAKEN,
                        AiTableNameAvailability.describe(List.of("leave_request")));
            }
        };
        FakeAgent agent = new FakeAgent(new ArrayDeque<>(list));

        StudioChatResult result = orchestrator(agent, data -> List.of(), tableNameCheck, Clock.systemUTC())
                .run(draft(), p -> { });

        assertEquals("REPAIR:" + AiTableNameAvailability.TABLE_NAME_TAKEN, agent.messages.get(2));
        // 只在表步检查：原名一次、改名后一次
        assertEquals(List.of(TABLES, renamed), checkedTables);
        assertEquals(renamed, result.proposal().get("tableDefinitions"));
        // 表单步的 CURRENT DESIGN 已是改过名的表
        assertEquals(renamed, currentDesign(agent.messages.get(3)).get("tableDefinitions"));
    }

    @Test
    void aStepWithoutDataFailsTheRunWithItsStepNumberAndStopsAsking() {
        List<Map<String, Object>> list = new ArrayList<>(happyAnswers());
        list.set(1, answer("Tables:\n```json\n{}\n```", null));
        list.add(2, answer("I need the process first.", null));
        FakeAgent agent = new FakeAgent(new ArrayDeque<>(list));

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> orchestrator(agent).run(draft(), p -> { }));

        assertEquals(AiStudioOneClickOrchestrator.STEP_NO_DATA, e.getErrorCode());
        assertEquals("Step 2/5 (Table Design): Content Organizer returned no data: I need the process first.",
                e.getMessage());
        assertEquals("REPAIR:NO_DATA_BLOCK", agent.messages.get(2));
        assertEquals(3, agent.messages.size());
    }

    @Test
    void aDecisionStepWithoutADataBlockEvenAfterTheRepairMeansNoDecisionTables() {
        List<Map<String, Object>> list = new ArrayList<>(happyAnswers());
        list.set(3, answer("No decision table is needed.", null));
        list.add(4, answer("Still no decision table.", null));
        FakeAgent agent = new FakeAgent(new ArrayDeque<>(list));

        StudioChatResult result = orchestrator(agent).run(draft(), p -> { });

        assertEquals("REPAIR:NO_DATA_BLOCK", agent.messages.get(4));
        assertEquals(List.of(), result.proposal().get("decisionDefinitions"));
        assertEquals(ACTIONS, result.proposal().get("actionDefinitions"));
    }

    @Test
    void aContentOrganizerFailureKeepsItsCodeAndNamesTheStep() {
        FakeAgent agent = new FakeAgent(happyAnswers()) {
            @Override
            public Map<String, Object> ask(String message, UnaryOperator<Map<String, Object>> accept) {
                if (messages.size() == 2) throw new AiGenerationException("AI_STUDIO_AGENT_FAILED", "boom");
                return super.ask(message, accept);
            }
        };

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> orchestrator(agent).run(draft(), p -> { }));

        assertEquals("AI_STUDIO_AGENT_FAILED", e.getErrorCode());
        assertEquals("Step 3/5 (Form Design): boom", e.getMessage());
    }

    @Test
    void aCancelledJobDoesNotStartTheNextStep() {
        FakeAgent agent = new FakeAgent(happyAnswers());
        agent.onAsk = () -> Thread.currentThread().interrupt();
        try {
            AiGenerationException e = assertThrows(AiGenerationException.class,
                    () -> orchestrator(agent).run(draft(), p -> { }));

            assertEquals("AI_STUDIO_PROPOSAL_CANCELLED", e.getErrorCode());
            assertEquals(1, agent.messages.size());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void aStepIsNotStartedWhenLessThanOneCallIsLeftOfTheBudget() {
        MutableClock clock = new MutableClock();
        FakeAgent agent = new FakeAgent(happyAnswers());
        agent.onAsk = () -> clock.now = clock.now.plus(Duration.ofMinutes(13));

        AiGenerationException e = assertThrows(AiGenerationException.class,
                () -> orchestrator(agent, data -> List.of(), clock).run(draft(), p -> { }));

        // 0 分、13 分开始的两步都发了；26 分 + 310 秒超过 30 分钟，第三步不再发
        assertEquals("AI_STUDIO_PROPOSAL_TIMED_OUT", e.getErrorCode());
        assertTrue(e.getMessage().startsWith("Step 3/5 (Form Design): "));
        assertEquals(2, agent.messages.size());
    }
}
