package com.admin.dto.response;

import com.platform.security.enums.AssignmentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色分配响应（分配记录列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleAssignmentResponse {
    
    /**
     * 分配ID
     */
    private String id;
    
    /**
     * 角色ID
     */
    private String roleId;
    
    /**
     * 角色名称
     */
    private String roleName;
    
    /**
     * 分配目标类型
     */
    private AssignmentTargetType targetType;
    
    /**
     * 分配目标ID
     */
    private String targetId;
    
    /**
     * 分配目标名称（用户名/部门名/虚拟组名）
     */
    private String targetName;
    
    /**
     * 该分配影响的用户数
     */
    private long effectiveUserCount;
    
    /**
     * 分配时间
     */
    private LocalDateTime assignedAt;
    
    /**
     * 分配人ID
     */
    private String assignedBy;
    
    /**
     * 分配人名称
     */
    private String assignedByName;
    
    /**
     * 有效期开始时间
     */
    private LocalDateTime validFrom;
    
    /**
     * 有效期结束时间
     */
    private LocalDateTime validTo;
}
