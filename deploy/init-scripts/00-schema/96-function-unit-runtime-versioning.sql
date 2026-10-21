BEGIN;
-- Released definitions remain independent of mutable Developer Workstation designs.
ALTER TABLE sys_function_units ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(64);
ALTER TABLE sys_action_definitions ADD COLUMN IF NOT EXISTS source_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_action_catalog_source
    ON sys_action_definitions(function_unit_id, source_id) WHERE source_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS up_function_unit_runtime_tables (
    id BIGSERIAL PRIMARY KEY,
    catalog_id VARCHAR(64) NOT NULL REFERENCES sys_function_units(id) ON DELETE RESTRICT,
    source_table_id VARCHAR(64) NOT NULL,
    definition JSONB NOT NULL,
    UNIQUE(catalog_id, source_table_id)
);
ALTER TABLE dw_table_data_rows ADD COLUMN IF NOT EXISTS runtime_table_id BIGINT
    REFERENCES up_function_unit_runtime_tables(id) ON DELETE RESTRICT;
ALTER TABLE dw_table_data_rows ADD COLUMN IF NOT EXISTS process_instance_id VARCHAR(64);
ALTER TABLE dw_table_data_rows ALTER COLUMN table_id DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_runtime_table_row
    ON dw_table_data_rows(runtime_table_id, row_id) WHERE runtime_table_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_runtime_table_process
    ON dw_table_data_rows(runtime_table_id, process_instance_id) WHERE runtime_table_id IS NOT NULL;
-- Existing DW preview rows retain table_id; migrated/new Portal rows use runtime_table_id.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_dw_row_definition_owner') THEN
        ALTER TABLE dw_table_data_rows ADD CONSTRAINT chk_dw_row_definition_owner
            CHECK ((table_id IS NOT NULL) <> (runtime_table_id IS NOT NULL));
    END IF;
END $$;

-- A completed request needs its definition just as much as an in-flight request.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_up_process_runtime_catalog') THEN
        ALTER TABLE up_process_instance ADD CONSTRAINT fk_up_process_runtime_catalog
            FOREIGN KEY(function_unit_catalog_id) REFERENCES sys_function_units(id)
            ON DELETE RESTRICT NOT VALID;
    END IF;
END $$;

-- Older clients persisted a display composite (key:version:<actual-id>) instead of the engine id.
-- Only a join to the engine repository is sufficient evidence to normalize it.
DROP TRIGGER IF EXISTS protect_released_fu_content ON sys_function_unit_contents;
UPDATE sys_function_unit_contents c SET flowable_process_definition_id=p.id_
    FROM act_re_procdef p
    WHERE c.content_type='PROCESS'
      AND c.flowable_process_definition_id=concat(p.key_, ':', p.version_, ':', p.id_);

CREATE OR REPLACE FUNCTION protect_released_fu_content() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_function_units u WHERE u.id=OLD.function_unit_id
               AND (u.process_deployed OR u.status IN ('DEPLOYED','ARCHIVED','DEPRECATED')))
       OR EXISTS (SELECT 1 FROM up_process_instance p WHERE p.function_unit_catalog_id=OLD.function_unit_id) THEN
        IF TG_OP='DELETE' THEN
            RAISE EXCEPTION 'VERSION_CONTENT_IMMUTABLE';
        END IF;
        -- The first Engine association may flush after its FU deployed flag. It is write-once,
        -- and is never invented for a release already referenced by historical requests.
        IF TG_TABLE_NAME='sys_function_unit_contents' THEN
            IF OLD.flowable_process_definition_id IS NULL AND OLD.flowable_deployment_id IS NULL
               AND NEW.flowable_process_definition_id IS NOT NULL AND NEW.flowable_deployment_id IS NOT NULL
               AND (to_jsonb(NEW)-'flowable_process_definition_id'-'flowable_deployment_id')
                   = (to_jsonb(OLD)-'flowable_process_definition_id'-'flowable_deployment_id')
               AND NOT EXISTS (SELECT 1 FROM up_process_instance p WHERE p.function_unit_catalog_id=OLD.function_unit_id) THEN
                RETURN NEW;
            END IF;
        END IF;
        -- source_id was omitted by older imports. Only association metadata may be repaired;
        -- names/config/content remain immutable and the original package checksum is verified by Admin.
        IF OLD.source_id IS NULL AND NEW.source_id IS NOT NULL
           AND (to_jsonb(NEW)-'updated_at'-'source_id') = (to_jsonb(OLD)-'updated_at'-'source_id') THEN
            RETURN NEW;
        END IF;
        IF (to_jsonb(NEW)-'updated_at') IS DISTINCT FROM (to_jsonb(OLD)-'updated_at') THEN
            RAISE EXCEPTION 'VERSION_CONTENT_IMMUTABLE';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS protect_released_fu_content ON sys_function_unit_contents;
CREATE TRIGGER protect_released_fu_content BEFORE UPDATE OR DELETE ON sys_function_unit_contents
    FOR EACH ROW EXECUTE FUNCTION protect_released_fu_content();
DROP TRIGGER IF EXISTS protect_released_fu_action ON sys_action_definitions;
CREATE TRIGGER protect_released_fu_action BEFORE UPDATE OR DELETE ON sys_action_definitions
    FOR EACH ROW EXECUTE FUNCTION protect_released_fu_content();

CREATE OR REPLACE FUNCTION protect_process_version_pin() RETURNS trigger AS $$
BEGIN
    -- A missing legacy association can be recovered once; an existing association is immutable.
    -- Missing descriptive fields may be filled from the same trusted catalog, without changing
    -- either the catalog or definition. Already populated fields remain write-once.
    IF OLD.function_unit_catalog_id IS NOT NULL
       AND NEW.function_unit_catalog_id IS NOT DISTINCT FROM OLD.function_unit_catalog_id
       AND NEW.process_definition_id IS NOT DISTINCT FROM OLD.process_definition_id
       AND (NULLIF(BTRIM(OLD.function_unit_code),'') IS NULL
            OR NEW.function_unit_code IS NOT DISTINCT FROM OLD.function_unit_code)
       AND (NULLIF(BTRIM(OLD.function_unit_version_label),'') IS NULL
            OR NEW.function_unit_version_label IS NOT DISTINCT FROM OLD.function_unit_version_label)
       AND EXISTS (SELECT 1 FROM sys_function_units u WHERE u.id=OLD.function_unit_catalog_id
            AND u.code=NEW.function_unit_code AND u.version=NEW.function_unit_version_label) THEN
        RETURN NEW;
    END IF;
    IF OLD.function_unit_catalog_id IS NOT NULL AND
       (NEW.function_unit_catalog_id IS DISTINCT FROM OLD.function_unit_catalog_id
        OR NEW.function_unit_code IS DISTINCT FROM OLD.function_unit_code
        OR NEW.function_unit_version_label IS DISTINCT FROM OLD.function_unit_version_label
        OR NEW.process_definition_id IS DISTINCT FROM OLD.process_definition_id) THEN
        RAISE EXCEPTION 'VERSION_CONTEXT_MODIFICATION_DENIED';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS protect_process_version_pin ON up_process_instance;
CREATE TRIGGER protect_process_version_pin BEFORE UPDATE ON up_process_instance
    FOR EACH ROW EXECUTE FUNCTION protect_process_version_pin();

CREATE OR REPLACE FUNCTION protect_released_fu_identity() RETURNS trigger AS $$
BEGIN
    IF (OLD.process_deployed OR OLD.status IN ('DEPLOYED','ARCHIVED','DEPRECATED')
        OR EXISTS (SELECT 1 FROM up_process_instance p WHERE p.function_unit_catalog_id=OLD.id))
       AND (NEW.id IS DISTINCT FROM OLD.id OR NEW.code IS DISTINCT FROM OLD.code
            OR NEW.version IS DISTINCT FROM OLD.version OR NEW.content_checksum IS DISTINCT FROM OLD.content_checksum) THEN
        RAISE EXCEPTION 'VERSION_CONTENT_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS protect_released_fu_identity ON sys_function_units;
CREATE TRIGGER protect_released_fu_identity BEFORE UPDATE ON sys_function_units
    FOR EACH ROW EXECUTE FUNCTION protect_released_fu_identity();
COMMIT;
