package com.developer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI Studio Copilot 对话请求。
 *
 * <p>与 AI Generate 的 {@link AiChatRequest} 刻意分开：Copilot 是无会话、无锁、无文档产出的
 * 轻量顾问式对话，历史由前端随请求带上（不落库），phase 是 AI Studio 的 11 阶段而不是
 * {@code AiPhase} 三阶段。</p>
 */
@Data
public class AiStudioChatRequest {

    @NotNull
    private Long functionUnitId;

    /** AI Studio 阶段 key，见前端 utils/aiStudioDraft.ts 的 AI_STUDIO_PHASES。 */
    @NotBlank
    @Pattern(regexp = "PROCESS_DESIGN|TABLE_DESIGN|FORM_DESIGN|VIEW_DESIGN|ACTION_DESIGN|AUTOMATION"
            + "|CONNECTIONS|EMAIL_TEMPLATES|EMAIL_MONITORS|DECISION_DESIGN|VALIDATION")
    private String phase;

    @NotBlank
    @Size(max = 4000)
    private String message;

    /** 近期对话历史（旧→新），服务端还会按字符预算再截断一次。 */
    @Valid
    @Size(max = 20)
    private List<HistoryMessage> history;

    @Data
    public static class HistoryMessage {

        @NotBlank
        @Pattern(regexp = "USER|ASSISTANT")
        private String role;

        @NotBlank
        @Size(max = 4000)
        private String content;
    }
}
