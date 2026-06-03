package com.admin.entity.module;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "ac_frontend_module_health_log")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FrontendModuleHealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_registry_id", nullable = false)
    private Long moduleRegistryId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @CreatedDate
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
