package com.developer.service;

import com.developer.entity.FormTableBinding;
import com.developer.entity.SubTableViewConfig;
import com.platform.common.dto.RelationFieldDTO;

import java.util.List;

/**
 * Sub-Table View 配置服务接口
 */
public interface SubTableViewService {

    /**
     * 获取 View 配置
     */
    SubTableViewConfig getViewConfig(Long bindingId);

    /**
     * 获取或创建 View 配置（如果不存在则创建默认配置）
     */
    SubTableViewConfig getOrCreateViewConfig(Long bindingId);

    /**
     * 保存 View 字段配置
     */
    SubTableViewConfig saveViewConfig(Long bindingId, List<ViewFieldDTO> fields);

    /**
     * 创建默认 View 配置（包含所有字段）
     */
    SubTableViewConfig createDefaultViewConfig(Long bindingId);

    /**
     * 获取子表的所有可用字段
     */
    List<RelationFieldDTO> getAvailableFields(Long tableId);

    /**
     * 获取子表绑定的可用字段（通过 binding）
     */
    List<RelationFieldDTO> getAvailableFieldsByBinding(FormTableBinding binding);

    /**
     * 获取 View 配置并返回 DTO 格式
     */
    ViewConfigDTO getViewConfigDTO(Long bindingId);

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

    /**
     * View 配置 DTO（用于API返回）
     */
    record ViewConfigDTO(
            Long id,
            Long bindingId,
            Long tableId,
            List<ViewFieldDTO> viewFields
    ) {}
}
