\set ON_ERROR_STOP on

-- =====================================================
-- Admin Center: LDAP 同步审计表 ac_ldap_sync_audit
-- =====================================================
-- 对应 Flyway admin-center V212（建表）+ V213（扩展 Hermes AD Group 同步审计字段）
-- 幂等：IF NOT EXISTS，可重复执行。
-- =====================================================

CREATE TABLE IF NOT EXISTS ac_ldap_sync_audit (
    id            VARCHAR(64)   NOT NULL,
    sync_type     VARCHAR(20)   NOT NULL,           -- FULL / INCREMENTAL / HERMES_AD_GROUP / HERMES_AD_INCR
    status        VARCHAR(20)   NOT NULL,           -- RUNNING / SUCCESS / FAILED
    total_fetched INTEGER,
    upserted      INTEGER,
    failed        INTEGER,
    message       VARCHAR(1000),                    -- 失败原因/摘要（脱敏）
    snapshot_at   TIMESTAMP,                        -- 本次同步开始时刻，作为下次增量水位
    started_at    TIMESTAMP     NOT NULL,
    finished_at   TIMESTAMP,
    -- V213 Hermes AD Group 同步扩展列（幂等：手动 DO 块检查）
    high_water_mark   VARCHAR(1000),
    groups            VARCHAR(2000),
    success_count     INTEGER,
    skipped_missing_key INTEGER,
    insert_count      INTEGER,
    update_count      INTEGER,
    duration_ms       BIGINT,
    CONSTRAINT pk_ac_ldap_sync_audit PRIMARY KEY (id)
);

-- 按 (sync_type, status, started_at) 查询最近一次成功的增量/全量
CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_type_status_started
    ON ac_ldap_sync_audit (sync_type, status, started_at DESC);

-- 列表分页
CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_started
    ON ac_ldap_sync_audit (started_at DESC);
