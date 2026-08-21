package com.developer.entity;

import com.developer.enums.FormScene;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Form stage binding entity.
 * Manages the binding relationship between Task Forms and BPMN Stage (userTask).
 *
 * <p>A node can hold one binding per {@link FormScene}, so To Do and My Requests
 * can render entirely different designs of the same step.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "scene", nullable = false, length = 16)
    @Builder.Default
    private FormScene scene = FormScene.TASK;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
