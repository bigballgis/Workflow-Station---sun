-- =====================================================
-- Additional schema for decision definitions, table relations,
-- form type rename, task form fields, and form stage bindings.
-- Corresponds to Flyway V301, V305, V306, V307, V308.
-- =====================================================

-- Decision Definitions (V301)
CREATE TABLE IF NOT EXISTS dw_decision_definitions (
    id               BIGSERIAL       PRIMARY KEY,
    function_unit_id BIGINT          NOT NULL,
    decision_key     VARCHAR(100)    NOT NULL,
    decision_name    VARCHAR(200),
    dmn_xml          TEXT,
    hit_policy       VARCHAR(20),
    description      TEXT,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_decision_function_unit
        FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE,
    CONSTRAINT uk_decision_fu_key
        UNIQUE (function_unit_id, decision_key)
);
CREATE INDEX IF NOT EXISTS idx_decision_function_unit_id ON dw_decision_definitions(function_unit_id);

-- Table Relations (V305)
CREATE TABLE IF NOT EXISTS dw_table_relations (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id) ON DELETE CASCADE,
    source_table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    source_field_name VARCHAR(100) NOT NULL,
    relation_type VARCHAR(20) NOT NULL CHECK (relation_type IN ('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_MANY')),
    target_table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    target_field_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_table_relations_fu_id ON dw_table_relations(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_table_relations_source ON dw_table_relations(source_table_id);
CREATE INDEX IF NOT EXISTS idx_table_relations_target ON dw_table_relations(target_table_id);

-- Form type rename (V306)
-- Rename MAIN→PROCESS, SUB→TASK, remove POPUP; align with Java FormType enum
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_form_type'
          AND conrelid = 'dw_form_definitions'::regclass
    ) THEN
        ALTER TABLE dw_form_definitions DROP CONSTRAINT chk_form_type;
    END IF;

    UPDATE dw_form_definitions SET form_type = 'PROCESS' WHERE form_type = 'MAIN';
    UPDATE dw_form_definitions SET form_type = 'TASK'    WHERE form_type = 'SUB';
    UPDATE dw_form_definitions SET form_type = 'ACTION'  WHERE form_type = 'POPUP';

    ALTER TABLE dw_form_definitions
        ADD CONSTRAINT chk_form_type CHECK (form_type IN ('PROCESS', 'TASK', 'ACTION'));
END $$;

-- Task form fields (V307)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='dw_form_definitions' AND column_name='field_permissions') THEN
        ALTER TABLE dw_form_definitions ADD COLUMN field_permissions JSONB DEFAULT '{}';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='dw_form_definitions' AND column_name='show_live_values') THEN
        ALTER TABLE dw_form_definitions ADD COLUMN show_live_values BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;
END $$;

-- Form Stage Bindings (V308)
CREATE TABLE IF NOT EXISTS dw_form_stage_bindings (
    id            BIGSERIAL PRIMARY KEY,
    form_id       BIGINT NOT NULL REFERENCES dw_form_definitions(id) ON DELETE CASCADE,
    stage_id      VARCHAR(255) NOT NULL,
    stage_name    VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(form_id, stage_id)
);
CREATE INDEX IF NOT EXISTS idx_form_stage_bindings_stage_id ON dw_form_stage_bindings(stage_id);
