-- Icon name is a display label (usually the uploaded filename), not a catalog key.
-- Function Units bind icons by icon_id. Designers commonly upload the same filename
-- (e.g. sample.svg) onto another Function Unit; UNIQUE(name) rejected that insert.
--
-- Idempotent: safe to re-run on an existing database.
-- NOTE for existing environments: init-scripts only run on FIRST container start.
-- Apply manually:
--   docker exec -i platform-postgres-dev psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/78-dw-icons-name-not-unique.sql

ALTER TABLE dw_icons DROP CONSTRAINT IF EXISTS dw_icons_name_key;
ALTER TABLE dw_icons DROP CONSTRAINT IF EXISTS uk_dw_icons_name;

DROP INDEX IF EXISTS dw_icons_name_key;
DROP INDEX IF EXISTS uk_dw_icons_name;

-- Keep a non-unique lookup index for search / AI name matching.
CREATE INDEX IF NOT EXISTS idx_dw_icons_name ON dw_icons(name);

COMMENT ON COLUMN dw_icons.name IS
    'Display label, typically the uploaded filename without extension. Not unique; identity is id.';
