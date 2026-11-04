package com.developer.service;

import com.developer.dto.AiStudioChatRequest;
import com.developer.dto.FunctionUnitContextDTO;
import com.developer.enums.AiMode;

import java.util.List;
import java.util.Map;

/**
 * AI Studio Copilot 对话服务。
 *
 * <p>模型链路二选一（{@code ai-generation.ai-studio.provider}）：默认经 Automation 调 Content Organizer 的
 * agent setting；{@code GAIP} 与 AI Generate 同源，走同一个 {@code AiGatewayClient}（集团 gateway + 每用户
 * AMToken / dev 静态 key）。两条链路的回答都交同一个 {@code AiResponseParser} 解析与校验。与 AI Generate 的
 * 区别只在编排：无会话持久化、无锁、无文档与生成数据产出，单轮请求带历史直出回复。</p>
 */
public interface AiStudioChatService {

    /**
     * 单轮结果。普通对话只有 reply；propose 轮次可能附带改动提案
     * （{@code AiGeneratedData} 同构的 Map）与其写入范围。
     */
    record StudioChatResult(String reply, java.util.Map<String, Object> proposal, String proposalScope,
                            com.developer.dto.AiStudioProposalPreview preview) {
        public StudioChatResult(String reply, java.util.Map<String, Object> proposal, String proposalScope) {
            this(reply, proposal, proposalScope, null);
        }

        public StudioChatResult withPreview(com.developer.dto.AiStudioProposalPreview p) {
            return new StudioChatResult(reply, proposal, proposalScope, p);
        }
    }

    /**
     * 发起一轮 Copilot 对话。
     *
     * <p>{@code request.propose=true} 时切换到 AI Generate 的 GENERATION 管线：
     * 带全量设计上下文与 schema 元数据，按当前阶段限定 regenerateScope，
     * 产出可 Apply 的结构化提案。</p>
     *
     * @param userId  当前用户；经 Content Organizer 时取其工号，没有工号以 {@code AI_STUDIO_NO_EMPLOYEE_ID} 失败
     * @param amToken 该用户的 DSP AMToken，透传给 AI gateway 作 Bearer 凭证；
     *                缺失时以 {@code AI_GATEWAY_TOKEN_MISSING} 显式失败，不做匿名调用
     */
    StudioChatResult chat(AiStudioChatRequest request, String userId, String amToken);

    /**
     * 已在请求线程上准备好的提案输入：scope、按 scope 限定的用户消息、序列化后的功能单元上下文与模式。
     *
     * <p>上下文序列化走 JPA 懒加载，必须在带事务的请求线程完成（与 AiGenerationComponentImpl#chatStream
     * 的注释同一条理由）；之后的模型调用可以脱离请求线程。</p>
     *
     * <p>{@code staffId} 非空表示走 Content Organizer：此时 {@code message} 已是按契约分节拼好的整段请求，
     * 工号也在请求线程定下（后台线程里没有当前用户）。为空走网关链路。</p>
     */
    record ProposalDraft(Long functionUnitId, String phase, String scope, String message,
                         FunctionUnitContextDTO context, AiMode mode,
                         List<Map<String, String>> documents, String staffId) {
    }

    /**
     * 提案第一步（请求线程）：校验阶段是否支持结构化提案、序列化上下文、拼装 scoped 消息。
     * 不支持的阶段以 {@code AI_STUDIO_PROPOSAL_UNSUPPORTED_PHASE} 显式失败。
     */
    ProposalDraft prepareProposal(AiStudioChatRequest request, String userId);

    /**
     * 提案第二步（可在后台线程）：经 Content Organizer 或 AI Generate 的 GENERATION 管线调模型并裁剪出 scope 内切片。
     * 推理模型跑一份 PROCESS 切片实测 7 分钟上下，所以调用方不应在 HTTP 请求线程上同步等它。
     */
    StudioChatResult runProposal(ProposalDraft draft, String amToken);

    /**
     * 一键生成第一步（请求线程）：与 {@link #prepareProposal} 同样序列化上下文，但写入范围是整套核心设计
     * （scope {@code ALL}），消息尾追加"一次产出全部核心切片"的指令。{@code request.phase} 只决定结果落在
     * 哪个阶段线程，不限定范围。第二步直接用 {@link #runProposal}。
     */
    ProposalDraft prepareOneClick(AiStudioChatRequest request);

    /**
     * 一键生成经 Content Organizer 时的输入，全部在请求线程上定下（作业线程不碰 JPA、没有当前用户）。
     *
     * @param design         功能单元当前设计（FunctionUnitContextDTO 转成的 Map），各步的 CURRENT DESIGN 由它派生
     * @param schemaMetadata 枚举与结构元数据的 JSON
     * @param formatSpec     GENERATION 提示词全文
     * @param conversation   CONVERSATION 分节（线程历史转写），可空
     * @param userRequest    需求描述或修正指令
     * @param staffId        发起人工号（CO 按它审计）
     */
    record AgentOneClickDraft(Long functionUnitId, Map<String, Object> design, String schemaMetadata,
                              String formatSpec, List<Map<String, String>> documents, String conversation,
                              String userRequest, String staffId) {
    }

    /**
     * 一键生成第一步（请求线程），模型链路是 Content Organizer 时用：返回逐阶段编排的输入；链路是网关时返回空，
     * 调用方走 {@link #prepareOneClick} + {@link #runProposal}。没有工号以 {@code AI_STUDIO_NO_EMPLOYEE_ID} 失败。
     */
    java.util.Optional<AgentOneClickDraft> prepareAgentOneClick(AiStudioChatRequest request, String userId);

    /**
     * 一键生成第二步（后台线程）：按 流程 → 表 → 表单 → 决策 → 动作 逐阶段各问一次 CO，每步的 CURRENT DESIGN
     * 带上本轮上游的产出，结果合并成 scope {@code ALL} 的提案；不写库。某一步失败即整体失败。
     *
     * @param progress 每开始一步回调一次
     */
    StudioChatResult runAgentOneClick(AgentOneClickDraft draft,
                                      java.util.function.Consumer<com.developer.dto.AiStudioProposalJobResponse.Progress> progress);
}
