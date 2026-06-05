-- Rename sys_action_definitions.display_name → description (align with admin-center ActionDefinition entity)

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sys_action_definitions'
          AND column_name = 'display_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sys_action_definitions'
          AND column_name = 'description'
    ) THEN
        ALTER TABLE sys_action_definitions RENAME COLUMN display_name TO description;
    END IF;
END
$$;

COMMENT ON COLUMN sys_action_definitions.description IS 'Action description imported from Developer Workstation';
