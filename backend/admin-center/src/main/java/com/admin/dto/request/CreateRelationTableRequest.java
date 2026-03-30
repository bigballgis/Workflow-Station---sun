package com.admin.dto.request;

import com.platform.common.enums.RelationDataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建 Relation Table 请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRelationTableRequest {

    @NotBlank(message = "表名不能为空")
    @Size(max = 100, message = "表名长度不能超过100")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "表名只能包含小写字母、数字和下划线，且必须以小写字母开头")
    private String tableName;

    @Size(max = 200, message = "显示名长度不能超过200")
    private String displayName;

    private String description;

    @NotEmpty(message = "字段定义列表不能为空")
    @Valid
    private List<FieldDefinitionRequest> fieldDefinitions;

    /**
     * 字段定义请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDefinitionRequest {

        @NotBlank(message = "字段名不能为空")
        @Size(max = 100, message = "字段名长度不能超过100")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字段名只能包含小写字母、数字和下划线，且必须以小写字母开头")
        private String fieldName;

        @jakarta.validation.constraints.NotNull(message = "数据类型不能为空")
        private RelationDataType dataType;

        private Integer length;

        private Integer precision;

        private Integer scale;

        @Builder.Default
        private Boolean nullable = true;

        @Builder.Default
        private Boolean isPrimaryKey = false;

        @Size(max = 500, message = "默认值长度不能超过500")
        private String defaultValue;

        private String comment;

        private Integer sortOrder;
    }
}
