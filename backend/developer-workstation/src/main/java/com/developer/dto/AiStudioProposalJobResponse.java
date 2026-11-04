package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * AI Studio Copilot 改动提案的后台作业快照。
 *
 * <p>提案一轮要跑分钟级的 GENERATION 管线，同步 HTTP 会被网关（Kong 300s）掐断，
 * 所以提交立即返回作业 id，前端按 id 轮询直到 {@code status} 进入终态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStudioProposalJobResponse {

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    /**
     * 多步作业（经 Content Organizer 的一键生成）正在跑第几步；单步作业为空。只在进程内，不落库：
     * DW 重启后没跑完的作业一律判为已中断，进度没有意义。
     *
     * @param phase 这一步所属的 AI Studio 阶段 key
     */
    public record Progress(int step, int totalSteps, String phase) {
    }

    private String jobId;

    private Long functionUnitId;

    private String phase;

    /** 发起时的原始消息（"重新发起"用） */
    private String message;

    /** 发起人展示名（队友看到"某某正在生成提案"） */
    private String authorName;

    private Status status;

    private Instant submittedAt;

    private Instant startedAt;

    private Instant finishedAt;

    private Progress progress;

    /** 以下三项仅 SUCCEEDED 时有值，语义同 {@link AiStudioChatResponse}。 */
    private String reply;

    private Map<String, Object> proposal;

    private String proposalScope;

    /** 提案卡预览，见 {@link AiStudioProposalPreview} */
    private AiStudioProposalPreview preview;

    /** 以下两项仅 FAILED 时有值。 */
    private String errorCode;

    private String errorMessage;
}
