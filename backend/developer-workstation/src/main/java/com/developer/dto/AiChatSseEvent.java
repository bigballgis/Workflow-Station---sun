package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatSseEvent {

    private String eventType;

    private Object data;
}
