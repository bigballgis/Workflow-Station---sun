package com.admin.entity;

import com.admin.enums.TaskActionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 虚拟组任务处理历史实体
 * 记录任务的认领、委托、处理等历史
 * 虚拟组是跨服务共享的
 */
@Entity
@Table(name = "sys_virtual_group_task_history")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VirtualGroupTaskHistory {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;
    
    @Column(name = "group_id", length = 64)
    private String groupId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private TaskActionType actionType;
    
    @Column(name = "from_user_id", length = 64)
    private String fromUserId;
    
    @Column(name = "to_user_id", length = 64)
    private String toUserId;
    
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
