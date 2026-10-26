-- A rollback/import rebuilds table identities. Keep external BI configuration inspectable,
-- just as view_id intentionally has no cascading foreign key. Runtime still validates the
-- exact published View/FU/table tuple; a recreated table never inherits an old grant.
BEGIN;
DO $$
DECLARE table_constraint RECORD;
BEGIN
    FOR table_constraint IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
        WHERE c.contype = 'f'
          AND c.conrelid = 'bi_data_view_assignment'::regclass
          AND c.confrelid = 'dw_table_definitions'::regclass
          AND a.attname = 'table_id'
    LOOP
        EXECUTE format('ALTER TABLE bi_data_view_assignment DROP CONSTRAINT %I', table_constraint.conname);
    END LOOP;
END $$;
COMMIT;
