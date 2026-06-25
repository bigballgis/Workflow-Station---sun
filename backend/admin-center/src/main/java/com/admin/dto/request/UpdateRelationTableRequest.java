package com.admin.dto.request;

import com.platform.common.enums.RelationDataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 更新 Relation Table 请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRelationTableRequest {

    @Size(max = 100, message = "表名长度不能超过100")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "表名只能包含小写字母、数字和下划线，且必须以小写字母开头")
    private String tableName;

    @Size(max = 200, message = "显示名长度不能超过200")
    private String displayName;

    private String description;

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

        /**
         * 字段 ID（更新已有字段时提供，新增字段时为 null）
         */
        private Long id;

        @Size(max = 100, message = "字段名长度不能超过100")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字段名只能包含小写字母、数字和下划线，且必须以小写字母开头")
        private String fieldName;

        private RelationDataType dataType;

        private Integer length;

        private Integer precision;

        private Integer scale;

        private Boolean nullable;

        private Boolean isPrimaryKey;

        @Size(max = 500, message = "默认值长度不能超过500")
        private String defaultValue;

        private String displayName;

        private Integer sortOrder;

        private Map<String, Object> pkGeneration;

        private Boolean isForeignKey;

        private Long refTableId;

        private List<String> refPrimaryKeyFields;

        /** readonly | hidden */
        private String fkDisplayMode;
    }
}
