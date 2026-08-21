\set ON_ERROR_STOP on

-- =====================================================
-- Sub-table rows: backfill the row identity (row_id)
-- =====================================================
-- Companion to ProcessInstance#ensureSubTableRowIdentity, which from now on gives every row
-- reaching up_process_instance.variables.__subTables__ an identity. Rows written before that
-- callback existed can still be anonymous, and a reader has no honest way to tell two anonymous
-- rows apart: hashing their content merges two genuinely different rows that happen to carry
-- equal values. So the stored rows are identified here, once, instead of being guessed at on
-- every read.
--
-- Identity keys and their priority come from com.platform.common.jdbc.SubTableRowIdentity —
-- row_id, rowId, rowID, id_idw, _rowKey, rowKey, id — matched case-insensitively, with blank
-- values counting as absent. A row that already carries any of them keeps it untouched, so a
-- designer-allocated primary key always wins over a generated row_id.
--
-- Idempotent: rerunning finds nothing to assign and updates no rows.
-- =====================================================

CREATE OR REPLACE FUNCTION pg_temp.has_row_identity(row_elem jsonb)
RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT EXISTS (
        SELECT 1
        FROM jsonb_each_text(row_elem) AS kv(key, value)
        WHERE lower(kv.key) IN ('row_id', 'rowid', 'id_idw', '_rowkey', 'rowkey', 'id')
          AND btrim(coalesce(kv.value, '')) <> ''
    );
$$;

-- Mirrors SubTableRowIdentityEnricher: each key of __subTables__ holds one form binding's rows,
-- and a row may itself carry the one nesting level the sanitizer preserves.
CREATE OR REPLACE FUNCTION pg_temp.ensure_subtable_row_ids(sub_tables jsonb)
RETURNS jsonb LANGUAGE plpgsql AS $$
DECLARE
    slice_key  text;
    slice      jsonb;
    row_elem   jsonb;
    new_slice  jsonb;
    result     jsonb := sub_tables;
BEGIN
    IF sub_tables IS NULL OR jsonb_typeof(sub_tables) <> 'object' THEN
        RETURN sub_tables;
    END IF;

    FOR slice_key, slice IN SELECT key, value FROM jsonb_each(sub_tables) LOOP
        CONTINUE WHEN jsonb_typeof(slice) <> 'array';
        new_slice := '[]'::jsonb;

        FOR row_elem IN SELECT value FROM jsonb_array_elements(slice) LOOP
            IF jsonb_typeof(row_elem) = 'object' THEN
                IF NOT pg_temp.has_row_identity(row_elem) THEN
                    row_elem := jsonb_set(row_elem, '{row_id}',
                                          to_jsonb(gen_random_uuid()::text), true);
                END IF;
                IF jsonb_typeof(row_elem -> '__subTables__') = 'object' THEN
                    row_elem := jsonb_set(row_elem, '{__subTables__}',
                                          pg_temp.ensure_subtable_row_ids(row_elem -> '__subTables__'),
                                          true);
                END IF;
            END IF;
            new_slice := new_slice || jsonb_build_array(row_elem);
        END LOOP;

        result := jsonb_set(result, ARRAY[slice_key], new_slice, true);
    END LOOP;

    RETURN result;
END;
$$;

DO $$
DECLARE
    touched integer;
BEGIN
    -- The WHERE clause calls the function to decide whether anything is missing; SET calls it
    -- again to produce the value. Both are correct on their own, and comparing the rewrite with
    -- the original is what keeps a rerun from touching rows that are already identified.
    WITH updated AS (
        UPDATE up_process_instance pi
        SET variables = jsonb_set(pi.variables, '{__subTables__}',
                                  pg_temp.ensure_subtable_row_ids(pi.variables -> '__subTables__'),
                                  true)
        WHERE jsonb_typeof(pi.variables -> '__subTables__') = 'object'
          AND pg_temp.ensure_subtable_row_ids(pi.variables -> '__subTables__')
              IS DISTINCT FROM (pi.variables -> '__subTables__')
        RETURNING 1
    )
    SELECT count(*) INTO touched FROM updated;

    RAISE NOTICE 'sub-table row identity backfill: % process instance(s) updated', touched;
END $$;
