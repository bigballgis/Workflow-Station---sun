package com.admin.dto.response;

import com.platform.security.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Role list row DTO. Grouping writes go here, never onto the JPA {@link Role} entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleListItem {

    private String id;
    private String name;
    private String code;
    private String type;
    private String displayName;
    private String status;
    private Boolean isSystem;
    private Instant createdAt;
    private Instant updatedAt;

    public static RoleListItem fromEntity(Role role) {
        return RoleListItem.builder()
                .id(role.getId())
                .name(role.getName())
                .code(role.getCode())
                .type(role.getType())
                .displayName(role.getDisplayName())
                .status(role.getStatus())
                .isSystem(role.getIsSystem())
                .createdAt(toInstant(role.getCreatedAt()))
                .updatedAt(toInstant(role.getUpdatedAt()))
                .build();
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
