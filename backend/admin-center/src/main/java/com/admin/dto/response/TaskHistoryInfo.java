package com.admin.dto.response;

import com.admin.entity.VirtualGroupTaskHistory;
import com.admin.enums.TaskActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 任务历史信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistoryInfo {
    
    private String id;
    private String taskId;
    private String groupId;
    private TaskActionType actionType;
    private String fromUserId;
    private String fromUserName;
    private String toUserId;
    private String toUserName;
    private String reason;
    private String comment;
    private Instant createdAt;
    
    public static TaskHistoryInfo fromEntity(VirtualGroupTaskHistory history) {
        return TaskHistoryInfo.builder()
                .id(history.getId())
                .taskId(history.getTaskId())
                .groupId(history.getGroupId())
                .actionType(history.getActionType())
                .fromUserId(history.getFromUserId())
                .toUserId(history.getToUserId())
                .reason(history.getReason())
                .comment(history.getComment())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
