package com.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Admin Center view of dw_common_table_access
 */
@Entity
@Table(name = "dw_common_table_access")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AdminCommonTableAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "common_table_id")
    private Long commonTableId;

    @Column(name = "access_type", length = 20)
    private String accessType;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
