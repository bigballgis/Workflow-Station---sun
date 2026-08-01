package com.developer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * AI 提示词覆盖值（每个 phase 至多一行）。
 *
 * <p>没有行 = 用镜像里的内置默认值 {@code resources/ai-prompts/<phase>.txt}；有行 = 用行里的 content。
 * 存在的意义是让提示词可以在运行时改，不必重新构建镜像重新部署。</p>
 */
@Entity
@Table(name = "dw_ai_prompt_templates")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AiPromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** REQUIREMENTS / DESIGN / GENERATION，与 {@code AiPromptTemplateServiceImpl.PHASES} 一致 */
    @Column(name = "phase", nullable = false, length = 20, updatable = false)
    private String phase;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
