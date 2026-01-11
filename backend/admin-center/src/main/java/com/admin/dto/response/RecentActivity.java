package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 最近活动响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivity {
    
    /**
     * 活动ID
     */
    private String id;
    
    /**
     * 操作类型
     */
    private String action;
    
    /**
     * 资源类型
     */
    private String resourceType;
    
    /**
     * 资源ID
     */
    private String resourceId;
    
    /**
     * 资源名称
     */
    private String resourceName;
    
    /**
     * 操作用户名
     */
    private String username;
    
    /**
     * 操作用户ID
     */
    private String userId;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 创建时间
     */
    private String createdAt;
}
