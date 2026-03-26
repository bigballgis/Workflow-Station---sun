package com.developer.service;

import com.developer.entity.RelationViewConfig;
import com.platform.common.dto.RelationFieldDTO;

import java.util.List;

/**
 * Relation Table View 配置服务接口
 */
public interface RelationViewService {

    /**
     * 获取 View 配置（包含已选字段列表）
     */
    RelationViewConfig getViewConfig(Long bindingId);

    /**
     * 保存 View 字段配置
     */
    RelationViewConfig saveViewConfig(Long bindingId, List<ViewFieldDTO> fields);

    /**
     * 获取已部署表的所有可用字段
     */
    List<RelationFieldDTO> getAvailableFields(Long tableId);

    /**
     * View 字段配置 DTO
     */
    record ViewFieldDTO(
            String fieldName,
            String displayLabel,
            Integer columnWidth,
            Integer sortOrder,
            Boolean visible
    ) {}
}
