package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表绑定 DTO
 * 描述表单与数据表之间的绑定关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableBindingDTO {
    private Long bindingId;
    private String bindingType;    // "PRIMARY", "SUB", "RELATED"
    private String bindingMode;    // "EDITABLE", "READONLY"
    private String foreignKeyField;
    private Integer sortOrder;
    private String tableName;
    private String tableType;
    private String tableDescription;
}
