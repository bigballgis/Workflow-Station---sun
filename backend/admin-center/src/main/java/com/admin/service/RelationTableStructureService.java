package com.admin.service;

import com.admin.dto.request.CreateRelationTableRequest;
import com.admin.dto.request.UpdateRelationTableRequest;
import com.admin.dto.response.RelationTableResponse;

import java.util.List;

/**
 * Relation Table 表结构管理服务接口
 */
public interface RelationTableStructureService {

    /**
     * 创建表定义
     * 验证表名唯一性，保存表定义和字段定义，状态设为 DRAFT
     */
    RelationTableResponse createTable(CreateRelationTableRequest request);

    /**
     * 更新表定义
     * 更新基本信息和字段定义，状态设为 DRAFT
     */
    RelationTableResponse updateTable(Long id, UpdateRelationTableRequest request);

    /**
     * 删除表定义
     * 检查是否有绑定关系，有则拒绝删除
     */
    void deleteTable(Long id);

    /**
     * 获取所有表定义列表
     */
    List<RelationTableResponse> getTableList();

    /**
     * 根据 ID 获取表定义详情
     */
    RelationTableResponse getTableById(Long id);

    /**
     * 切换启用/禁用状态
     */
    RelationTableResponse toggleEnabled(Long id, Boolean enabled);

    /**
     * 切换门户可见性
     */
    RelationTableResponse togglePortalVisibility(Long id, Boolean portalVisible);

    /**
     * Check whether a table name is available platform-wide (RT + DW).
     */
    boolean isTableNameAvailable(String tableName, Long excludeTableId);
}
