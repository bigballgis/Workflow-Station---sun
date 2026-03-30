package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表关系请求/响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableRelationDTO {

    private Long id;

    @NotNull
    private Long sourceTableId;

    @NotBlank
    @Size(max = 100)
    private String sourceFieldName;

    @NotBlank
    @Pattern(regexp = "ONE_TO_ONE|ONE_TO_MANY|MANY_TO_MANY")
    private String relationType;

    @NotNull
    private Long targetTableId;

    @NotBlank
    @Size(max = 100)
    private String targetFieldName;
}
