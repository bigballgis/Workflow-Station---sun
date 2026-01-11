package com.platform.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析后的用户信息
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
    private String departmentId;
    private String departmentName;
    private String email;
}
