-- =====================================================
-- Superset metadata schema bootstrap
-- Ensure SQLAlchemy can create ab_* objects under configured search_path
-- =====================================================

CREATE SCHEMA IF NOT EXISTS superset;

GRANT USAGE, CREATE ON SCHEMA superset TO platform_dev;
