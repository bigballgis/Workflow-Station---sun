package com.workflow.email.inbound.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Engine-side view of {@code sys_email_monitor_rules}. The scheduler reads enabled rules and
 * writes back {@code lastSyncCursor} / {@code lastSyncedAt} after each poll.
 *
 * <p>{@code functionUnitId} is mapped as a plain FK string (no relation) to avoid coupling the
 * engine to admin-center's {@code sys_function_units} entity.
 */
@Entity
@Table(name = "sys_email_monitor_rules")
@Getter
@Setter
public class SysEmailMonitorRule {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "function_unit_id", length = 64)
    private String functionUnitId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "connection_uid", length = 64)
    private String connectionUid;

    @Column(name = "process_definition_key", length = 255)
    private String processDefinitionKey;

    @Column(name = "start_event_id", length = 255)
    private String startEventId;

    @Column(name = "folder_label", length = 255)
    private String folderLabel;

    @Column(name = "filter_from", length = 255)
    private String filterFrom;

    @Column(name = "filter_subject", length = 500)
    private String filterSubject;

    @Column(name = "action_type", length = 30)
    private String actionType;

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

    @Column(name = "poll_interval_seconds")
    private Integer pollIntervalSeconds;

    @Column(name = "review_on_missing")
    private Boolean reviewOnMissing;

    @Column(name = "last_sync_cursor", columnDefinition = "TEXT")
    private String lastSyncCursor;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
