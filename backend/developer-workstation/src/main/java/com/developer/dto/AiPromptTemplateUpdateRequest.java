package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 覆盖某一段 AI 提示词（编辑保存 / 导入文件都走这里）
 */
@Data
public class AiPromptTemplateUpdateRequest {

    /** 提示词全文。上限 200000 字符——三段内置提示词最长的也不到 14KB，留足余量同时挡住滥用。 */
    @NotBlank
    @Size(max = 200_000)
    private String content;
}
