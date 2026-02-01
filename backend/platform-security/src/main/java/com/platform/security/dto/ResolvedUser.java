package com.platform.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析后的用户信息
 * 
 * Note: Department fields have been removed as part of the migration
 * from Department to BusinessUnit architecture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedUser {
    private String userId;
    private String username;
    private String displayName;
    private String employeeId;
    private String email;
}
