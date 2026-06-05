-- =============================================================================
-- V315: Rename dw_field_definitions.description → display_name
--
-- Background: PRD docs/table-design-fk-pk-requirements.md (Module 0)
-- The "description" column on dw_field_definitions has always been used as
-- the field's display name (not as a free-form note). PRD unifies the API
-- and UI under a single "displayName" name; we drop the old column and use
-- display_name going forward.
--
-- Safe under both Flyway upgrades (column exists → rename) and fresh installs
-- via init-scripts (column already named display_name → no-op).
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dw_field_definitions'
          AND column_name = 'description'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'dw_field_definitions'
              AND column_name = 'display_name'
        ) THEN
            -- Both columns exist (mixed state). Backfill display_name from
            -- description where empty, then drop description.
            UPDATE dw_field_definitions
            SET display_name = description
            WHERE display_name IS NULL AND description IS NOT NULL;

            ALTER TABLE dw_field_definitions DROP COLUMN description;
        ELSE
            ALTER TABLE dw_field_definitions RENAME COLUMN description TO display_name;
        END IF;
    END IF;
END
$$;
