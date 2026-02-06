-- =====================================================
-- Platform Security Schema - Action Definitions Table
-- Table for storing action definitions in all environments
-- This table is separate from dw_action_definitions which only exists in dev
-- =====================================================

-- =====================================================
-- Action Definitions (sys_action_definitions)
-- Stores action definitions that are deployed with function units
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_action_definitions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    description TEXT,
    config_json JSONB DEFAULT '{}'::jsonb,
    icon VARCHAR(50),
    button_color VARCHAR(20),
    is_default BOOLEAN DEFAULT false,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    CONSTRAINT fk_action_function_unit FOREIGN KEY (function_unit_id) REFERENCES sys_function_units(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sys_action_function_unit ON sys_action_definitions(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_action_name ON sys_action_definitions(action_name);
CREATE INDEX IF NOT EXISTS idx_sys_action_type ON sys_action_definitions(action_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_action_name_fu ON sys_action_definitions(function_unit_id, action_name);

COMMENT ON TABLE sys_action_definitions IS 'Action definitions deployed with function units (all environments)';
COMMENT ON COLUMN sys_action_definitions.function_unit_id IS 'Reference to sys_function_units';
COMMENT ON COLUMN sys_action_definitions.action_type IS 'Action type: APPROVE, REJECT, FORM_POPUP, API_CALL, etc.';
COMMENT ON COLUMN sys_action_definitions.config_json IS 'Additional configuration in JSON format (formId, apiEndpoint, etc.)';
