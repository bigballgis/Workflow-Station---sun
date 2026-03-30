package com.developer.service;

import com.developer.entity.RelationLookupConfig;

import java.util.List;

/**
 * Relation Table Lookup 配置服务接口
 */
public interface RelationLookupService {

    /**
     * 获取 Lookup 组件配置
     */
    RelationLookupConfig getLookupConfig(Long formId, String componentId);

    /**
     * 保存 Lookup 配置
     */
    RelationLookupConfig saveLookupConfig(Long formId, String componentId, LookupConfigDTO config);

    /**
     * 获取当前 Form 已绑定的 Relation Table 的 View 列表（仅返回已绑定的表）
     */
    List<BoundViewDTO> getBoundViews(Long formId);

    /**
     * Lookup 配置 DTO
     */
    record LookupConfigDTO(
            Long viewConfigId,
            Long tableId,
            String searchFields,
            String displayField
    ) {}

    /**
     * 已绑定 View DTO
     */
    record BoundViewDTO(
            Long bindingId,
            Long tableId,
            String tableName,
            String displayName,
            Long viewConfigId
    ) {}
}
