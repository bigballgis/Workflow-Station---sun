\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: Add binding_link_mode column
-- =====================================================
-- The column is already in 04-developer-workstation-schema.sql base DDL
-- but older databases initialized before the column was added to the base
-- schema are missing it.  This script backfills the column for those
-- databases so that cascading JPA operations (delete, list) on
-- dw_form_table_bindings do not fail with "column does not exist".
--
-- Keep idempotent via IF NOT EXISTS.
-- =====================================================

ALTER TABLE dw_form_table_bindings
  ADD COLUMN IF NOT EXISTS binding_link_mode VARCHAR(32) NOT NULL DEFAULT 'structuralFk';

COMMENT ON COLUMN dw_form_table_bindings.binding_link_mode IS
  'Link mode: structuralFk (FK via table structure) or customField (user-managed FK field)';
