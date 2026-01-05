package com.admin.dto.response;

import com.admin.enums.TaskAssignmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 虚拟组任务信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupTaskInfo {
    
    private String taskId;
    private String taskName;
    private String processInstanceId;
    private String processName;
    private TaskAssignmentType assignmentType;
    private String assigneeId;
    private String assigneeName;
    private String groupId;
    private String groupName;
    private String status;
    private Instant createdAt;
    private Instant dueDate;
    private boolean claimed;
    private String claimedBy;
    private Instant claimedAt;
}
