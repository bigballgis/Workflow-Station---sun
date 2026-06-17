package com.admin.ldap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * LDAP 同步审计记录（表 {@code ac_ldap_sync_audit}）。
 *
 * <p>用途：①运维可视化同步历史与失败原因；②增量同步以最近一次成功记录的 {@code snapshotAt}
 * 作为 AD {@code whenChanged} 水位起点。</p>
 */
@Entity
@Table(name = "ac_ldap_sync_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class LdapSyncAudit {

    @Id
    @Column(length = 64)
    private String id;

    /** FULL / INCREMENTAL。 */
    @Column(name = "sync_type", nullable = false, length = 20)
    private String syncType;

    /** RUNNING / SUCCESS / FAILED。 */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_fetched")
    private Integer totalFetched;

    @Column(name = "upserted")
    private Integer upserted;

    @Column(name = "failed")
    private Integer failed;

    /** 失败原因或摘要（脱敏，不含密码/DN 明文）。 */
    @Column(length = 1000)
    private String message;

    /** 本次同步开始时刻；增量同步以此作为下次 whenChanged 水位。 */
    @Column(name = "snapshot_at")
    private Instant snapshotAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
