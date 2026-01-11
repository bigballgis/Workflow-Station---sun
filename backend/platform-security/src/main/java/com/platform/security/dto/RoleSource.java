package com.platform.security.dto;

import com.platform.security.enums.AssignmentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色来源信息
 * 描述用户如何获得某个角色
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSource {
    /**
     * 来源类型
     */
    private AssignmentTargetType sourceType;
    
    /**
     * 来源ID（用户ID/部门ID/虚拟组ID）
     */
    private String sourceId;
    
    /**
     * 来源名称
     */
    private String sourceName;
    
    /**
     * 对应的分配记录ID
     */
    private String assignmentId;
}
