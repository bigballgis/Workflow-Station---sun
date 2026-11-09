-- WP12: the two runtime stores User Portal writes take Portal names. No runtime table starts with dw_
-- any more; dw_ is Developer Workstation's design data, which production does not have.
--   dw_table_data_rows -> up_table_data_rows  FORM_POPUP / ACTION rows of released function units
--   dw_pk_sequences    -> up_pk_sequences     primary key counters of released function unit tables
-- Their sequences, constraints and indexes follow (dw_ -> up_ in the name).
--
-- Ships with the services that use the new names (WP12); never run it under services that still use the
-- old ones.
--
-- Idempotent. Re-running the earlier scripts (04 / 77 / 96) re-creates an empty old table: it is dropped
-- here when empty; when both old and new hold rows the script stops, so nothing is lost.
BEGIN;

DO $$
DECLARE
    pair text[];
    old_name text;
    new_name text;
    r record;
BEGIN
    FOREACH pair SLICE 1 IN ARRAY ARRAY[['dw_table_data_rows', 'up_table_data_rows'],
                                         ['dw_pk_sequences', 'up_pk_sequences']]
    LOOP
        old_name := pair[1];
        new_name := pair[2];
        IF to_regclass('public.' || old_name) IS NOT NULL THEN
            IF to_regclass('public.' || new_name) IS NULL THEN
                EXECUTE format('ALTER TABLE %I RENAME TO %I', old_name, new_name);
            ELSE
                EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I)', old_name) INTO STRICT r;
                IF r.exists THEN
                    RAISE EXCEPTION '% and % both hold rows; move the rows of % first', old_name, new_name, old_name;
                END IF;
                EXECUTE format('DROP TABLE %I', old_name);
            END IF;
        END IF;

        IF to_regclass('public.' || new_name) IS NULL THEN
            CONTINUE;
        END IF;
        IF to_regclass('public.' || old_name || '_id_seq') IS NOT NULL
           AND to_regclass('public.' || new_name || '_id_seq') IS NULL THEN
            EXECUTE format('ALTER SEQUENCE %I RENAME TO %I', old_name || '_id_seq', new_name || '_id_seq');
        END IF;
        FOR r IN
            SELECT c.conname FROM pg_constraint c
            WHERE c.conrelid = ('public.' || new_name)::regclass AND c.conname LIKE '%dw\_%'
        LOOP
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                           new_name, r.conname, replace(r.conname, 'dw_', 'up_'));
        END LOOP;
        FOR r IN
            SELECT i.relname FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid
            WHERE x.indrelid = ('public.' || new_name)::regclass AND i.relname LIKE '%dw\_%'
              AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = x.indexrelid)
        LOOP
            EXECUTE format('ALTER INDEX %I RENAME TO %I', r.relname, replace(r.relname, 'dw_', 'up_'));
        END LOOP;
    END LOOP;
END $$;

DO $$
BEGIN
    IF to_regclass('public.up_table_data_rows') IS NOT NULL THEN
        COMMENT ON TABLE up_table_data_rows IS
            'User Portal runtime rows of released function unit tables (FORM_POPUP / ACTION), keyed by up_function_unit_runtime_tables';
    END IF;
    IF to_regclass('public.up_pk_sequences') IS NOT NULL THEN
        COMMENT ON TABLE up_pk_sequences IS
            'User Portal primary key counters of released function unit tables (keyed by the release''s source table id)';
    END IF;
END $$;

COMMIT;
