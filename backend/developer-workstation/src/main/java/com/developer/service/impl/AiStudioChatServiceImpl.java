package com.developer.service.impl;

import com.developer.dto.AiStudioChatRequest;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.entity.User;
import com.developer.enums.AiDocumentType;
import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiStudioPhase;
import com.developer.exception.AiGenerationException;
import com.developer.repository.UserRepository;
import com.developer.service.AiGenerationService;
import com.developer.service.AiPromptTemplateService;
import com.developer.service.AiStudioChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI Studio Copilot 对话实现。
 *
 * <p>Prompt 不走 {@code AiPromptBuilder}（那套模板与 AI Generate 的三阶段/文档协议绑定），
 * 而是本类自带的顾问式 system prompt + 明文对话转写。响应仍交 {@link AiResponseParser}——
 * 它负责 OpenAI 兼容信封的拆取与空响应显式失败，Copilot 只取其中的 {@code reply}。</p>
 *
 * <p>两条模型链路，由 {@code ai-generation.ai-studio.provider} 选：{@code AUTOMATION}（默认）经
 * {@link AiStudioAutomationClient} 调 Content Organizer 的 agent setting「Function Unit」，请求按
 * docs/design/developer-workstation-ai-studio-co-output-contract.md 分节拼成一条消息，回答仍交
 * {@link AiResponseParser} 解析与校验；{@code GAIP} 是原来的直连集团 AI gateway。一键生成在 {@code AUTOMATION}
 * 下由 {@link AiStudioOneClickOrchestrator} 逐阶段编排（CO 每次只有一个专家回答），{@code GAIP} 下仍是单次
 * {@code scope=ALL} 生成。</p>
 */
@Slf4j
@Service
public class AiStudioChatServiceImpl implements AiStudioChatService {

    /** 对话转写的字符预算：超出时从最旧的历史开始丢，永远保住最新一条用户消息。 */
    private static final int TRANSCRIPT_CHAR_BUDGET = 16000;
    private static final int ADVISORY_REQUIREMENTS_CHAR_CAP = 12000;
    private static final int ADVISORY_DESIGN_CHAR_CAP = 8000;

    /** 历史里单条提案 JSON 进转写的上限：够模型看清上一轮提了什么，又不至于把设计上下文挤出窗口。 */
    static final int HISTORY_PROPOSAL_CHAR_CAP = 6000;

    private static final com.fasterxml.jackson.databind.ObjectMapper HISTORY_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** CO 消息里的设计上下文与元数据：上下文里可能有时间类型，注册 classpath 上的模块。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper AGENT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

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
            user's latest message. Be concise; prefer short lists over long prose.
            The user message may start with the function unit's current design for this phase. \
            When it does, ground every answer in those real names (tables, fields, forms, nodes, \
            flow keys) and never invent ones that are not listed. The listing is name-level only: \
            say so instead of guessing when a detail it does not carry is needed.""";

    /** 按业务键 upsert 的 scope：提案只列新增/修改项，未提及的对象保持不变。 */
    static final Set<String> UPSERT_SCOPES = Set.of(
            "EMAIL_TEMPLATES", "CONNECTIONS", "EMAIL_MONITORS", "VIEWS", "SERVICE_TASK_BINDINGS");

    /**
     * scope → 允许写入的 generatedData 切片，与 {@code AiWriteServiceImpl#clearScopedData}
     * 的清理范围对齐（TABLES 清的是整个表图谱，所以连关系一起）。模型在 scoped 轮次里经常
     * 顺手带上范围外的切片（如 processDefinition）——写入层会照单全写，撞上"每 FU 一份流程
     * 定义"这类唯一约束，所以提案返回前与 Apply 落库前都必须按这张表裁剪。
     */
    private static final Map<String, Set<String>> SCOPE_SLICES = Map.ofEntries(
            Map.entry("TABLES", Set.of("tableDefinitions", "tableRelations")),
            Map.entry("TABLE_RELATIONS", Set.of("tableRelations")),
            Map.entry("FORMS", Set.of("formDefinitions")),
            Map.entry("ACTIONS", Set.of("actionDefinitions")),
            Map.entry("DECISIONS", Set.of("decisionDefinitions")),
            Map.entry("PROCESS", Set.of("processDefinition")),
            Map.entry("EMAIL_TEMPLATES", Set.of("emailTemplates")),
            Map.entry("CONNECTIONS", Set.of("emailConnections")),
            Map.entry("EMAIL_MONITORS", Set.of("emailMonitorRules")),
            Map.entry("VIEWS", Set.of("mainTableViews")),
            Map.entry("SERVICE_TASK_BINDINGS", Set.of("serviceTaskBindings")));

    /** 一键生成的写入范围（AiWriteService 的全量替换） */
    public static final String SCOPE_ALL = "ALL";

    /**
     * 一键生成产出的切片：六类核心设计。视图、邮件与 service task 绑定不在内——连接要人工补凭证、
     * 监控依赖连接、绑定依赖已发布的 Automation flow，一次生成里带上它们只会让整份结果过不了引用校验。
     */
    public static final Set<String> ONE_CLICK_SLICES = Set.of("tableDefinitions", "tableRelations",
            "formDefinitions", "actionDefinitions", "decisionDefinitions", "processDefinition");

    /** scope 允许的切片 key；ALL 返回全部。供本类与 Apply 编排（component）共用。 */
    public static Set<String> allowedSlices(String scope) {
        if ("ALL".equalsIgnoreCase(scope)) {
            return SCOPE_SLICES.values().stream()
                    .flatMap(Set::stream)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        Set<String> slices = SCOPE_SLICES.get(scope);
        if (slices == null) {
            throw new AiGenerationException("AI_STUDIO_UNKNOWN_SCOPE", "Unknown proposal scope: " + scope);
        }
        return slices;
    }

    /** 模型链路：AUTOMATION = Automation flow → Content Organizer agent setting；GAIP = 直连集团 AI gateway。 */
    public enum Provider { AUTOMATION, GAIP }

    private final AiGatewayClient aiGatewayClient;
    private final AiResponseParser aiResponseParser;
    private final AiGenerationService aiGenerationService;
    private final AiStudioContextDigest contextDigest;
    private final FunctionUnitDocumentService documentService;
    private final AiStudioAutomationClient automationClient;
    private final AiPromptTemplateService promptTemplateService;
    private final UserRepository userRepository;
    private final Provider provider;

    /** 一键生成逐阶段编排的总时限：与提案作业的最长运行时间同一个配置，作业超时前编排先停下 */
    @Value("${ai-generation.studio.proposal-max-runtime-minutes:30}")
    private long oneClickBudgetMinutes = 30;

    /** 单次 CO 调用的上限（与 {@link AiStudioAutomationClient} 同一个配置）：剩余时间不够就不再发下一步 */
    @Value("${ai-generation.ai-studio.automation.timeout-seconds:310}")
    private long agentCallTimeoutSeconds = 310;

    public AiStudioChatServiceImpl(AiGatewayClient aiGatewayClient, AiResponseParser aiResponseParser,
                                   AiGenerationService aiGenerationService,
                                   AiStudioContextDigest contextDigest,
                                   FunctionUnitDocumentService documentService,
                                   AiStudioAutomationClient automationClient,
                                   AiPromptTemplateService promptTemplateService,
                                   UserRepository userRepository,
                                   @Value("${ai-generation.ai-studio.provider:AUTOMATION}") Provider provider) {
        this.aiGatewayClient = aiGatewayClient;
        this.aiResponseParser = aiResponseParser;
        this.aiGenerationService = aiGenerationService;
        this.contextDigest = contextDigest;
        this.documentService = documentService;
        this.automationClient = automationClient;
        this.promptTemplateService = promptTemplateService;
        this.userRepository = userRepository;
        this.provider = provider;
    }

    @Override
    public StudioChatResult chat(AiStudioChatRequest request, String userId, String amToken) {
        if (request.isPropose()) {
            // 同步形态只留给单测与直接调用；HTTP 入口走 prepare + 后台 run（见 AiStudioProposalJobService）
            return runProposal(prepareProposal(request, userId), amToken);
        }
        String reply = provider == Provider.AUTOMATION
                ? agentAdvisoryChat(request, staffId(userId))
                : advisoryChat(request, amToken);
        return new StudioChatResult(reply, null, null);
    }

    /**
     * 提案第一步：校验阶段、序列化上下文（JPA 懒加载，必须在请求线程）、拼 scoped 消息。
     */
    @Override
    public ProposalDraft prepareProposal(AiStudioChatRequest request, String userId) {
        // propose 轮次的写入范围：AI Studio 阶段 → AiWriteService 的 regenerateScope；
        // 邮件三阶段、视图与 service task 绑定的切片是 upsert 语义（见 AiWriteServiceImpl）
        String scope = AiStudioPhase.fromKey(request.getPhase())
                .flatMap(AiStudioPhase::proposalScope)
                .orElseThrow(() -> new AiGenerationException("AI_STUDIO_PROPOSAL_UNSUPPORTED_PHASE",
                        "Phase " + request.getPhase() + " has no structured proposal scope; "
                                + "proposals are supported for: " + Arrays.stream(AiStudioPhase.values())
                                .filter(p -> p.proposalScope().isPresent()).toList()));
        FunctionUnitContextDTO context =
                aiGenerationService.serializeFunctionUnitContext(request.getFunctionUnitId());
        AiMode mode = aiGenerationService.determineMode(request.getFunctionUnitId());
        List<Map<String, String>> documents = documentService.latestContents(request.getFunctionUnitId())
                .entrySet().stream()
                .map(e -> Map.of("documentType", e.getKey().name(), "content", e.getValue()))
                .toList();
        if (provider == Provider.AUTOMATION) {
            // 工号与整条消息都在请求线程定下：后台作业线程里既没有当前用户，也不该再碰 JPA
            String message = AiStudioAgentMessage.build("PROPOSE", request.getPhase(), scope, toJson(context),
                    toJson(aiGenerationService.getSchemaMetadata()), promptTemplateService.resolve("GENERATION"),
                    documents, historyTranscript(request), request.getMessage());
            return new ProposalDraft(request.getFunctionUnitId(), request.getPhase(), scope, message, context, mode,
                    documents, staffId(userId));
        }
        return new ProposalDraft(request.getFunctionUnitId(), request.getPhase(), scope,
                buildProposalMessage(request, scope), context, mode, documents, null);
    }

    @Override
    public ProposalDraft prepareOneClick(AiStudioChatRequest request) {
        FunctionUnitContextDTO context =
                aiGenerationService.serializeFunctionUnitContext(request.getFunctionUnitId());
        AiMode mode = aiGenerationService.determineMode(request.getFunctionUnitId());
        List<Map<String, String>> documents = documentService.latestContents(request.getFunctionUnitId())
                .entrySet().stream()
                .map(e -> Map.of("documentType", e.getKey().name(), "content", e.getValue()))
                .toList();
        return new ProposalDraft(request.getFunctionUnitId(), request.getPhase(), SCOPE_ALL,
                buildOneClickMessage(request), context, mode, documents, null);
    }

    @Override
    public java.util.Optional<AgentOneClickDraft> prepareAgentOneClick(AiStudioChatRequest request, String userId) {
        if (provider != Provider.AUTOMATION) {
            return java.util.Optional.empty();
        }
        // 工号先查：没有工号直接 4xx，不必先做一次全量上下文序列化
        String staffId = staffId(userId);
        Map<String, Object> design = AGENT_JSON.convertValue(
                aiGenerationService.serializeFunctionUnitContext(request.getFunctionUnitId()),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {
                });
        List<Map<String, String>> documents = documentService.latestContents(request.getFunctionUnitId())
                .entrySet().stream()
                .map(e -> Map.of("documentType", e.getKey().name(), "content", e.getValue()))
                .toList();
        return java.util.Optional.of(new AgentOneClickDraft(request.getFunctionUnitId(), design,
                toJson(aiGenerationService.getSchemaMetadata()), promptTemplateService.resolve("GENERATION"),
                documents, historyTranscript(request), request.getMessage(), staffId));
    }

    @Override
    public StudioChatResult runAgentOneClick(AgentOneClickDraft draft,
                                             java.util.function.Consumer<com.developer.dto.AiStudioProposalJobResponse.Progress> progress) {
        StudioChatResult result = new AiStudioOneClickOrchestrator(
                (message, accept) -> askAgent(message, draft.staffId(), accept),
                aiResponseParser::checkCrossSlices,
                java.time.Duration.ofMinutes(oneClickBudgetMinutes),
                java.time.Duration.ofSeconds(agentCallTimeoutSeconds), java.time.Clock.systemUTC())
                .run(draft, progress);
        log.info("AI Studio one-click via Content Organizer: functionUnitId={}, slices={}, replyChars={}",
                draft.functionUnitId(), result.proposal().keySet(),
                result.reply() == null ? 0 : result.reply().length());
        return result;
    }

    /**
     * 提案第二步：复用 AI Generate 的 GENERATION 管线（{@code callAiModel} 自带上下文序列化时的
     * 提示词模板、schema 元数据、校验失败自动修复重试）。sessionId 用随机 UUID——
     * Copilot 无会话持久化，管线只拿它查历史（查不到即空），对话上下文已折进 draft.message。
     */
    @Override
    public StudioChatResult runProposal(ProposalDraft draft, String amToken) {
        String scope = draft.scope();
        Map<String, Object> parsed = draft.staffId() != null
                ? agentProposal(draft)
                : aiGenerationService.callAiModel(
                        UUID.randomUUID(), draft.message(), AiPhase.GENERATION, draft.mode(),
                        draft.context(), draft.functionUnitId(), draft.documents(), scope, amToken);

        Object reply = parsed.get("reply");
        Object generatedData = parsed.get("generatedData");
        @SuppressWarnings("unchecked")
        Map<String, Object> proposal = generatedData instanceof Map<?, ?> m
                ? new java.util.LinkedHashMap<>((Map<String, Object>) m)
                : null;
        if (proposal != null) {
            proposal.keySet().retainAll(SCOPE_ALL.equals(scope) ? ONE_CLICK_SLICES : allowedSlices(scope));
            if (proposal.isEmpty()) proposal = null;
        }
        if (proposal == null && (!(reply instanceof String r) || r.isBlank())) {
            // 既没有数据块也没有解释文本：显式失败，别让前端拿到一张空白卡
            throw new AiGenerationException("AI_STUDIO_PROPOSAL_EMPTY",
                    "The model returned neither a proposal data block nor an explanation");
        }
        log.info("AI Studio proposal round: provider={}, functionUnitId={}, phase={}, scope={}, hasProposal={}, "
                        + "replyChars={}", draft.staffId() != null ? Provider.AUTOMATION : Provider.GAIP,
                draft.functionUnitId(), draft.phase(), scope, proposal != null,
                reply instanceof String r ? r.length() : 0);
        return new StudioChatResult(
                reply instanceof String r && !r.isBlank() ? r.trim() : null,
                proposal,
                proposal != null ? scope : null);
    }

    /**
     * propose 轮次的用户消息 = 对话转写 + 显式的 scope 限定指令。
     *
     * <p>GENERATION 提示词在 NEW 模式（空功能单元）下默认产出整套设计——包括一份大概率过不了
     * 平台校验的 BPMN，而校验失败会让整个提案轮次失败。这里从源头限定：只产出 scope 内的切片。
     * 即便模型仍旧多给，{@code allowedSlices} 的裁剪也会兜住，但少生成就少一次校验失败的机会。</p>
     */
    private String buildProposalMessage(AiStudioChatRequest request, String scope) {
        String upsertNote = UPSERT_SCOPES.contains(scope)
                ? "This slice is applied as an upsert keyed by name: list ONLY the items you add or change; "
                        + "every existing item you do not list stays untouched, nothing is deleted.\n"
                : "";
        return buildTranscript(request) + "\n\n"
                + "========== Scoped change request (system-provided, highest priority) ==========\n"
                + "Regenerate ONLY the '" + scope + "' slice of the design to fulfil the user's latest request.\n"
                + "The GENERATED_DATA block must contain exactly these keys and nothing else: "
                + allowedSlices(scope) + ".\n"
                + upsertNote
                + "Do NOT output any other slice (no process, forms, actions, decisions or tables outside the "
                + "scope), do NOT rename the function unit, and do NOT include an icon.\n"
                + "========== End of scoped change request ==========";
    }

    /**
     * 一键生成的用户消息 = 对话转写（含本轮需求描述）+ 整体生成指令。Requirements / Design 文档由管线另行带上。
     */
    private String buildOneClickMessage(AiStudioChatRequest request) {
        return buildTranscript(request) + "\n\n"
                + "========== One-click generation request (system-provided, highest priority) ==========\n"
                + "Generate the COMPLETE function unit design in a single pass from the user's requirements above "
                + "and the Requirements / Function Unit Design documents (when provided). It REPLACES the whole "
                + "existing design, so every slice must be complete and consistent with the others "
                + "(forms bind existing tables and fields, the process references existing forms and actions).\n"
                + "The GENERATED_DATA block must contain exactly these keys and nothing else: "
                + new java.util.TreeSet<>(ONE_CLICK_SLICES) + ". Use an empty array for a slice the requirements "
                + "do not call for.\n"
                + "Do NOT output email templates, connections, email monitors, main table views or service task "
                + "bindings, do NOT rename the function unit, and do NOT include an icon.\n"
                + "========== End of one-click generation request ==========";
    }

    /**
     * CO 提案：消息已在请求线程拼好（{@link #prepareProposal}）。回答与网关链路走同一个解析器，标记块解析、归一化与
     * 语义校验完全一样；可修的结构违规照网关链路的做法带着校验原话再问一次，只修一次。
     */
    private Map<String, Object> agentProposal(ProposalDraft draft) {
        return askAgent(draft.message(), draft.staffId(), java.util.function.UnaryOperator.identity());
    }

    /**
     * 问 CO 一次：解析回答后交给 {@code accept}（一键生成在这里并入并校验跨切片引用）；解析或 {@code accept}
     * 抛出可修违规时带着校验原话再问一次，只修一次。
     */
    private Map<String, Object> askAgent(String message, String staffId,
                                         java.util.function.UnaryOperator<Map<String, Object>> accept) {
        String reply = automationClient.chat(message, staffId);
        try {
            return accept.apply(aiResponseParser.parseText(reply));
        } catch (AiGenerationException e) {
            if (!AiGenerationServiceImpl.REPAIRABLE_ERROR_CODES.contains(e.getErrorCode())) {
                throw e;
            }
            log.warn("AI Studio agent proposal rejected by platform validation ({}), asking once more with the "
                    + "violation fed back: {}", e.getErrorCode(), e.getMessage());
            String repaired = automationClient.chat(AiGenerationServiceImpl.buildRepairMessage(message, e), staffId);
            return accept.apply(aiResponseParser.parseText(repaired));
        }
    }

    /**
     * CO 顾问轮：完整设计上下文（专家按名字落地回答）+ 文档 + 历史 + 本轮问题，回答原样返回。
     * 上下文序列化失败时与网关链路一样降级为空设计——没有上下文只是回答得泛。
     */
    private String agentAdvisoryChat(AiStudioChatRequest request, String staffId) {
        AiStudioPhase phase = AiStudioPhase.fromKey(request.getPhase())
                .orElseThrow(() -> new AiGenerationException("AI_STUDIO_UNKNOWN_PHASE",
                        "Unknown AI Studio phase: " + request.getPhase()));
        String context;
        try {
            context = toJson(aiGenerationService.serializeFunctionUnitContext(request.getFunctionUnitId()));
        } catch (RuntimeException e) {
            // FALLBACK(ux): 与网关顾问轮的 advisoryDigest 同一取舍——没有上下文只是回答得泛，不让整轮对话失败
            log.warn("AI Studio agent chat continues without the design context (functionUnitId={}): {}",
                    request.getFunctionUnitId(), e.getMessage());
            context = "{}";
        }
        List<Map<String, String>> documents = documentService.latestContents(request.getFunctionUnitId())
                .entrySet().stream()
                .map(e -> Map.of("documentType", e.getKey().name(), "content", e.getValue()))
                .toList();
        String message = AiStudioAgentMessage.build("ADVISORY", phase.name(), phase.proposalScope().orElse("NONE"),
                context, toJson(aiGenerationService.getSchemaMetadata()), null, documents,
                historyTranscript(request), request.getMessage());
        String reply = automationClient.chat(message, staffId).trim();
        log.info("AI Studio agent replied: functionUnitId={}, phase={}, historySize={}, messageChars={}, replyChars={}",
                request.getFunctionUnitId(), request.getPhase(),
                request.getHistory() == null ? 0 : request.getHistory().size(), message.length(), reply.length());
        return reply;
    }

    /** Content Organizer 每次调用都要工号（审计到人）；没有就明确失败，不拿别的身份顶替。 */
    private String staffId(String userId) {
        return userRepository.findById(userId)
                .map(User::getEmployeeId)
                .filter(id -> !id.isBlank())
                .map(String::trim)
                .orElseThrow(() -> new AiGenerationException("AI_STUDIO_NO_EMPLOYEE_ID",
                        "Your account has no employee ID, which Content Organizer requires; "
                                + "ask an administrator to set it in Admin Center"));
    }

    private static String toJson(Object value) {
        try {
            return AGENT_JSON.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AiGenerationException("AI_STUDIO_CONTEXT_SERIALIZATION_FAILED",
                    "Could not serialize the design for Content Organizer: " + e.getOriginalMessage());
        }
    }

    private String advisoryChat(AiStudioChatRequest request, String amToken) {
        // DTO 的 @Pattern 已挡住未知阶段；这里兜的是绕过 DTO 校验的直接调用
        AiStudioPhase phase = AiStudioPhase.fromKey(request.getPhase())
                .orElseThrow(() -> new AiGenerationException("AI_STUDIO_UNKNOWN_PHASE",
                        "Unknown AI Studio phase: " + request.getPhase()));

        String system = SYSTEM_PROMPT.formatted(phase.name(), phase.advisoryHint());
        String digest = advisoryDigest(request);
        String user = (digest.isEmpty() ? ""
                : "## Current design (" + request.getPhase() + ")\n" + digest + "\n\n")
                + documentsContext(request.getFunctionUnitId(), phase)
                + buildTranscript(request);

        Map<String, Object> httpResult = aiGatewayClient.chat(
                new AiPromptBuilder.RenderedPrompt(system, user), amToken);
        Map<String, Object> parsed = aiResponseParser.parse(httpResult);

        Object reply = parsed.get("reply");
        if (!(reply instanceof String text) || text.isBlank()) {
            // parse() 对空 choices 已显式失败；这里挡的是"内容全被文档块吃掉"的极端情况
            throw new AiGenerationException("AI_GATEWAY_EMPTY_RESPONSE",
                    "AI gateway returned no usable reply text");
        }
        log.info("AI Studio copilot replied: functionUnitId={}, phase={}, historySize={}, digestChars={}, replyChars={}",
                request.getFunctionUnitId(), request.getPhase(),
                request.getHistory() == null ? 0 : request.getHistory().size(), digest.length(), text.length());
        return text.trim();
    }

    /**
     * 顾问轮的"当前设计现状"：按阶段裁剪的名称级摘要（见 {@link AiStudioContextDigest}）。
     *
     * <p>失败一律降级为无上下文对话——顾问式回答没有上下文只是泛泛而谈，为此让整轮对话失败
     * 是更差的结果。超大功能单元的 {@code AI_CONTEXT_TOO_LARGE} 也走这条路。</p>
     */
    private String advisoryDigest(AiStudioChatRequest request) {
        try {
            return contextDigest.digest(request.getPhase(),
                    aiGenerationService.serializeFunctionUnitContext(request.getFunctionUnitId()));
        } catch (RuntimeException e) {
            log.warn("AI Studio copilot chat continues without the design digest (functionUnitId={}): {}",
                    request.getFunctionUnitId(), e.getMessage());
            return "";
        }
    }

    /**
     * 顾问轮带上功能单元的文档：Requirements 全文（有上限）+ Design 里当前阶段那一节。
     * 两份都没有时返回空串，提示词与原来一致。
     */
    private String documentsContext(Long functionUnitId, AiStudioPhase phase) {
        Map<AiDocumentType, String> docs = documentService.latestContents(functionUnitId);
        StringBuilder sb = new StringBuilder();
        String requirements = docs.get(AiDocumentType.REQUIREMENTS);
        if (requirements != null && !requirements.isBlank()) {
            sb.append("## Requirements document (the business intent)\n")
                    .append(capped(requirements, ADVISORY_REQUIREMENTS_CHAR_CAP)).append("\n\n");
        }
        String design = docs.get(AiDocumentType.DESIGN);
        if (design != null && !design.isBlank()) {
            String label = phase.label();
            String section = designSection(design, label);
            if (section != null) {
                sb.append("## Function Unit Design document — ").append(label).append(" section\n")
                        .append(capped(section, ADVISORY_DESIGN_CHAR_CAP)).append("\n\n");
            } else {
                // FALLBACK(ux): 旧文档还没整理成按阶段分节的结构——带整份（有上限），缺的只是聚焦，不会写错数据
                sb.append("## Function Unit Design document\n")
                        .append(capped(design, ADVISORY_DESIGN_CHAR_CAP)).append("\n\n");
            }
        }
        return sb.toString();
    }

    /** 按文档模板的二级标题取一节（不含标题行）；没有这一节返回 null。 */
    static String designSection(String design, String label) {
        String[] lines = design.split("\n", -1);
        StringBuilder section = null;
        for (String line : lines) {
            boolean heading = line.startsWith("## ");
            if (section != null) {
                if (heading) break;
                section.append(line).append('\n');
            } else if (heading && line.substring(3).strip().equalsIgnoreCase(label)) {
                section = new StringBuilder();
            }
        }
        return section == null ? null : section.toString().strip();
    }

    private static String capped(String text, int cap) {
        return text.length() <= cap ? text : text.substring(0, cap) + "\n…(document truncated)";
    }

    /**
     * 历史 + 本轮消息 → 明文转写。从最旧开始丢直到进预算，最新一条用户消息永不截断
     * （超长消息已被 DTO 的 @Size(max=4000) 挡在门外）。
     */
    private String buildTranscript(AiStudioChatRequest request) {
        String tail = "User: " + request.getMessage();
        String history = historyLines(request, TRANSCRIPT_CHAR_BUDGET - tail.length());
        return history.isEmpty() ? tail : "Conversation so far:\n" + history + tail;
    }

    /** CO 消息的 CONVERSATION 分节：只有历史，本轮消息另放 USER REQUEST。 */
    private String historyTranscript(AiStudioChatRequest request) {
        return historyLines(request, TRANSCRIPT_CHAR_BUDGET).strip();
    }

    /** 从最新的历史往回收，收满预算为止，再按时间序拼出（每条一行，带换行）；没有历史为空串。 */
    private static String historyLines(AiStudioChatRequest request, int budget) {
        List<AiStudioChatRequest.HistoryMessage> history = request.getHistory();
        if (history == null || history.isEmpty()) {
            return "";
        }
        int start = history.size();
        int used = 0;
        String[] rendered = new String[history.size()];
        while (start > 0) {
            AiStudioChatRequest.HistoryMessage m = history.get(start - 1);
            rendered[start - 1] = renderHistoryEntry(m);
            int cost = rendered[start - 1].length() + 1;
            if (used + cost > budget) break;
            used += cost;
            start--;
        }
        StringBuilder lines = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            lines.append(rendered[i]).append('\n');
        }
        return lines.toString();
    }

    /**
     * 单条历史 → 转写行。ASSISTANT 条目若附带上一轮的结构化提案，把提案 JSON（裁到
     * {@link #HISTORY_PROPOSAL_CHAR_CAP}）也写进去——否则"把刚才那个模板改一下"这类二次修改，
     * 模型只看得到自己说过"Here is the proposed change"，不知道提了什么。
     */
    private static String renderHistoryEntry(AiStudioChatRequest.HistoryMessage m) {
        StringBuilder sb = new StringBuilder("USER".equals(m.getRole()) ? "User: " : "Assistant: ")
                .append(m.getContent());
        if (!"USER".equals(m.getRole()) && m.getProposal() != null && !m.getProposal().isEmpty()) {
            String json;
            try {
                json = HISTORY_JSON.writeValueAsString(m.getProposal());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                json = String.valueOf(m.getProposal());
            }
            if (json.length() > HISTORY_PROPOSAL_CHAR_CAP) {
                json = json.substring(0, HISTORY_PROPOSAL_CHAR_CAP) + " …(truncated)";
            }
            sb.append("\n[Proposed change").append(m.getProposalScope() != null ? ", scope=" + m.getProposalScope() : "")
                    .append("; not yet applied unless the current data already reflects it] ").append(json);
        }
        return sb.toString();
    }
}
