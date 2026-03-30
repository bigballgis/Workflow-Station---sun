package com.portal.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 子表绑定数据 DTO
 */
@Data
@Builder
public class SubTableBindingData {
    private Long bindingId;
    private String tableName;
    private String bindingType;
    private String bindingMode;
    private List<Map<String, Object>> columns;
    private List<Map<String, Object>> data;
}
