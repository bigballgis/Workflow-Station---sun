-- =============================================================================
-- V206: Rename rt_field_definitions.comment → display_name
--
-- Background: PRD docs/table-design-fk-pk-requirements.md (Module 0)
-- The "comment" column on rt_field_definitions has been used as the field's
-- display name in Admin Center Relation Tables. PRD unifies the API and UI
-- under a single "displayName"; we drop the old column going forward.
--
-- Safe under both Flyway upgrades (column exists → rename) and fresh installs
-- via init-scripts (column already named display_name → no-op).
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'rt_field_definitions'
          AND column_name = 'comment'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'rt_field_definitions'
              AND column_name = 'display_name'
        ) THEN
            UPDATE rt_field_definitions
            SET display_name = comment
            WHERE display_name IS NULL AND comment IS NOT NULL;

            ALTER TABLE rt_field_definitions DROP COLUMN comment;
        ELSE
            ALTER TABLE rt_field_definitions RENAME COLUMN comment TO display_name;
        END IF;
    END IF;
END
$$;
