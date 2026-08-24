-- Relation Table <-> Function Unit becomes many-to-many: a table may be linked to multiple
-- Function Units, or to none (Common — visible/usable across all Function Units).
-- rt_table_definitions.function_unit_id (single FK, see 70-add-rt-function-unit.sql) is kept
-- as-is for backward compatibility but is no longer written by new code; this table is the
-- new source of truth going forward.
CREATE TABLE IF NOT EXISTS rt_table_function_units (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    relation_table_id   BIGINT       NOT NULL REFERENCES rt_table_definitions(id) ON DELETE CASCADE,
    function_unit_id    VARCHAR(64)  NOT NULL REFERENCES sys_function_units(id) ON DELETE CASCADE,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          VARCHAR(64),
    CONSTRAINT uk_rt_table_fu UNIQUE (relation_table_id, function_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_rt_table_fu_table ON rt_table_function_units(relation_table_id);
CREATE INDEX IF NOT EXISTS idx_rt_table_fu_fu ON rt_table_function_units(function_unit_id);

COMMENT ON TABLE rt_table_function_units IS 'Many-to-many link between rt_table_definitions and sys_function_units; a table with no rows here is Common (visible to all Function Units)';

-- One-time backfill: migrate any existing single-FK assignment into the new link table.
INSERT INTO rt_table_function_units (id, relation_table_id, function_unit_id, created_at, created_by)
SELECT gen_random_uuid()::text, t.id, t.function_unit_id, now(), 'system-migration'
FROM rt_table_definitions t
WHERE t.function_unit_id IS NOT NULL
ON CONFLICT (relation_table_id, function_unit_id) DO NOTHING;
