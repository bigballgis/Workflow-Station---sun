package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据表内容 DTO
 * 包含表结构 JSON 数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableContentDTO {
    private String id;
    private String name;
    private String sourceId;
    private String data;       // 表结构 JSON 字符串
    private String type;       // "DATA_TABLE"
}
