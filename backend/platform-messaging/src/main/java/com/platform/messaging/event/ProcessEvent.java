package com.platform.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event for process state changes.
 * Validates: Requirements 6.2
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessEvent extends BaseEvent {
    
    public static final String TOPIC = "platform.process.events";
    
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String businessKey;
    private ProcessEventType processEventType;
    private String initiatorId;
    private Map<String, Object> variables;
    private String previousState;
    private String currentState;
    
    @Override
    public String getTopic() {
        return TOPIC;
    }
    
    public enum ProcessEventType {
        STARTED,
        COMPLETED,
        TERMINATED,
        SUSPENDED,
        ACTIVATED,
        STATE_CHANGED,
        VARIABLE_UPDATED
    }
}
