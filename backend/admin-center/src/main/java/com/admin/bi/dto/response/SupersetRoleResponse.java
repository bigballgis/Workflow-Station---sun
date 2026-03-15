package com.admin.bi.dto.response;

import com.admin.bi.enums.SupersetRoleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Superset 角色响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupersetRoleResponse {

    private Integer id;
    private Integer supersetRoleId;
    private String name;
    private SupersetRoleStatus status;
    private LocalDateTime lastSyncedAt;
}
