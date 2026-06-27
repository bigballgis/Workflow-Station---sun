package com.admin.service;

import com.admin.entity.RelationTableAccess;

import java.util.Collection;
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
     *
     * @param permissionLevel READONLY | READ_WRITE（为空默认 READ_WRITE）
     */
    RelationTableAccess addAccess(Long tableId, String targetType, String targetId, String permissionLevel);

    /**
     * 批量设置访问配置（替换现有配置），统一使用同一个权限级别
     */
    void batchSetAccess(Long tableId, List<String> targetIds, String permissionLevel);

    /**
     * 修改某条授权的权限级别（原地切换 READONLY / READ_WRITE）
     */
    RelationTableAccess updatePermissionLevel(String accessId, String permissionLevel);

    /**
     * 删除访问配置
     */
    void removeAccess(String accessId);

    /**
     * 解析用户在某表上的权限级别。
     * 给定一组角色，返回 READ_WRITE（任一角色读写则读写）/ READONLY / null（无任何授权）。
     * 注意：User Portal 的「按当前 active role」解析在 Portal 服务内单独实现，这里供 admin / 通用场景使用。
     */
    String resolvePermissionLevel(Long tableId, Collection<String> userRoleIds);

    /**
     * 检查用户是否有权限访问某个表（任一角色有授权即可）
     */
    boolean hasAccess(Long tableId, List<String> userRoleIds);
}
