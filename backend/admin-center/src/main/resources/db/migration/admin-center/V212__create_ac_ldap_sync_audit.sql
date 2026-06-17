-- =====================================================================
-- LDAP 同步审计表 ac_ldap_sync_audit
-- 用途：①运维可视化 LDAP 用户同步历史与失败原因；
--      ②增量同步以最近一次 SUCCESS 记录的 snapshot_at 作为 AD whenChanged 水位起点。
-- 幂等：IF NOT EXISTS，可重复执行（双轨 schema 见 docs/schema-and-migration.md）。
-- =====================================================================
CREATE TABLE IF NOT EXISTS ac_ldap_sync_audit (
    id            VARCHAR(64)   NOT NULL,
    sync_type     VARCHAR(20)   NOT NULL,           -- FULL / INCREMENTAL
    status        VARCHAR(20)   NOT NULL,           -- RUNNING / SUCCESS / FAILED
    total_fetched INTEGER,
    upserted      INTEGER,
    failed        INTEGER,
    message       VARCHAR(1000),                    -- 失败原因/摘要（脱敏，不含密码/DN 明文）
    snapshot_at   TIMESTAMP,                        -- 本次同步开始时刻，作为下次增量水位
    started_at    TIMESTAMP     NOT NULL,
    finished_at   TIMESTAMP,
    CONSTRAINT pk_ac_ldap_sync_audit PRIMARY KEY (id)
);

-- 按 (sync_type, status, started_at) 查询「最近一次成功的增量/全量」时使用
CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_type_status_started
    ON ac_ldap_sync_audit (sync_type, status, started_at DESC);

-- 列表分页（started_at 倒序）
CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_started
    ON ac_ldap_sync_audit (started_at DESC);
