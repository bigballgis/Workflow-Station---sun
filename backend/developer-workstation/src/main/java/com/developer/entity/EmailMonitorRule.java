package com.developer.entity;

import com.developer.enums.EmailMonitorActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

/**
 * Per–Function Unit inbound email monitor rule: which mailbox to watch, how to filter, and the
 * no-code {@code extractionRules} that map email content to main/sub-table fields.
 */
@Entity
@Table(name = "dw_email_monitor_rules")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class EmailMonitorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_uid", nullable = false, unique = true, length = 64)
    private String ruleUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** UID of the inbound OAuth {@link EmailConnection} this rule watches. */
    @Column(name = "connection_uid", nullable = false, length = 64)
    private String connectionUid;

    @Column(name = "process_definition_key", length = 255)
    private String processDefinitionKey;

    @Column(name = "start_event_id", length = 255)
    private String startEventId;

    @Column(name = "folder_label", length = 255)
    @Builder.Default
    private String folderLabel = "INBOX";

    @Column(name = "filter_from", length = 255)
    private String filterFrom;

    @Column(name = "filter_subject", length = 500)
    private String filterSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    @Builder.Default
    private EmailMonitorActionType actionType = EmailMonitorActionType.START_PROCESS;

    @Column(name = "target_form_id")
    private Long targetFormId;

    @Column(name = "target_binding_id", length = 64)
    private String targetBindingId;

    @Column(name = "system_initiator_user_id", length = 64)
    private String systemInitiatorUserId;

    /** Visual-pick / AI-assist extraction rules consumed by the workflow-engine interpreter. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_rules", columnDefinition = "jsonb")
    private Map<String, Object> extractionRules;

    /** Correlation config used by APPEND_SUB_TABLE to locate the running process (Phase 2). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correlation", columnDefinition = "jsonb")
    private Map<String, Object> correlation;

    @Column(name = "poll_interval_seconds", nullable = false)
    @Builder.Default
    private Integer pollIntervalSeconds = 60;

    @Column(name = "review_on_missing")
    @Builder.Default
    private Boolean reviewOnMissing = true;

    @Column(name = "last_sync_cursor", columnDefinition = "TEXT")
    private String lastSyncCursor;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
