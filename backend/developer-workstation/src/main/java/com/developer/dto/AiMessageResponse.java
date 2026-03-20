package com.developer.dto;

import com.developer.enums.AiMessageRole;
import com.developer.enums.AiPhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI 消息信息响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageResponse {

    private Long id;

    private String sessionId;

    private AiMessageRole role;

    private String content;

    private AiPhase phase;

    private Instant createdAt;
}
