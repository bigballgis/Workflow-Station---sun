package com.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流程实例实体
 */
@Entity
@Table(name = "up_process_instance")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessInstance {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "process_definition_id", length = 64)
    private String processDefinitionId;

    @Column(name = "process_definition_key", nullable = false, length = 255)
    private String processDefinitionKey;

    @Column(name = "process_definition_name", length = 255)
    private String processDefinitionName;

    @Column(name = "business_key", length = 255)
    private String businessKey;

    @Column(name = "start_user_id", nullable = false, length = 64)
    private String startUserId;

    @Column(name = "start_user_name", length = 100)
    private String startUserName;

    @Column(name = "current_node", length = 255)
    private String currentNode;

    @Column(name = "current_assignee", length = 64)
    private String currentAssignee;

    /**
     * 候选用户列表（用于或签场景，多个用户用逗号分隔）
     */
    @Column(name = "candidate_users", length = 500)
    private String candidateUsers;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private Map<String, Object> variables;

    @Column(length = 32)
    private String priority;

    @CreationTimestamp
    @Column(name = "start_time", updatable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
