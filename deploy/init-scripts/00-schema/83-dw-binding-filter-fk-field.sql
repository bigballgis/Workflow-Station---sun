\set ON_ERROR_STOP on

-- =====================================================
-- Developer Workstation: per-binding filter FK declaration
-- =====================================================
-- Records WHICH declared foreign key a given sub-table binding filters its
-- rows by, as an explicit per-binding statement instead of something the
-- runtime infers by scanning the table.
--
-- Why this has to be per-binding: four separate places answer "what is this
-- sub-table's role" by scanning ALL foreign keys of the physical table and
-- taking the first / any match --
--   frontend user-portal  miBindingKindFromConfig.ts   hasFieldFkTo (anyMatch)
--   backend  user-portal  MiSubTaskSubTableRowMerger   foreignKeyTargetsMainTable (anyMatch)
--   backend  developer-workstation FormTableBindingRestorer soleDeclaredForeignKeyField (findFirst)
--   backend  developer-workstation FormTableBindingRestorer resolveBindingLinkMode (findFirst)
-- Those answers are correct today only because every table that declares a
-- foreign key declares exactly one: measured in dev, 14 of 14 such tables have
-- n_fk = 1, and no table mixes a MAIN-bound and a SUB-bound key. Binding two
-- roles of the same table to one form -- the point of same-table multi-binding
-- -- requires a second declared key on that table, and at that moment all four
-- start picking one silently.
--
-- Stores dw_field_definitions.id, NOT the column name: a name-based reference
-- breaks the moment a designer renames the column, which is exactly the class
-- of failure the runtime already suffered (the demo Function Unit renaming
-- sub_task_id -> sub_task_idq made every name-matching judgement answer wrong).
--
-- No FOREIGN KEY constraint, matching dw_field_definitions.ref_table_id: import
-- writes bindings and field definitions in separate passes, and a deleted field
-- leaving a dangling id resolves to no row -> NULL -> the existing table-level
-- fallback, which is the safe direction. BIGSERIAL never reuses ids, so a stale
-- id cannot silently start pointing at a different field.
--
-- Keep idempotent: ADD COLUMN IF NOT EXISTS + the backfill only touches rows
-- still NULL.
-- =====================================================

ALTER TABLE dw_form_table_bindings
  ADD COLUMN IF NOT EXISTS filter_fk_field_id BIGINT;

COMMENT ON COLUMN dw_form_table_bindings.filter_fk_field_id IS
  'dw_field_definitions.id of the declared foreign key this SUB binding filters rows by. NULL = not declared; runtime falls back to scanning the table''s foreign keys, which is only unambiguous while the table declares exactly one.';

CREATE INDEX IF NOT EXISTS idx_dw_form_table_bindings_filter_fk
  ON dw_form_table_bindings(filter_fk_field_id);

-- Backfill existing bindings.
--
-- Deterministic, not a guess: HAVING count(*) = 1 restricts the update to
-- tables that declare exactly ONE foreign key, where "the key this binding
-- filters by" has a single possible answer. Tables declaring none, or more than
-- one, are left NULL for a designer to state explicitly.
--
-- This window is why the backfill runs now rather than alongside the readers:
-- it is unambiguous only while n_fk = 1 holds for every table (true in dev as
-- of 2026-09-16). Once a second key is declared on a table, backfilling its
-- bindings stops being computable and becomes a human decision.
UPDATE dw_form_table_bindings b
SET filter_fk_field_id = sole.field_id
FROM (
    SELECT f.table_id, min(f.id) AS field_id
    FROM dw_field_definitions f
    WHERE f.is_foreign_key = true
      AND f.ref_table_id IS NOT NULL
    GROUP BY f.table_id
    HAVING count(*) = 1
) sole
WHERE b.table_id = sole.table_id
  AND b.binding_type = 'SUB'
  AND b.filter_fk_field_id IS NULL;
