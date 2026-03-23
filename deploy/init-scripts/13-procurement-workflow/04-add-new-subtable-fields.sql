-- =============================================================================
-- 13-procurement-workflow: (已废弃 — 仅用于旧数据库迁移)
-- 所有字段已合并到 01-create-tables.sql 和 03-form-table-bindings.sql
-- 新环境初始化无需执行此脚本
-- =============================================================================

-- 此脚本保留为空，避免执行顺序引用报错
DO $$ BEGIN RAISE NOTICE '04-add-new-subtable-fields.sql: SKIPPED (deprecated, all fields in 01/03)'; END $$;
