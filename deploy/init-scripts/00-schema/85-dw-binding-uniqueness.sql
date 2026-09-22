\set ON_ERROR_STOP on

-- =====================================================
-- Same-form binding uniqueness (P1 at the database)
-- =====================================================
-- Application policy lives in FormTableBindingUniquenessGuard:
--   SUB may share a table when filter_fk_field_id differs
--   two SUB rows with the same filter (including both NULL) are duplicates
--   PRIMARY / RELATED / ACTION stay one binding per table (or relation table)
--
-- Postgres UNIQUE treats NULL as distinct, so a plain UNIQUE
-- (form_id, table_id, filter_fk_field_id) would still allow two undeclared
-- SUB filters. Partial indexes close that hole.
--
-- Mix of SUB + non-SUB on the same table stays a Java-guard check: these
-- indexes do not collapse those two roles into one slot.
--
-- Scan before applying on a predating environment; a non-empty result means
-- the CREATE UNIQUE INDEX below will fail until the extra rows are removed:
--   SELECT form_id, table_id, filter_fk_field_id, count(*), array_agg(id)
--   FROM dw_form_table_bindings
--   WHERE binding_type = 'SUB' AND table_id IS NOT NULL AND filter_fk_field_id IS NOT NULL
--   GROUP BY 1, 2, 3 HAVING count(*) > 1;
--   SELECT form_id, table_id, count(*), array_agg(id)
--   FROM dw_form_table_bindings
--   WHERE binding_type = 'SUB' AND table_id IS NOT NULL AND filter_fk_field_id IS NULL
--   GROUP BY 1, 2 HAVING count(*) > 1;
--   SELECT form_id, table_id, count(*), array_agg(id)
--   FROM dw_form_table_bindings
--   WHERE binding_type <> 'SUB' AND table_id IS NOT NULL
--   GROUP BY 1, 2 HAVING count(*) > 1;
--   SELECT form_id, relation_table_id, count(*), array_agg(id)
--   FROM dw_form_table_bindings
--   WHERE relation_table_id IS NOT NULL
--   GROUP BY 1, 2 HAVING count(*) > 1;
-- =====================================================

DO $$
DECLARE
    n integer;
BEGIN
    SELECT count(*) INTO n FROM (
        SELECT 1
        FROM dw_form_table_bindings
        WHERE binding_type = 'SUB'
          AND table_id IS NOT NULL
          AND filter_fk_field_id IS NOT NULL
        GROUP BY form_id, table_id, filter_fk_field_id
        HAVING count(*) > 1
    ) d;
    IF n > 0 THEN
        RAISE EXCEPTION
            'uk_dw_ftb_sub_declared_filter blocked: % duplicate SUB (form, table, filter_fk) groups',
            n;
    END IF;

    SELECT count(*) INTO n FROM (
        SELECT 1
        FROM dw_form_table_bindings
        WHERE binding_type = 'SUB'
          AND table_id IS NOT NULL
          AND filter_fk_field_id IS NULL
        GROUP BY form_id, table_id
        HAVING count(*) > 1
    ) d;
    IF n > 0 THEN
        RAISE EXCEPTION
            'uk_dw_ftb_sub_undeclared_filter blocked: % duplicate undeclared-filter SUB groups',
            n;
    END IF;

    SELECT count(*) INTO n FROM (
        SELECT 1
        FROM dw_form_table_bindings
        WHERE binding_type <> 'SUB'
          AND table_id IS NOT NULL
        GROUP BY form_id, table_id
        HAVING count(*) > 1
    ) d;
    IF n > 0 THEN
        RAISE EXCEPTION
            'uk_dw_ftb_non_sub_table blocked: % duplicate non-SUB (form, table) groups',
            n;
    END IF;

    SELECT count(*) INTO n FROM (
        SELECT 1
        FROM dw_form_table_bindings
        WHERE relation_table_id IS NOT NULL
        GROUP BY form_id, relation_table_id
        HAVING count(*) > 1
    ) d;
    IF n > 0 THEN
        RAISE EXCEPTION
            'uk_dw_ftb_relation_table blocked: % duplicate (form, relation_table) groups',
            n;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dw_ftb_sub_declared_filter
    ON dw_form_table_bindings (form_id, table_id, filter_fk_field_id)
    WHERE binding_type = 'SUB'
      AND table_id IS NOT NULL
      AND filter_fk_field_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dw_ftb_sub_undeclared_filter
    ON dw_form_table_bindings (form_id, table_id)
    WHERE binding_type = 'SUB'
      AND table_id IS NOT NULL
      AND filter_fk_field_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dw_ftb_non_sub_table
    ON dw_form_table_bindings (form_id, table_id)
    WHERE binding_type <> 'SUB'
      AND table_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dw_ftb_relation_table
    ON dw_form_table_bindings (form_id, relation_table_id)
    WHERE relation_table_id IS NOT NULL;

COMMENT ON INDEX uk_dw_ftb_sub_declared_filter IS
    'SUB bindings: one (form, table, declared filter FK) pair.';
COMMENT ON INDEX uk_dw_ftb_sub_undeclared_filter IS
    'SUB bindings: at most one undeclared filter_fk_field_id per (form, table).';
COMMENT ON INDEX uk_dw_ftb_non_sub_table IS
    'PRIMARY / RELATED / ACTION: one binding per (form, table).';
COMMENT ON INDEX uk_dw_ftb_relation_table IS
    'One binding per (form, relation_table_id).';
