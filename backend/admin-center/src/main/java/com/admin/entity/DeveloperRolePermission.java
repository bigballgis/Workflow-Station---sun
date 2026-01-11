package com.admin.entity;

import com.admin.enums.DeveloperPermission;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 开发角色权限映射实体
 * 存储开发角色与权限的关联关系
 */
@Entity
@Table(name = "sys_developer_role_permissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission"}))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DeveloperRolePermission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;
    
    @Column(name = "role_id", nullable = false, length = 64)
    private String roleId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private DeveloperPermission permission;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
