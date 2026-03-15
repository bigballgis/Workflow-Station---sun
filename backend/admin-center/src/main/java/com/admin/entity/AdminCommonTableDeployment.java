package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Admin Center view of dw_common_table_deployments
 */
@Entity
@Table(name = "dw_common_table_deployments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AdminCommonTableDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "common_table_id")
    private Long commonTableId;

    @Column(name = "version", length = 20)
    private String version;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private Instant createdAt;
}
