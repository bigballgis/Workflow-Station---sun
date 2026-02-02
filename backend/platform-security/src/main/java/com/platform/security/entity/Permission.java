package com.platform.security.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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
    
    @Column(length = 500)
    private String description;
    
    @Column(length = 50)
    private String module;
    
    @Column(name = "resource_type", length = 50)
    private String resourceType;
    
    @Column(length = 50)
    private String action;
    
    @Column(nullable = false)
    private boolean enabled;
}
