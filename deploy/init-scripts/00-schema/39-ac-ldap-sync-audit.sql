-- =====================================================================
-- LDAP 同步审计表 ac_ldap_sync_audit（Docker 首次初始化基线；与 Flyway V212/V213 等价）
-- Dev 容器 SPRING_FLYWAY_ENABLED=false，故以本 init 脚本为准（见 docs/schema-and-migration.md）。
-- 幂等：IF NOT EXISTS，可重复执行。
-- =====================================================================
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
    CONSTRAINT pk_ac_ldap_sync_audit PRIMARY KEY (id)
);

-- 扩展列：支持 Hermes AD Group 同步审计（幂等：不强制要求存在）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'high_water_mark') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN high_water_mark VARCHAR(1000);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'groups') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN groups VARCHAR(2000);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'success_count') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN success_count INTEGER;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'skipped_missing_key') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN skipped_missing_key INTEGER;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'insert_count') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN insert_count INTEGER;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'update_count') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN update_count INTEGER;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'ac_ldap_sync_audit' AND column_name = 'duration_ms') THEN
        ALTER TABLE ac_ldap_sync_audit ADD COLUMN duration_ms BIGINT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_type_status_started
    ON ac_ldap_sync_audit (sync_type, status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ac_ldap_sync_audit_started
    ON ac_ldap_sync_audit (started_at DESC);
