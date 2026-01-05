package com.platform.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * Role model representing a user role with associated permissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String code;
    private String name;
    private String description;
    private Set<String> permissionCodes;
    private boolean enabled;
}
