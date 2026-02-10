-- =====================================================
-- All Schemas (Docker mount paths)
-- =====================================================
-- Used by 00-init-all.sh via Docker entrypoint.
-- For standalone psql, use 00-init-all-schemas-standalone.sql
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

\echo 'All schemas created successfully.'
