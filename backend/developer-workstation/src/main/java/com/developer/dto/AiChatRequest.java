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
}
