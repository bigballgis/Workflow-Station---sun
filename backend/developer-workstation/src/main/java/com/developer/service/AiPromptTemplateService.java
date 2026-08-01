package com.developer.service;

import com.developer.dto.AiPromptTemplateResponse;

import java.util.List;

/**
 * 三段 AI 提示词（REQUIREMENTS / DESIGN / GENERATION）的读取与运行时覆盖。
 *
 * <p>内置默认值来自镜像里的 {@code resources/ai-prompts/<phase>.txt}，覆盖值存 {@code dw_ai_prompt_templates}。
 * {@link com.developer.service.impl.AiPromptBuilder} 每轮对话通过 {@link #resolve(String)} 取当前生效文本，
 * 因此改完立即生效，不需要重启或重新部署。</p>
 */
public interface AiPromptTemplateService {

    /** 支持的三个相位，顺序即前端展示顺序。 */
    List<String> phases();

    /** 三段提示词的当前生效内容 + 来源。 */
    List<AiPromptTemplateResponse> list();

    /** 单段提示词的当前生效内容 + 来源；phase 非法抛 {@code AiGenerationException}。 */
    AiPromptTemplateResponse get(String phase);

    /**
     * 当前实际送给模型的提示词全文：有覆盖值用覆盖值，否则用内置默认值。
     *
     * @param phase 相位，调用方保证已是三者之一
     */
    String resolve(String phase);

    /** 写入/更新覆盖值，返回写入后的状态。 */
    AiPromptTemplateResponse save(String phase, String content);

    /** 删除覆盖值，恢复到内置默认值，返回恢复后的状态。 */
    AiPromptTemplateResponse reset(String phase);
}
