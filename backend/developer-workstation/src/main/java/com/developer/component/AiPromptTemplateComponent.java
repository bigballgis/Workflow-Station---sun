package com.developer.component;

import com.developer.dto.AiPromptTemplateResponse;
import com.developer.service.AiPromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 提示词管理的编排层（Controller → Component → Service → Repository）。
 */
@Component
@RequiredArgsConstructor
public class AiPromptTemplateComponent {

    private final AiPromptTemplateService aiPromptTemplateService;

    public List<AiPromptTemplateResponse> list() {
        return aiPromptTemplateService.list();
    }

    public AiPromptTemplateResponse get(String phase) {
        return aiPromptTemplateService.get(phase);
    }

    public AiPromptTemplateResponse save(String phase, String content) {
        return aiPromptTemplateService.save(phase, content);
    }

    public AiPromptTemplateResponse reset(String phase) {
        return aiPromptTemplateService.reset(phase);
    }
}
