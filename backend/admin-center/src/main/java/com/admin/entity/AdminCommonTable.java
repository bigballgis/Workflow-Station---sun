package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Admin Center view of dw_common_table_definitions
 */
@Entity
@Table(name = "dw_common_table_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AdminCommonTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "version", length = 20)
    private String version;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
