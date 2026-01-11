package com.platform.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户有效角色
 * 包含角色信息和来源列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEffectiveRole {
    /**
     * 角色ID
     */
    private String roleId;
    
    /**
     * 角色代码
     */
    private String roleCode;
    
    /**
     * 角色名称
     */
    private String roleName;
    
    /**
     * 角色类型
     */
    private String roleType;
    
    /**
     * 角色来源列表（一个角色可能有多个来源）
     */
    @Builder.Default
    private List<RoleSource> sources = new ArrayList<>();
    
    /**
     * 添加来源
     */
    public void addSource(RoleSource source) {
        if (sources == null) {
            sources = new ArrayList<>();
        }
        sources.add(source);
    }
}
