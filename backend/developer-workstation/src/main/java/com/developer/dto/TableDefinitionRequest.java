package com.developer.dto;

import com.developer.enums.TableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表定义请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDefinitionRequest {
    
    @NotBlank(message = "{validation.table_name_required}")
    @Size(max = 100, message = "{validation.table_name_max_length}")
    private String tableName;
    
    @NotNull(message = "{validation.table_type_required}")
    private TableType tableType;
    
    @Size(max = 200, message = "{validation.table_display_name_max_length}")
    private String tableDisplayName;

    private String description;

    /** 主表 Request ID 配置(有序字段 + 分隔符);仅 MAIN 表有意义,可空。 */
    private RequestIdConfig requestIdConfig;

    private List<FieldDefinitionRequest> fields;
}
