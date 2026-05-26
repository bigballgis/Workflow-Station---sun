-- =============================================================================
-- Align all dw_* / rt_* BIGSERIAL `id` sequences with MAX(id).
--
-- Why this lives under 90-post-seed/ (its own stage, not 00-schema/, not 99-maintenance/):
--   This is the "finalize / post-seed" stage of `00-init-all.sh`:
--     * NOT schema DDL  -> would not belong under 00-schema/.
--     * NOT on-demand maintenance -> would not belong under 99-maintenance/.
--     * NOT tied to one Function Unit -> would not belong under 08-/15-/16-/17-.
--   It is "every init must run AFTER all seed scripts are loaded", an
--   independent stage that any future post-seed alignment can join.
--
-- Why this script is necessary:
--   `17-Multi-Instance-Subtask-Demo/00-init-kk.sql` is exported from a real
--   dev environment and uses `INSERT INTO ... (id, ...) VALUES (<explicit>, ...)`
--   across ~12 tables (dw_function_units, dw_table_definitions,
--   dw_field_definitions, dw_action_definitions, dw_form_definitions,
--   dw_form_table_bindings, dw_sub_table_view_configs, dw_sub_table_view_fields,
--   dw_table_relations, dw_process_definitions, rt_table_definitions,
--   rt_field_definitions, rt_table_versions). PostgreSQL `BIGSERIAL` does NOT
--   advance its sequence when an INSERT supplies an explicit value, so on first
--   container start `last_value` stays at 1 while `MAX(id)` is much higher.
--   The very first JPA `GenerationType.IDENTITY` insert through the app then
--   collides on the primary key:
--     ERROR: duplicate key value violates unique constraint "<table>_pkey"
--   (first reproduced on `dw_form_table_bindings_pkey` when the Designer's
--   Manage Table Bindings dialog tried to "Add" a sub-table binding).
--
-- Execution timing:
--   `00-init-all.sh` invokes this script explicitly in its post-seed stage,
--   right after the last seed package (17-Multi-Instance-Subtask-Demo).
--   Putting it any earlier would see empty tables and become a no-op.
--
-- Defensive coverage:
--   Dynamically discovers every `public.dw_*` and `public.rt_*` table that has
--   a serial sequence on `id`. So any future seed package using the same
--   explicit-id pattern is automatically protected -- no per-table list to
--   maintain. Idempotent; safe to re-run on every fresh init.
-- =============================================================================

DO $align_id_sequences$
DECLARE
  v_tbl  TEXT;
  v_seq  TEXT;
  v_next BIGINT;
BEGIN
  FOR v_tbl IN
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND (table_name LIKE 'dw\_%' ESCAPE '\'
        OR table_name LIKE 'rt\_%' ESCAPE '\')
    ORDER BY table_name
  LOOP
    v_seq := pg_get_serial_sequence(quote_ident(v_tbl), 'id');
    IF v_seq IS NULL THEN
      CONTINUE;
    END IF;

    EXECUTE format(
      'SELECT GREATEST(COALESCE((SELECT MAX(id) FROM %I), 0), 1)',
      v_tbl
    ) INTO v_next;

    PERFORM setval(v_seq::regclass, v_next);
  END LOOP;
END $align_id_sequences$;
