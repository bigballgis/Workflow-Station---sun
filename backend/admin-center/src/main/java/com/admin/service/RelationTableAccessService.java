package com.admin.service;

import com.admin.entity.RelationTableAccess;

import java.util.List;

/**
 * Relation Table 访问权限服务接口
 * 参照 FunctionUnitAccessService 模式，通过 Business Role 控制 User Portal 中的数据可见性
 */
public interface RelationTableAccessService {

    /**
     * 获取表的所有访问配置
     */
    List<RelationTableAccess> getAccessConfig(Long tableId);

    /**
     * 添加访问配置
     */
    RelationTableAccess addAccess(Long tableId, String targetType, String targetId);

    /**
     * 批量设置访问配置（替换现有配置）
     */
    void batchSetAccess(Long tableId, List<String> targetIds);

    /**
     * 删除访问配置
     */
    void removeAccess(String accessId);

    /**
     * 检查用户是否有权限访问某个表
     * 根据用户的 Business Role 判断
     */
    boolean hasAccess(Long tableId, List<String> userRoleIds);
}
