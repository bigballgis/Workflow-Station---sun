-- Enforce case-insensitive uniqueness on designer/relation table names at the physical layer.
-- Postgres unquoted DDL identifiers fold to lowercase, so case-only-different table_name values
-- (e.g. "ACQ_Transaction" vs "acq_transaction") were previously allowed by the plain UNIQUE
-- constraints (uk_dw_table_name / the UNIQUE on rt_table_definitions.table_name are both
-- case-sensitive) and by the application-level availability checks, letting two Function Units
-- register logically-colliding table names. Multi-instance sub-table resolution then had no
-- reliable way to tell them apart by name (see MiCollectionVariableBuilder, which now resolves
-- by subTableId instead of by name for exactly this reason).
--
-- Run the following scan first on any environment that predates this script; a non-empty result
-- means the index creation below will fail until the conflicting rows are renamed:
--   SELECT lower(table_name), array_agg(id ORDER BY id) FROM dw_table_definitions GROUP BY 1 HAVING COUNT(*) > 1;
--   SELECT lower(table_name), array_agg(id ORDER BY id) FROM rt_table_definitions GROUP BY 1 HAVING COUNT(*) > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_dw_table_name_lower ON dw_table_definitions (lower(table_name));
CREATE UNIQUE INDEX IF NOT EXISTS ux_rt_table_name_lower ON rt_table_definitions (lower(table_name));
