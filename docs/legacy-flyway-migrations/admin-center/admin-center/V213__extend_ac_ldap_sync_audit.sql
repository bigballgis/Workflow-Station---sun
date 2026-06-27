-- =====================================================================
-- 扩展 LDAP 同步审计表 ac_ldap_sync_audit，支持 Hermes AD Group 同步审计
-- 新增字段：high_water_mark, groups, success_count, skipped_missing_key,
--           insert_count, update_count, duration_ms
-- 幂等：COLUMN IF NOT EXISTS（每列独立），可重复执行。
-- =====================================================================
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS high_water_mark   VARCHAR(1000);
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS groups            VARCHAR(2000);
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS success_count     INTEGER;
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS skipped_missing_key INTEGER;
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS insert_count      INTEGER;
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS update_count      INTEGER;
ALTER TABLE ac_ldap_sync_audit ADD COLUMN IF NOT EXISTS duration_ms       BIGINT;
