-- BI targets now use Admin runtime identities. Retain legacy bindings without any DW cascade.
DO $$
DECLARE designer_constraint RECORD;
BEGIN
    FOR designer_constraint IN
        SELECT c.conname FROM pg_constraint c
        WHERE c.contype='f' AND c.conrelid='bi_data_view_assignment'::regclass
          AND c.confrelid IN ('dw_function_units'::regclass,'dw_table_definitions'::regclass,
                             'dw_main_table_view_configs'::regclass)
    LOOP
        EXECUTE format('ALTER TABLE bi_data_view_assignment DROP CONSTRAINT %I',designer_constraint.conname);
    END LOOP;
END $$;
