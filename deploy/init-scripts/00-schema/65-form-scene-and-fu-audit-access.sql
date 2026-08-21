-- ============================================================
-- Portal form scene split + FU-level audit access
-- ============================================================
-- Splits "which form renders this step" by *scene* so My Requests can be
-- designed independently of To Do, and adds a FU-level audit grant so
-- reviewers who are neither initiator nor participant can see every request
-- of a function unit.
--
-- Idempotent: safe to re-run on an existing database.
-- NOTE for existing environments: init-scripts only run on FIRST container
-- start. Apply manually:
--   docker exec -i <pg> psql -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/00-schema/65-form-scene-and-fu-audit-access.sql
-- ============================================================

-- --- 1. scene axis on per-node form bindings -----------------
ALTER TABLE dw_form_stage_bindings
    ADD COLUMN IF NOT EXISTS scene VARCHAR(16) NOT NULL DEFAULT 'TASK';

ALTER TABLE dw_form_stage_bindings
    DROP CONSTRAINT IF EXISTS chk_form_stage_binding_scene;
ALTER TABLE dw_form_stage_bindings
    ADD CONSTRAINT chk_form_stage_binding_scene CHECK (scene IN ('TASK', 'REQUEST'));

-- The original UNIQUE(form_id, stage_id) was declared inline, so Postgres
-- auto-named it. It must be dropped explicitly: a plain CREATE ... IF NOT
-- EXISTS never replaces an existing constraint, and while it survives, one
-- node cannot hold both a TASK and a REQUEST form — the whole point here.
ALTER TABLE dw_form_stage_bindings
    DROP CONSTRAINT IF EXISTS dw_form_stage_bindings_form_id_stage_id_key;
ALTER TABLE dw_form_stage_bindings
    DROP CONSTRAINT IF EXISTS uk_form_stage_binding_form_stage_scene;
ALTER TABLE dw_form_stage_bindings
    ADD CONSTRAINT uk_form_stage_binding_form_stage_scene UNIQUE (form_id, stage_id, scene);

-- --- 2. scene axis on form definitions -----------------------
-- Carries the REQUEST variant of the PROCESS form. The start step is not a
-- BPMN node, so it has no stage id to bind to; a synthetic stage id would
-- collide with node-id lookups and validation.
ALTER TABLE dw_form_definitions
    ADD COLUMN IF NOT EXISTS scene VARCHAR(16) NOT NULL DEFAULT 'TASK';

ALTER TABLE dw_form_definitions
    DROP CONSTRAINT IF EXISTS chk_form_definition_scene;
ALTER TABLE dw_form_definitions
    ADD CONSTRAINT chk_form_definition_scene CHECK (scene IN ('TASK', 'REQUEST'));

-- --- 3. DETAIL form type + per-view detail form --------------
-- The constraint is named chk_form_type in 04-developer-workstation-schema.sql;
-- the auto-generated name is dropped too in case some environment carries it.
ALTER TABLE dw_form_definitions
    DROP CONSTRAINT IF EXISTS chk_form_type;
ALTER TABLE dw_form_definitions
    DROP CONSTRAINT IF EXISTS dw_form_definitions_form_type_check;
ALTER TABLE dw_form_definitions
    ADD CONSTRAINT chk_form_type
    CHECK (form_type IN ('PROCESS', 'TASK', 'ACTION', 'DETAIL'));

ALTER TABLE dw_main_table_view_configs
    ADD COLUMN IF NOT EXISTS detail_form_id BIGINT;

ALTER TABLE dw_main_table_view_configs
    DROP CONSTRAINT IF EXISTS fk_mtv_configs_detail_form;
ALTER TABLE dw_main_table_view_configs
    ADD CONSTRAINT fk_mtv_configs_detail_form
    FOREIGN KEY (detail_form_id) REFERENCES dw_form_definitions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_mtv_configs_detail_form
    ON dw_main_table_view_configs(detail_form_id)
    WHERE detail_form_id IS NOT NULL;

-- --- 4. FU-level audit grant ---------------------------------
-- Deliberately a separate table rather than a new access_type on
-- sys_function_unit_access: every reader of that table filters on
-- target_type only and ignores access_type, so an AUDIT row there would be
-- read as a launch grant. A separate key space cannot leak that way.
CREATE TABLE IF NOT EXISTS sys_function_unit_audit_access (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT fk_audit_access_func_unit FOREIGN KEY (function_unit_id)
        REFERENCES sys_function_units(id) ON DELETE CASCADE,
    CONSTRAINT chk_audit_access_target_type CHECK (target_type IN ('ROLE'))
);

CREATE INDEX IF NOT EXISTS idx_fu_audit_access_func_unit
    ON sys_function_unit_audit_access(function_unit_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fu_audit_access_target
    ON sys_function_unit_audit_access(function_unit_id, target_type, target_id);

COMMENT ON TABLE sys_function_unit_audit_access IS
    'Roles allowed to review every request of a function unit without being initiator or participant.';
COMMENT ON COLUMN dw_form_stage_bindings.scene IS
    'TASK = To Do/Completed rendering; REQUEST = My Requests/audit rendering.';
COMMENT ON COLUMN dw_form_definitions.scene IS
    'TASK = To Do/Completed rendering; REQUEST = My Requests/audit rendering.';
COMMENT ON COLUMN dw_main_table_view_configs.detail_form_id IS
    'DETAIL form rendered when a row of this view is opened; NULL = no detail page.';
