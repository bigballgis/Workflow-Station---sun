package com.platform.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Permission model representing a single permission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String code;
    private String name;
    private String description;
    private String module;
    private String resourceType;
    private String action;
    private boolean enabled;
}
