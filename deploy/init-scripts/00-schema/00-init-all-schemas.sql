-- =====================================================
-- All Schemas (Docker mount paths)
-- =====================================================
-- NOTE: This file is for MANUAL psql usage only.
-- It is NOT called by 00-init-all.sh or init-database.ps1.
-- The automated scripts execute each SQL file individually
-- using glob patterns.
--
-- To use manually:
--   psql -U postgres -d your_db -f 00-init-all-schemas.sql
-- =====================================================

SET client_min_messages = WARNING;
SET timezone = 'UTC';

BEGIN;

\echo 'Creating Platform Security Schema (sys_*)...'
\i /docker-entrypoint-initdb.d/00-schema/01-platform-security-schema.sql

\echo 'Creating Workflow Engine Schema (wf_*)...'
\i /docker-entrypoint-initdb.d/00-schema/02-workflow-engine-schema.sql

\echo 'Creating User Portal Schema (up_*)...'
\i /docker-entrypoint-initdb.d/00-schema/03-user-portal-schema.sql

\echo 'Creating Developer Workstation Schema (dw_*)...'
\i /docker-entrypoint-initdb.d/00-schema/04-developer-workstation-schema.sql

\echo 'Creating Admin Center Schema (admin_*)...'
\i /docker-entrypoint-initdb.d/00-schema/05-admin-center-schema.sql

COMMIT;

\echo 'Applying incremental migrations...'
\i /docker-entrypoint-initdb.d/00-schema/06-add-deployment-rollback-columns.sql
\i /docker-entrypoint-initdb.d/00-schema/07-add-action-definitions-table.sql
\i /docker-entrypoint-initdb.d/00-schema/08-add-function-unit-versioning.sql
\i /docker-entrypoint-initdb.d/00-schema/10-add-approval-order-column.sql
\i /docker-entrypoint-initdb.d/00-schema/11-add-unique-enabled-constraint.sql
\i /docker-entrypoint-initdb.d/00-schema/12-add-enabled-field-to-dw-function-units.sql
\i /docker-entrypoint-initdb.d/00-schema/13-add-notification-table.sql
\i /docker-entrypoint-initdb.d/00-schema/15-bi-management-schema.sql
\i /docker-entrypoint-initdb.d/00-schema/16-add-decision-and-relations-tables.sql
\i /docker-entrypoint-initdb.d/00-schema/17-add-lock-version-to-user-portal-tables.sql
\i /docker-entrypoint-initdb.d/00-schema/18-add-lock-version-to-form-definitions.sql
\i /docker-entrypoint-initdb.d/00-schema/19-add-up-change-history.sql
\i /docker-entrypoint-initdb.d/00-schema/20-add-members-table.sql
\i /docker-entrypoint-initdb.d/00-schema/21-add-rt-relation-tables.sql
\i /docker-entrypoint-initdb.d/00-schema/22-add-lock-version-to-sys-roles.sql
\i /docker-entrypoint-initdb.d/00-schema/23-widen-up-process-instance-business-key.sql
\i /docker-entrypoint-initdb.d/00-schema/24-add-multi-instance-execution-table.sql
\i /docker-entrypoint-initdb.d/00-schema/25-add-row-version-to-sub-tables.sql
\i /docker-entrypoint-initdb.d/00-schema/26-add-dw-deployment-jobs.sql
\i /docker-entrypoint-initdb.d/00-schema/27-add-up-process-instance-catalog-pin.sql
\i /docker-entrypoint-initdb.d/00-schema/28-dw-function-unit-dev-groups.sql
\i /docker-entrypoint-initdb.d/00-schema/30-widen-flowable-identitylink-columns.sql
\i /docker-entrypoint-initdb.d/00-schema/31-widen-flowable-act-hi-comment-columns.sql

\echo 'All schemas created successfully.'
