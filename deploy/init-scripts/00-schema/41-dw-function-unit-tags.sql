-- Align with Flyway V322: custom tags on function units (no seed data — tags are user-defined only)
ALTER TABLE dw_function_units
    ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN dw_function_units.tags IS 'User-defined tags for filtering (JSON string array)';

-- PostgreSQL helper: check if a JSONB array contains ALL elements from a text array.
-- Used by server-side tag filter (AND semantics).
CREATE OR REPLACE FUNCTION jsonb_exists_all(data jsonb, keys text[])
RETURNS boolean
LANGUAGE sql
IMMUTABLE
RETURNS NULL ON NULL INPUT
AS 'SELECT data ?& keys';
