package com.developer.entity;

import com.developer.enums.AiMode;
import com.developer.enums.AiPhase;
import com.developer.enums.AiSessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 会话实体
 */
@Entity
@Table(name = "dw_ai_sessions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AiSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID sessionId;

    @Column(name = "function_unit_id", nullable = false)
    private Long functionUnitId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 20)
    private AiPhase currentPhase;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private AiMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AiSessionStatus status = AiSessionStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
