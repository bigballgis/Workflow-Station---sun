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
    
    @NotBlank(message = "表名不能为空")
    @Size(max = 100, message = "表名长度不能超过100个字符")
    private String tableName;
    
    @NotNull(message = "表类型不能为空")
    private TableType tableType;
    
    private String description;
    
    private List<FieldDefinitionRequest> fields;
}
