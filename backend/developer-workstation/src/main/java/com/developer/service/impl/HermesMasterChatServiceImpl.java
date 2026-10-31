package com.developer.service.impl;

import com.developer.dto.FunctionUnitContextDTO;
import com.developer.dto.HermesMasterChatRequest;
import com.developer.entity.User;
import com.developer.enums.AiStudioPhase;
import com.developer.exception.AiGenerationException;
import com.developer.repository.UserRepository;
import com.developer.service.AiGenerationService;
import com.developer.service.HermesMasterChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Hermes Master 对话实现。
 *
 * <p>两条模型链路，由 {@code ai-generation.hermes-master.provider} 选：
 * {@code AUTOMATION}（默认）走 {@link HermesMasterAutomationClient} → Content Organizer，提示词是 CO 上的 prompt setting
 * 「Hermes Master」，本类只拼它的变量；{@code GAIP} 是原来的直连集团 AI gateway，提示词用本类的 {@link #SYSTEM_PROMPT}
 * （改提示词时两边要一起改）。两条链路送给模型的上下文是同一段 {@link #userInput}。</p>
 */
@Slf4j
@Service
public class HermesMasterChatServiceImpl implements HermesMasterChatService {

    /** 对话转写的字符预算：超出时从最旧的历史开始丢，永远保住最新一条用户消息。 */
    private static final int TRANSCRIPT_CHAR_BUDGET = 12000;

    /** 设计摘要总预算：HM 不分阶段，把几个主干阶段的名称级摘要拼在一起，整段封顶。 */
    static final int DESIGN_CHAR_BUDGET = 10000;

    /** 设计建议最依赖的几个阶段，按"先骨架后细节"排序；预算不够时后面的先被舍弃。 */
    private static final List<AiStudioPhase> DESIGN_PHASES = List.of(
            AiStudioPhase.VALIDATION, AiStudioPhase.PROCESS_DESIGN, AiStudioPhase.TABLE_DESIGN,
            AiStudioPhase.FORM_DESIGN, AiStudioPhase.ACTION_DESIGN, AiStudioPhase.VIEW_DESIGN);

    private static final String SYSTEM_PROMPT = """
            You are Hermes Master (HM), the assistant mascot of Developer Workstation (DW), the design-time \
            app of Workflow Station, a low-code workflow platform. You live on every DW page and help with \
            three things: answering questions about DW, reviewing and advising on Function Unit design, and \
            guiding the user step by step through DW operations.

            What DW contains (use these exact names, never invent menus or buttons):
            - Sidebar: Function Units (list, grouped by workspace; create, clone, import, recent list) and \
            Automation (automation flows of the user's workspace).
            - A Function Unit is the smallest deployable business unit. Its editor has these tabs, in order: \
            Process Design (BPMN: start/end events, user tasks with assignees, service tasks, gateways with \
            conditions), Table Design (one main table plus sub tables and relation tables, fields, primary \
            and foreign keys), Form Design (drag-and-drop forms bound to tables, sub-table fields, computed \
            fields, lookups, validation rules), View Design (main table views with columns, filters and \
            Business Unit + Role access), Action Design (buttons such as approve, reject, API call or form \
            popup, bound to forms and process nodes), Automation (one automation flow per Automation-type \
            service task, referenced by flow key, with input/output mapping), Connections (email \
            connections), Email Templates, Email Monitors (inbound mail rules), Decision Design (decision \
            tables), Version Management (version history and rollback).
            - Editor header buttons: AI Studio (guided 11-phase AI design with a copilot that can propose \
            changes), AI Generate (generate a whole design from a requirement conversation), Function Unit \
            Settings (basic info plus the Requirements and Function Unit Design documents), Export, \
            Validate, Deploy.
            - Typical build order: process -> tables -> forms -> bind forms to process nodes -> actions -> \
            views -> automation / email -> Validate -> Deploy. Deployed function units run in User Portal; \
            users, roles and business units are managed in Admin Center, not in DW.

            Design advice you can give: a single main table with sub tables for repeating rows, explicit \
            primary and foreign keys on sub tables, one form per process stage instead of one giant form, \
            every user task with an assignee and a bound form, every gateway branch with a condition and a \
            default flow, every Automation service task with a flow key and input/output mapping, views \
            with paired Business Unit + Role access, and running Validate before Deploy.

            Rules: you are advisory only and cannot change anything yourself; for changes point the user \
            to the right tab, or to AI Studio when they want the AI to propose the change. When the \
            message starts with the current function unit design, ground your advice in those real names \
            and never invent ones that are not listed; the listing is name-level only, so say so instead \
            of guessing when you need a detail it does not carry. When you do not know how something \
            works in DW, say so. Answer in the language of the user's latest message. You speak from a \
            small chat bubble: be warm and brief, prefer short numbered steps, and keep answers under \
            about 150 words unless the user asks for more.""";

    /** 模型链路：AUTOMATION = Automation flow → Content Organizer；GAIP = 直连集团 AI gateway。 */
    public enum Provider { AUTOMATION, GAIP }

    private final AiGatewayClient aiGatewayClient;
    private final AiResponseParser aiResponseParser;
    private final AiGenerationService aiGenerationService;
    private final AiStudioContextDigest contextDigest;
    private final HermesMasterAutomationClient automationClient;
    private final UserRepository userRepository;
    private final Provider provider;

    public HermesMasterChatServiceImpl(AiGatewayClient aiGatewayClient, AiResponseParser aiResponseParser,
                                       AiGenerationService aiGenerationService,
                                       AiStudioContextDigest contextDigest,
                                       HermesMasterAutomationClient automationClient,
                                       UserRepository userRepository,
                                       @Value("${ai-generation.hermes-master.provider:AUTOMATION}") Provider provider) {
        this.aiGatewayClient = aiGatewayClient;
        this.aiResponseParser = aiResponseParser;
        this.aiGenerationService = aiGenerationService;
        this.contextDigest = contextDigest;
        this.automationClient = automationClient;
        this.userRepository = userRepository;
        this.provider = provider;
    }

    @Override
    public String chat(HermesMasterChatRequest request, String userId, String amToken) {
        String design = designDigest(request.getFunctionUnitId());
        String userInput = userInput(request, design);
        String text = provider == Provider.AUTOMATION
                ? automationClient.chat(request.getMessage(), userInput, staffId(userId))
                : gatewayReply(userInput, amToken);
        log.info("Hermes Master replied: provider={}, functionUnitId={}, page={}, historySize={}, designChars={}, "
                        + "replyChars={}", provider, request.getFunctionUnitId(), request.getPage(),
                request.getHistory() == null ? 0 : request.getHistory().size(), design.length(), text.length());
        return text.trim();
    }

    /** 送给模型的上下文：所在页面 + 设计摘要 + 对话转写（最后一行是本轮消息）。 */
    private String userInput(HermesMasterChatRequest request, String design) {
        StringBuilder user = new StringBuilder();
        if (request.getPage() != null && !request.getPage().isBlank()) {
            user.append("## Where the user is\nDW page: ").append(request.getPage()).append("\n\n");
        }
        if (!design.isEmpty()) {
            user.append("## Current function unit design (name-level)\n").append(design).append("\n\n");
        }
        return user.append(buildTranscript(request)).toString();
    }

    private String gatewayReply(String userInput, String amToken) {
        Map<String, Object> httpResult = aiGatewayClient.chat(
                new AiPromptBuilder.RenderedPrompt(SYSTEM_PROMPT, userInput), amToken);
        Object reply = aiResponseParser.parse(httpResult).get("reply");
        if (!(reply instanceof String text) || text.isBlank()) {
            throw new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE",
                    "AI gateway returned no usable reply text");
        }
        return text;
    }

    /** Content Organizer 每次调用都要工号（审计到人）；没有就明确失败，不拿别的身份顶替。 */
    private String staffId(String userId) {
        return userRepository.findById(userId)
                .map(User::getEmployeeId)
                .filter(id -> !id.isBlank())
                .map(String::trim)
                .orElseThrow(() -> new AiGenerationException("HERMES_MASTER_NO_EMPLOYEE_ID",
                        "Your account has no employee ID, which Content Organizer requires; "
                                + "ask an administrator to set it in Admin Center"));
    }

    /**
     * 当前功能单元的名称级设计摘要；没有功能单元时为空串。
     *
     * <p>失败降级为无上下文对话，理由与 Copilot 顾问轮相同：没有上下文只是回答得泛，
     * 为此让整轮对话失败是更差的结果。</p>
     */
    private String designDigest(Long functionUnitId) {
        if (functionUnitId == null) {
            return "";
        }
        try {
            FunctionUnitContextDTO context = aiGenerationService.serializeFunctionUnitContext(functionUnitId);
            StringBuilder sb = new StringBuilder();
            for (AiStudioPhase phase : DESIGN_PHASES) {
                String part = contextDigest.digest(phase.name(), context);
                if (part.isEmpty()) continue;
                if (sb.length() + part.length() + 2 > DESIGN_CHAR_BUDGET) break;
                sb.append(part).append("\n\n");
            }
            return sb.toString().trim();
        } catch (RuntimeException e) {
            log.warn("Hermes Master continues without the design digest (functionUnitId={}): {}",
                    functionUnitId, e.getMessage());
            return "";
        }
    }

    /** 历史 + 本轮消息 → 明文转写；从最新的历史往回收，收满预算为止。 */
    private static String buildTranscript(HermesMasterChatRequest request) {
        String tail = "User: " + request.getMessage();
        List<HermesMasterChatRequest.HistoryMessage> history = request.getHistory();
        if (history == null || history.isEmpty()) {
            return tail;
        }
        int budget = TRANSCRIPT_CHAR_BUDGET - tail.length();
        int start = history.size();
        int used = 0;
        String[] rendered = new String[history.size()];
        while (start > 0) {
            HermesMasterChatRequest.HistoryMessage m = history.get(start - 1);
            rendered[start - 1] = ("USER".equals(m.getRole()) ? "User: " : "Assistant: ") + m.getContent();
            int cost = rendered[start - 1].length() + 1;
            if (used + cost > budget) break;
            used += cost;
            start--;
        }
        StringBuilder transcript = new StringBuilder("Conversation so far:\n");
        for (int i = start; i < history.size(); i++) {
            transcript.append(rendered[i]).append('\n');
        }
        return transcript.append(tail).toString();
    }
}
