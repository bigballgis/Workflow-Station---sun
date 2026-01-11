package com.platform.security.resolver;

import com.platform.security.dto.ResolvedUser;
import com.platform.security.enums.AssignmentTargetType;

import java.util.List;

/**
 * 目标解析器接口
 * 用于将不同类型的分配目标解析为用户列表
 */
public interface TargetResolver {
    
    /**
     * 获取此解析器支持的目标类型
     */
    AssignmentTargetType getTargetType();
    
    /**
     * 检查目标是否存在
     * @param targetId 目标ID
     * @return 目标是否存在
     */
    boolean targetExists(String targetId);
    
    /**
     * 解析目标为用户列表
     * @param targetId 目标ID
     * @return 用户列表
     */
    List<ResolvedUser> resolveUsers(String targetId);
    
    /**
     * 获取目标的显示名称
     * @param targetId 目标ID
     * @return 显示名称
     */
    String getTargetDisplayName(String targetId);
    
    /**
     * 获取目标影响的用户数量
     * @param targetId 目标ID
     * @return 用户数量
     */
    default long getUserCount(String targetId) {
        return resolveUsers(targetId).size();
    }
}
