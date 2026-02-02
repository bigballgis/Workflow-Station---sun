package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Permission entity representing a single permission.
 * Maps to sys_permissions table.
 */
@Entity
@Table(name = "sys_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String code;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(length = 50)
    private String type;
    
    @Column(length = 100)
    private String resource;
    
    @Column(length = 50)
    private String action;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "parent_id", length = 64)
    private String parentId;
    
    @Column(name = "sort_order")
    private Integer sortOrder;
}
