package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 公共表创建/更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonTableRequest {

    @NotBlank(message = "表编码不能为空")
    @Size(max = 100, message = "表编码长度不能超过100个字符")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "表编码只能包含字母、数字和下划线，且必须以字母开头")
    private String code;

    @NotBlank(message = "表名称不能为空")
    @Size(max = 200, message = "表名称长度不能超过200个字符")
    private String name;

    private String description;

    private String status;

    private List<FieldDefinitionRequest> fields;
}
