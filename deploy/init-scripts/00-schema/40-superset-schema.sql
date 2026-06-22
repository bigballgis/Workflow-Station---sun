-- =====================================================
-- Superset BI — schema only (idempotent)
-- Tables/roles/data are managed by Superset's own CLI:
--   superset db upgrade   (incremental, never drops data)
--   superset init          (idempotent, skips existing)
-- =====================================================
CREATE SCHEMA IF NOT EXISTS superset;
