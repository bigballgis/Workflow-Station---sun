-- Freeze cross-FU table metadata in the consuming Admin release. The copy survives parent
-- disable/delete; source_catalog_id is provenance, not a live dependency or activation pointer.
CREATE TABLE IF NOT EXISTS ac_external_table_snapshots (
    catalog_id VARCHAR(64) NOT NULL REFERENCES sys_function_units(id) ON DELETE CASCADE,
    function_unit_code VARCHAR(50) NOT NULL,
    table_name VARCHAR(200) NOT NULL,
    source_catalog_id VARCHAR(64) NOT NULL,
    definition JSONB NOT NULL,
    PRIMARY KEY(catalog_id, function_unit_code, table_name)
);
CREATE OR REPLACE FUNCTION protect_external_table_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'An external table snapshot cannot be changed';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS external_table_snapshot_immutable ON ac_external_table_snapshots;
CREATE TRIGGER external_table_snapshot_immutable BEFORE UPDATE ON ac_external_table_snapshots
    FOR EACH ROW EXECUTE FUNCTION protect_external_table_snapshot();
