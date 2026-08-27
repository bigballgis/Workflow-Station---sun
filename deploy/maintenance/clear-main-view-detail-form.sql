-- Clear detail-form bindings from MAIN-table views.
--
-- A MAIN-table row IS a request (one process instance = one row), so the User Portal opens the
-- request detail page for it instead of a designed DETAIL form. Views on a MAIN table therefore
-- never bind a detail form; rows saved before that rule still carry one, and this clears them.
--
-- NOT an init-scripts/00-schema file on purpose: 00-schema runs at table-creation time, before
-- demo/seed function units are imported, so on a fresh database it would run against an empty
-- dw_main_table_view_configs and clear nothing. It also never runs against existing databases.
-- New data cannot reach this state at all — MainTableViewServiceImpl rejects it, and the import
-- and clone paths drop it.
--
-- Run once per existing environment. Idempotent: safe to re-run.
--
--   docker exec -i <pg-container> psql -U <user> -d <db> \
--       -f /path/to/clear-main-view-detail-form.sql

UPDATE dw_main_table_view_configs v
   SET detail_form_id = NULL
  FROM dw_table_definitions td
 WHERE td.id = v.main_table_id
   AND td.table_type = 'MAIN'
   AND v.detail_form_id IS NOT NULL;
