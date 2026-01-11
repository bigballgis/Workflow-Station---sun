package com.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户角色关联实体
 */
@Entity
@Table(name = "sys_user_roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserRole {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
    
    @Column(name = "assigned_by", length = 64)
    private String assignedBy;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_to")
    private LocalDateTime validTo;
    
    /**
     * 获取用户ID（用于JSON序列化）
     */
    @JsonProperty("userId")
    public String getUserId() {
        return user != null && user.getId() != null ? user.getId().toString() : null;
    }
    
    /**
     * 获取员工编号（用于JSON序列化）
     */
    @JsonProperty("employeeId")
    public String getEmployeeId() {
        return user != null ? user.getEmployeeId() : null;
    }
    
    /**
     * 获取用户名（用于JSON序列化）
     */
    @JsonProperty("username")
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }
    
    /**
     * 获取用户显示名（用于JSON序列化）
     */
    @JsonProperty("displayName")
    public String getDisplayName() {
        return user != null ? user.getFullName() : null;
    }
    
    /**
     * 获取角色ID（用于JSON序列化）
     */
    @JsonProperty("roleId")
    public String getRoleId() {
        return role != null ? role.getId() : null;
    }
    
    /**
     * 获取角色编码（用于JSON序列化）
     */
    @JsonProperty("roleCode")
    public String getRoleCode() {
        return role != null ? role.getCode() : null;
    }
    
    /**
     * 检查角色分配是否有效
     */
    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        boolean afterValidFrom = validFrom == null || !now.isBefore(validFrom);
        boolean beforeValidTo = validTo == null || !now.isAfter(validTo);
        return afterValidFrom && beforeValidTo;
    }
}
