package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Process Instance entity for developer workstation.
 * Represents a running or completed process instance bound to a specific function unit version.
 * 
 * Requirements: 5.1, 5.2, 5.4, 5.5
 */
@Entity
@Table(name = "up_process_instance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
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
     * Candidate users list (for OR-join scenarios, multiple users separated by comma)
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

    /**
     * Reference to the specific version of the function unit this process instance is bound to.
     * This binding is immutable - once set at creation time, it never changes.
     * 
     * Requirements: 5.1 - WHEN a Process_Instance is created, THE System SHALL bind it to 
     *                     the current Active_Version
     *               5.2 - WHEN a new version is deployed, THE System SHALL ensure existing 
     *                     Process_Instance records continue using their bound version
     *               5.4 - THE System SHALL maintain the version binding for the entire 
     *                     lifecycle of each Process_Instance
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_version_id", nullable = false)
    private FunctionUnit functionUnitVersion;
}
