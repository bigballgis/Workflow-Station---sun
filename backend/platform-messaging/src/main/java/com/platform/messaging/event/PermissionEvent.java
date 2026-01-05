package com.platform.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

/**
 * Event for permission changes.
 * Validates: Requirements 6.4
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PermissionEvent extends BaseEvent {
    
    public static final String TOPIC = "platform.permission.events";
    
    private String userId;
    private String targetUserId;
    private PermissionEventType permissionEventType;
    private Set<String> permissions;
    private Set<String> roles;
    private String resourceType;
    private String resourceId;
    private String delegationId;
    
    @Override
    public String getTopic() {
        return TOPIC;
    }
    
    public enum PermissionEventType {
        PERMISSION_GRANTED,
        PERMISSION_REVOKED,
        ROLE_ASSIGNED,
        ROLE_REMOVED,
        DELEGATION_CREATED,
        DELEGATION_REVOKED,
        DELEGATION_EXPIRED,
        DATA_PERMISSION_CHANGED
    }
}
