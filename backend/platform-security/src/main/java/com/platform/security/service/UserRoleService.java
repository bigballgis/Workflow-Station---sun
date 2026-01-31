package com.platform.security.service;

import com.platform.security.dto.UserEffectiveRole;

import java.util.List;

/**
 * 用户角色服务接口
 * 提供统一的用户角色计算功能，供三个前端后端使用
 */
public interface UserRoleService {
    
    /**
     * 获取用户的所有有效角色（包括直接分配和继承）
     * @param userId 用户ID
     * @return 角色列表，每个角色包含来源信息
     */
    List<UserEffectiveRole> getEffectiveRolesForUser(String userId);
    
    /**
     * 获取用户的所有有效角色代码（去重后）
     * @param userId 用户ID
     * @return 角色代码列表
     */
    List<String> getEffectiveRoleCodesForUser(String userId);
    
    /**
     * 获取用户的所有权限（基于有效角色）
     * @param userId 用户ID
     * @return 权限列表
     */
    List<String> getPermissionsForUser(String userId);
    
    /**
     * 根据角色代码列表获取权限（从数据库查询）
     * @param roleCodes 角色代码列表
     * @return 权限代码列表
     */
    List<String> getPermissionsForRoleCodes(List<String> roleCodes);
    
    /**
     * 检查用户是否拥有指定角色
     * @param userId 用户ID
     * @param roleCode 角色代码
     * @return 是否拥有该角色
     */
    boolean hasRole(String userId, String roleCode);
    
    /**
     * 检查用户是否拥有指定权限
     * @param userId 用户ID
     * @param permission 权限代码
     * @return 是否拥有该权限
     */
    boolean hasPermission(String userId, String permission);
}
