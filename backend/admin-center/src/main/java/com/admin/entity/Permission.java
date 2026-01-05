package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 权限实体
 */
@Entity
@Table(name = "admin_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Permission {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;
    
    @Column(name = "type", nullable = false, length = 20)
    private String type;
    
    @Column(name = "resource", nullable = false, length = 200)
    private String resource;
    
    @Column(name = "action", nullable = false, length = 50)
    private String action;
    
    @Column(name = "parent_id", length = 64)
    private String parentId;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
    
    /**
     * 获取完整的权限标识
     */
    public String getFullCode() {
        return resource + ":" + action;
    }
}
