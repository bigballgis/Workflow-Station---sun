package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 一段 AI 提示词的当前生效内容 + 来源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptTemplateResponse {

    /** REQUIREMENTS / DESIGN / GENERATION */
    private String phase;

    /** 当前实际送给模型的全文（覆盖值优先，无覆盖时即内置默认值） */
    private String content;

    /** BUILT_IN = 用镜像里的 ai-prompts/*.txt；CUSTOM = 用库里的覆盖值 */
    private String source;

    /** 内置默认值全文，供前端做"对比/还原"提示（始终返回，与 source 无关） */
    private String defaultContent;

    /** 覆盖值的最后编辑人；source=BUILT_IN 时为 null */
    private String updatedBy;

    /** 覆盖值的最后编辑时间；source=BUILT_IN 时为 null */
    private Instant updatedAt;
}
