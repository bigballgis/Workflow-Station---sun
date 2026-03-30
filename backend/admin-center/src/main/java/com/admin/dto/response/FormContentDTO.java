package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表单内容 DTO
 * 包含 configJson 数据和关联的表绑定信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormContentDTO {
    private String id;
    private String name;
    private String sourceId;
    private String data;       // configJson 字符串
    private String type;       // "FORM"
    private List<TableBindingDTO> tableBindings;
}
