package com.workflow.email.inbound.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency + audit ledger row for an inbound email. A unique {@code (rule_uid, message_id)}
 * constraint guarantees an email triggers a process at most once, even across engine replicas:
 * the instance that wins the insert proceeds; concurrent losers hit the constraint and skip.
 */
@Entity
@Table(name = "we_email_processed_messages")
@Getter
@Setter
public class ProcessedEmailMessage {

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_REVIEW = "REVIEW";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_uid", nullable = false, length = 64)
    private String ruleUid;

    @Column(name = "message_id", nullable = false, length = 512)
    private String messageId;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processed_at")
    private Instant processedAt;
}
