-- Rename sys_function_units.display_name → description (align with admin-center FunctionUnit entity)

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sys_function_units'
          AND column_name = 'display_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sys_function_units'
          AND column_name = 'description'
    ) THEN
        ALTER TABLE sys_function_units RENAME COLUMN display_name TO description;
    END IF;
END
$$;

COMMENT ON COLUMN sys_function_units.description IS 'Function unit description imported from Developer Workstation manifest';
