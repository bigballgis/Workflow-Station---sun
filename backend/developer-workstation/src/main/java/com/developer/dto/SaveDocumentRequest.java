package com.developer.dto;

import com.developer.enums.AiDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存用户编辑的文档请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveDocumentRequest {

    @NotNull
    private Long functionUnitId;

    @NotNull
    private AiDocumentType documentType;

    @NotBlank
    private String content;
}
