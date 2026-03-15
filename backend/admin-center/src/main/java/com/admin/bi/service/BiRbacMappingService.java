package com.admin.bi.service;

import com.admin.bi.dto.request.RbacMappingUpdateRequest;
import com.admin.bi.dto.response.RbacMappingResponse;
import com.admin.bi.dto.response.SupersetRoleResponse;
import com.admin.bi.dto.response.SyncResultResponse;

import java.util.List;

/**
 * RBAC 映射 Service 接口
 */
public interface BiRbacMappingService {

    /**
     * 触发手动同步 Superset 角色
     */
    SyncResultResponse syncSupersetRoles();

    /**
     * 获取所有已同步的 Superset 角色列表
     */
    List<SupersetRoleResponse> listSupersetRoles();

    /**
     * 查询 RBAC 映射列表（支持 roleName/roleType 筛选）
     */
    List<RbacMappingResponse> listMappings(String roleName, String roleType);

    /**
     * 更新某个 Sys_Role 的 Superset_Role 映射（全量替换）
     */
    void updateMapping(String sysRoleId, RbacMappingUpdateRequest request);

    /**
     * 获取给定系统角色 ID 列表对应的有效（ACTIVE）Superset 角色 ID 列表（去重）
     */
    List<Integer> getEffectiveSupersetRoleIds(List<String> sysRoleIds);
}
