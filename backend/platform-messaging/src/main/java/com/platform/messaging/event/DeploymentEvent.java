package com.platform.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event for deployment state changes.
 * Validates: Requirements 6.4
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DeploymentEvent extends BaseEvent {
    
    public static final String TOPIC = "platform.deployment.events";
    
    private String deploymentId;
    private String functionUnitId;
    private String functionUnitName;
    private String version;
    private String environment;
    private DeploymentEventType deploymentEventType;
    private String deployedBy;
    private String previousVersion;
    private String errorMessage;
    
    @Override
    public String getTopic() {
        return TOPIC;
    }
    
    public enum DeploymentEventType {
        STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLBACK_STARTED,
        ROLLBACK_COMPLETED,
        ROLLBACK_FAILED
    }
}
