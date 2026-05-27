package com.workflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * User security info DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityInfo {
    
    /**
     * Username
     */
    private String username;
    
    /**
     * Display name
     */
    private String displayName;
    
    /**
     * Email
     */
    private String email;
    
    /**
     * User role
     */
    private Set<String> roles;
    
    /**
     * User permissions
     */
    private Set<String> permissions;
    
    /**
     * Last login time
     */
    private LocalDateTime lastLoginTime;
    
    /**
     * Last login IP
     */
    private String lastLoginIp;
    
    /**
     * Whether account is enabled
     */
    private Boolean enabled;
    
    /**
     * Whether account is locked
     */
    private Boolean locked;
    
    /**
     * Password expiration time
     */
    private LocalDateTime passwordExpiresAt;
}
