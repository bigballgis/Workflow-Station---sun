package com.developer.service.impl;

import com.developer.dto.AiStudioChatRequest;
import com.developer.exception.AiGenerationException;
import com.developer.service.AiStudioChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI Studio Copilot 对话实现。
 *
 * <p>Prompt 不走 {@code AiPromptBuilder}（那套模板与 AI Generate 的三阶段/文档协议绑定），
 * 而是本类自带的顾问式 system prompt + 明文对话转写。响应仍交 {@link AiResponseParser}——
 * 它负责 OpenAI 兼容信封的拆取与空响应显式失败，Copilot 只取其中的 {@code reply}。</p>
 */
@Slf4j
@Service
public class AiStudioChatServiceImpl implements AiStudioChatService {

    /** 对话转写的字符预算：超出时从最旧的历史开始丢，永远保住最新一条用户消息。 */
    private static final int TRANSCRIPT_CHAR_BUDGET = 8000;

    private static final String SYSTEM_PROMPT = """
            You are AI Copilot inside AI Studio of Workflow Station, a low-code workflow platform \
            (Developer Workstation). The user designs a Function Unit through 11 guided phases: \
            Process Design (BPMN), Table Design, Form Design, View Design, Action Design, \
            Automation (service tasks), Connections, Email Templates, Email Monitors, \
            Decision Design (decision tables), Validation.
            Current phase: %s — %s
            You are advisory only: you cannot modify the design yourself; the user applies every \
            change in the designer on the left. Give concrete, actionable guidance in the \
            platform's own terms for the current phase. Answer in the same language as the \
            user's latest message. Be concise; prefer short lists over long prose.""";

    /** 阶段 key → system prompt 里的一句话职责描述（模型上下文用，非 UI 文案）。 */
    private static final Map<String, String> PHASE_BLURBS = Map.ofEntries(
            Map.entry("PROCESS_DESIGN", "review the BPMN flow, roles and conditions"),
            Map.entry("TABLE_DESIGN", "define main and sub tables, fields and keys"),
            Map.entry("FORM_DESIGN", "bind forms to tables and lay out fields"),
            Map.entry("VIEW_DESIGN", "configure main table views and access control"),
            Map.entry("ACTION_DESIGN", "define actions triggered from views and forms"),
            Map.entry("AUTOMATION", "configure service tasks and automation flows"),
            Map.entry("CONNECTIONS", "manage external connections used by this unit"),
            Map.entry("EMAIL_TEMPLATES", "author email templates for notifications"),
            Map.entry("EMAIL_MONITORS", "set up inbound email monitors"),
            Map.entry("DECISION_DESIGN", "model decision tables used by the process"),
            Map.entry("VALIDATION", "run the final whole-design checks before deployment"));

    private final AiGatewayClient aiGatewayClient;
    private final AiResponseParser aiResponseParser;

    public AiStudioChatServiceImpl(AiGatewayClient aiGatewayClient, AiResponseParser aiResponseParser) {
        this.aiGatewayClient = aiGatewayClient;
        this.aiResponseParser = aiResponseParser;
    }

    @Override
    public String chat(AiStudioChatRequest request, String amToken) {
        String blurb = PHASE_BLURBS.get(request.getPhase());
        if (blurb == null) {
            // DTO 的 @Pattern 已挡住未知阶段；这里兜的是两处枚举日后失同步的编程错误
            throw new AiGenerationException("AI_STUDIO_UNKNOWN_PHASE",
                    "Unknown AI Studio phase: " + request.getPhase());
        }

        String system = SYSTEM_PROMPT.formatted(request.getPhase(), blurb);
        String user = buildTranscript(request);

        Map<String, Object> httpResult = aiGatewayClient.chat(
                new AiPromptBuilder.RenderedPrompt(system, user), amToken);
        Map<String, Object> parsed = aiResponseParser.parse(httpResult);

        Object reply = parsed.get("reply");
        if (!(reply instanceof String text) || text.isBlank()) {
            // parse() 对空 choices 已显式失败；这里挡的是"内容全被文档块吃掉"的极端情况
            throw new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE",
                    "AI gateway returned no usable reply text");
        }
        log.info("AI Studio copilot replied: functionUnitId={}, phase={}, historySize={}, replyChars={}",
                request.getFunctionUnitId(), request.getPhase(),
                request.getHistory() == null ? 0 : request.getHistory().size(), text.length());
        return text.trim();
    }

    /**
     * 历史 + 本轮消息 → 明文转写。从最旧开始丢直到进预算，最新一条用户消息永不截断
     * （超长消息已被 DTO 的 @Size(max=4000) 挡在门外）。
     */
    private String buildTranscript(AiStudioChatRequest request) {
        StringBuilder tail = new StringBuilder("User: ").append(request.getMessage());

        List<AiStudioChatRequest.HistoryMessage> history = request.getHistory();
        if (history == null || history.isEmpty()) {
            return tail.toString();
        }

        StringBuilder transcript = new StringBuilder();
        int budget = TRANSCRIPT_CHAR_BUDGET - tail.length();
        // 从最新的历史往回收，收满预算为止，再按时间序拼出
        int start = history.size();
        int used = 0;
        while (start > 0) {
            AiStudioChatRequest.HistoryMessage m = history.get(start - 1);
            int cost = m.getContent().length() + 16;
            if (used + cost > budget) break;
            used += cost;
            start--;
        }
        transcript.append("Conversation so far:\n");
        for (int i = start; i < history.size(); i++) {
            AiStudioChatRequest.HistoryMessage m = history.get(i);
            transcript.append("USER".equals(m.getRole()) ? "User: " : "Assistant: ")
                    .append(m.getContent()).append('\n');
        }
        return transcript.append(tail).toString();
    }
}
