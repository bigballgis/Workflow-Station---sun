package com.developer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 表单阶段绑定实体
 * 管理 Task Form 与 BPMN Stage (userTask) 的绑定关系
 */
@Entity
@Table(name = "dw_form_stage_bindings")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FormStageBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private FormDefinition form;

    @Column(name = "stage_id", nullable = false, length = 255)
    private String stageId;

    @Column(name = "stage_name", length = 255)
    private String stageName;

    @Column(name = "read_only", nullable = false)
    @Builder.Default
    private Boolean readOnly = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
