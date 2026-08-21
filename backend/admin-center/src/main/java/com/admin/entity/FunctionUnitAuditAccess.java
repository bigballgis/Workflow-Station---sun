package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 功能单元审计授权 —— 允许某个角色查看该功能单元下的**全部** request，
 * 无论该用户是否为发起人或流程参与人。
 *
 * <p>刻意与 {@link FunctionUnitAccess} 分表而不是在其 {@code access_type} 上加值：
 * 该表的所有读取方只按 {@code target_type} 过滤、完全忽略 {@code access_type}，
 * 一条 AUDIT 行会被当作「可发起该功能单元」而静默提权。独立键空间不会有这个问题。
 */
@Entity
@Table(name = "sys_function_unit_audit_access")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FunctionUnitAuditAccess {

    /** 目标类型：当前仅支持按角色授权。 */
    public static final String TARGET_TYPE_ROLE = "ROLE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "function_unit_id", nullable = false)
    private FunctionUnit functionUnit;

    /** 目标类型：ROLE */
    @Column(name = "target_type", nullable = false, length = 20)
    @Builder.Default
    private String targetType = TARGET_TYPE_ROLE;

    /** 目标ID（角色ID） */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;
}
