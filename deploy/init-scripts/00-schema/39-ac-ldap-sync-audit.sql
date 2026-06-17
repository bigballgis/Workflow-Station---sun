-- =====================================================================
-- LDAP 同步审计表 ac_ldap_sync_audit（Docker 首次初始化基线；与 Flyway V212 等价）
-- Dev 容器 SPRING_FLYWAY_ENABLED=false，故以本 init 脚本为准（见 docs/schema-and-migration.md）。
-- 幂等：IF NOT EXISTS，可重复执行。
-- =====================================================================
CREATE TABLE IF NOT EXISTS ac_ldap_sync_audit (
    id            VARCHAR(64)   NOT NULL,
    sync_type     VARCHAR(20)   NOT NULL,           -- FULL / INCREMENTAL
    status        VARCHAR(20)   NOT NULL,           -- RUNNING / SUCCESS / FAILED
    total_fetched INTEGER,
    upserted      INTEGER,
    failed        INTEGER,
    message       VARCHAR(1000),                    -- 失败原因/摘要（脱敏）
    snapshot_at   TIMESTAMP,                        -- 本次同步开始时刻，作为下次增量水位
    started_at    TIMESTAMP     NOT NULL,
    finished_at   TIMESTAMP,
    CONSTRAINT pk_ac_ldap_sync_audit PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_type_status_started
    ON ac_ldap_sync_audit (sync_type, status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_started
    ON ac_ldap_sync_audit (started_at DESC);
