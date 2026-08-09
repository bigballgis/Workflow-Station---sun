package com.developer.component.impl;

import com.developer.component.AiStudioChatComponent;
import com.developer.dto.AiStudioChatRequest;
import com.developer.dto.AiStudioChatResponse;
import com.developer.service.AiStudioChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** AI Studio Copilot 组件实现：目前只做鉴权后的直通编排，会话/上下文增强留给后续增量。 */
@Slf4j
@Component
public class AiStudioChatComponentImpl implements AiStudioChatComponent {

    private final AiStudioChatService aiStudioChatService;

    public AiStudioChatComponentImpl(AiStudioChatService aiStudioChatService) {
        this.aiStudioChatService = aiStudioChatService;
    }

    @Override
    public AiStudioChatResponse chat(AiStudioChatRequest request, String userId, String amToken) {
        log.info("AI Studio copilot chat: functionUnitId={}, phase={}, userId={}",
                request.getFunctionUnitId(), request.getPhase(), userId);
        return AiStudioChatResponse.builder()
                .reply(aiStudioChatService.chat(request, amToken))
                .build();
    }
}
