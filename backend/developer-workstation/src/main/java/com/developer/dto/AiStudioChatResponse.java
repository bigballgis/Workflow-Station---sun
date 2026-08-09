package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI Studio Copilot 单轮回复。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStudioChatResponse {

    private String reply;
}
