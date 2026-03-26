package com.developer.service;

import com.platform.common.dto.RelationTableDTO;

import java.util.List;

/**
 * Relation Table 绑定服务接口
 * 管理 Form 与 Relation Table 的绑定关系
 */
public interface RelationTableBindingService {

    /**
     * 获取所有已部署状态的 Relation Table（可绑定列表）
     */
    List<RelationTableDTO> getAvailableTables();

    /**
     * 绑定 Relation Table 到 Form，同时自动创建 RelationViewConfig
     */
    Long bindRelationTable(Long formId, Long tableId);

    /**
     * 解除绑定，同步删除 RelationViewConfig 及其 RelationViewField
     */
    void unbindRelationTable(Long formId, Long bindingId);

    /**
     * 获取当前 Form 的 Relation Table 绑定列表
     */
    List<RelationTableBindingDTO> getBindings(Long formId);

    /**
     * 绑定列表返回 DTO
     */
    record RelationTableBindingDTO(
            Long bindingId,
            Long tableId,
            String tableName,
            String displayName,
            String bindingType,
            Long viewConfigId
    ) {}
}
