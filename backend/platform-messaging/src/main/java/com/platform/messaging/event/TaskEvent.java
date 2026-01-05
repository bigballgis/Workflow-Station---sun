package com.platform.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event for task state changes.
 * Validates: Requirements 6.3
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskEvent extends BaseEvent {
    
    public static final String TOPIC = "platform.task.events";
    
    private String taskId;
    private String taskName;
    private String processInstanceId;
    private String processDefinitionId;
    private TaskEventType taskEventType;
    private String assignee;
    private String previousAssignee;
    private List<String> candidateGroups;
    private List<String> candidateUsers;
    private LocalDateTime dueDate;
    private Integer priority;
    
    @Override
    public String getTopic() {
        return TOPIC;
    }
    
    public enum TaskEventType {
        CREATED,
        ASSIGNED,
        REASSIGNED,
        COMPLETED,
        DELEGATED,
        CLAIMED,
        UNCLAIMED,
        DUE_DATE_CHANGED,
        PRIORITY_CHANGED
    }
}
