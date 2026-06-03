package com.admin.entity.module;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "ac_frontend_module_version")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FrontendModuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_registry_id", nullable = false)
    private Long moduleRegistryId;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "remote_entry_url", nullable = false, length = 512)
    private String remoteEntryUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "release_note", columnDefinition = "TEXT")
    private String releaseNote;

    @CreatedBy
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
