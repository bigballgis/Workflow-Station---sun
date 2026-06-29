package com.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Inbound email monitor rule synced from developer-workstation on Function Unit import/deploy.
 * Runtime source for the workflow-engine email monitor scheduler.
 */
@Entity
@Table(name = "sys_email_monitor_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class EmailMonitorRule {

    @Id
    @Column(length = 64)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

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

    @Column(name = "action_type", nullable = false, length = 30)
    @Builder.Default
    private String actionType = "START_PROCESS";

    @Column(name = "target_form_id", length = 64)
    private String targetFormId;

    @Column(name = "target_binding_id", length = 64)
    private String targetBindingId;

    @Column(name = "system_initiator_user_id", length = 64)
    private String systemInitiatorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_rules", columnDefinition = "jsonb")
    private Map<String, Object> extractionRules;

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

    @Column(name = "synced_at")
    private Instant syncedAt;
}
