package com.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Relation Table 版本快照实体
 * 每次部署生成新版本号并记录完整的表结构快照
 */
@Entity
@Table(name = "rt_table_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RelationTableVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private RelationTableDefinition tableDefinition;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "TEXT")
    private String snapshotData;

    @Column(name = "deployed_by", nullable = false, length = 64)
    private String deployedBy;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "change_log", columnDefinition = "TEXT")
    private String changeLog;
}
