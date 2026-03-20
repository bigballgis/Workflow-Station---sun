package com.developer.dto;

import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 会话信息响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionResponse {

    private String sessionId;

    private Long functionUnitId;

    private AiPhase currentPhase;

    private AiMode mode;

    private AiSessionStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
