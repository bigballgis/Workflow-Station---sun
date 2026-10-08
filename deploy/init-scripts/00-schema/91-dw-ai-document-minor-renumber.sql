-- Function unit Requirements / Design documents: repair duplicate v<major>.<minor> labels.
-- The old AI Generate save path (chat-produced documents and its "User manual edit") never set
-- major_version / minor_version, so every row it wrote kept the column defaults and showed as v1.1.
-- Renumber minor_version within each (function unit, document type, major) by the internal
-- version order, so the labels are unique again (v1.1, v1.2, v1.3 …). major_version is left as is.
-- Idempotent: safe to re-run (rows already numbered correctly are not touched).
-- NOTE for existing environments: init-scripts only run on FIRST container start.
-- Apply manually:
--   docker exec -i platform-postgres-dev psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/91-dw-ai-document-minor-renumber.sql

UPDATE dw_ai_documents d
SET minor_version = r.minor
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY function_unit_id, document_type, major_version
                              ORDER BY version) AS minor
    FROM dw_ai_documents
) r
WHERE d.id = r.id
  AND d.minor_version <> r.minor;
