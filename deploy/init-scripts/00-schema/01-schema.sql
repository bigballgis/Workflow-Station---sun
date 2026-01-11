-- =====================================================
-- Database Schema Initialization
-- Version: 3.0 - Component-managed tables
-- =====================================================
-- 
-- IMPORTANT: All database tables are now managed by each component's
-- own Flyway migrations. This file is kept for reference only.
--
-- Table ownership:
-- - sys_* tables: platform-security (shared across all services)
-- - admin_* tables: admin-center (admin-specific features)
-- - dw_* tables: developer-workstation
-- - up_* tables: user-portal
-- - wf_* tables: workflow-engine-core
--
-- Component Flyway locations:
-- - backend/platform-security/src/main/resources/db/migration/
-- - backend/admin-center/src/main/resources/db/migration/
-- - backend/developer-workstation/src/main/resources/db/migration/
-- - backend/user-portal/src/main/resources/db/migration/
-- - backend/workflow-engine-core/src/main/resources/db/migration/
--
-- =====================================================

-- Enable extensions (safe to run multiple times)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- This file intentionally left minimal.
-- Tables are created by component Flyway migrations.
SELECT 'Schema initialization delegated to component Flyway migrations' AS status;
