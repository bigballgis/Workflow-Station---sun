package com.portal.entity;

import com.portal.util.SubTableRowIdentityEnricher;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
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
@DynamicUpdate
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

    /**
     * 引擎侧实例标识（与 {@link #id} 一致时常见于门户发起流程；列可为空以兼容历史数据）
     */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "initiator_id", length = 64)
    private String initiatorId;

    @Column(length = 200)
    private String title;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    /**
     * 库表默认值维护；不显式写入以免覆盖 PostgreSQL DEFAULT。
     */
    @Column(name = "started_at", insertable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 绑定的功能单元版本（dw_function_units.id），与 developer 侧共用表时可空 */
    @Column(name = "function_unit_version_id")
    private Long functionUnitVersionId;

    /** 发起时钉死的 admin 功能单元目录行 ID（sys_function_units.id） */
    @Column(name = "function_unit_catalog_id", length = 64)
    private String functionUnitCatalogId;

    @Column(name = "function_unit_code", length = 50)
    private String functionUnitCode;

    @Column(name = "function_unit_version_label", length = 32)
    private String functionUnitVersionLabel;

    @CreationTimestamp
    @Column(name = "start_time", updatable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Version field for optimistic locking.
     * JPA will automatically increment this on each update and check for concurrent modifications.
     */
    @jakarta.persistence.Version
    @Column(name = "lock_version")
    private Long lockVersion;

    /**
     * Guarantee that every sub-table row reaching storage can be addressed later.
     *
     * <p>Rows enter {@code variables.__subTables__} from many directions — process start,
     * task submit, snapshot capture, approval sync, engine hydration, and email-triggered
     * starts that never pass through a portal form. Only rows whose sub-table declares an
     * auto primary key get one from {@code ProcessSubTablePrimaryKeyEnricherComponent}, so
     * enforcing identity at any single API entry point would leave the others without it
     * (dev already holds a row with full business data and no identity key at all). The
     * persistence callback is the one place all of those paths converge on.
     *
     * <p>Existing identities are never overwritten, so a designer-allocated primary key
     * always wins over a generated {@code row_id}.
     */
    @PrePersist
    @PreUpdate
    void ensureSubTableRowIdentity() {
        SubTableRowIdentityEnricher.ensureRowIdentities(variables);
    }
}
