-- =====================================================
-- All Schemas (Standalone psql)
-- =====================================================
-- Usage:
--   cd deploy/init-scripts
--   psql -h <host> -p <port> -U <user> -d <db> -f 00-schema/00-init-all-schemas-standalone.sql
-- =====================================================

SET client_min_messages = WARNING;
SET timezone = 'UTC';

BEGIN;

\echo '=== 1/5 Platform Security Schema (sys_*) ==='
\i 00-schema/01-platform-security-schema.sql

\echo '=== 2/5 Workflow Engine Schema (wf_*) ==='
\i 00-schema/02-workflow-engine-schema.sql

\echo '=== 3/5 User Portal Schema (up_*) ==='
\i 00-schema/03-user-portal-schema.sql

\echo '=== 4/5 Developer Workstation Schema (dw_*) ==='
\i 00-schema/04-developer-workstation-schema.sql

\echo '=== 5/5 Admin Center Schema (admin_*) ==='
\i 00-schema/05-admin-center-schema.sql

COMMIT;

\echo '=== Applying incremental migrations ==='
\i 00-schema/06-add-deployment-rollback-columns.sql
\i 00-schema/07-add-action-definitions-table.sql
\i 00-schema/08-add-function-unit-versioning.sql
\i 00-schema/10-add-approval-order-column.sql
\i 00-schema/11-add-unique-enabled-constraint.sql
\i 00-schema/12-add-enabled-field-to-dw-function-units.sql
\i 00-schema/13-add-notification-table.sql
\i 00-schema/15-bi-management-schema.sql
\i 00-schema/16-add-decision-and-relations-tables.sql
\i 00-schema/17-add-lock-version-to-user-portal-tables.sql
\i 00-schema/18-add-lock-version-to-form-definitions.sql
\i 00-schema/18-add-read-only-to-form-stage-bindings.sql
\i 00-schema/19-add-up-change-history.sql
\i 00-schema/20-add-members-table.sql
\i 00-schema/21-add-rt-relation-tables.sql
\i 00-schema/22-add-lock-version-to-sys-roles.sql
\i 00-schema/23-widen-up-process-instance-business-key.sql
\i 00-schema/24-add-multi-instance-execution-table.sql
\i 00-schema/25-add-row-version-to-sub-tables.sql
\i 00-schema/26-add-dw-deployment-jobs.sql
\i 00-schema/27-add-up-process-instance-catalog-pin.sql
\i 00-schema/28-dw-function-unit-dev-groups.sql
\i 00-schema/29-up-permission-request-submitted-by.sql
\i 00-schema/30-widen-flowable-identitylink-columns.sql
\i 00-schema/31-widen-flowable-act-hi-comment-columns.sql
\i 00-schema/32-add-dw-form-table-binding-subview-columns.sql
\i 00-schema/33-dw-sub-table-view-tables.sql
\i 00-schema/34-dw-link-form-components.sql
\i 00-schema/34-extend-function-unit-status-check.sql
\i 00-schema/35-drop-init-function-unit-status.sql
\i 00-schema/36-sys-function-units-description.sql
\i 00-schema/37-sys-action-definitions-description.sql
\i 00-schema/38-dw-main-table-view-tables.sql

\echo '=== All schemas created successfully ==='
