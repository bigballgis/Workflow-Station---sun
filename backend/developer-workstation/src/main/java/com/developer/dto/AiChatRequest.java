package com.developer.dto;

import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotNull
    private Long functionUnitId;

    private String sessionId;

    @NotBlank
    @Size(max = 50000)
    private String message;

    @NotNull
    private AiPhase phase;

    @NotNull
    private AiMode mode;

    private String regenerateScope;

    /**
     * 只重出本相位的产物，不推进会话相位。
     *
     * <p>聊天区的 Requirements / Design 文档卡上的 Regenerate 走这条路：用户想换一份需求文档，
     * 但会话可能已经走到 GENERATION。缺了这个标记，模型回的 {@code phaseComplete} 会把会话相位
     * 倒推回 DESIGN，顶部进度条跟着退，并且前端收到 {@code phase_complete} 后会自动触发下一相位，
     * 把已有的设计文档和生成结果一并盖掉——用户要的只是重出一份文档。</p>
     */
    private boolean regenerateOnly;
}
