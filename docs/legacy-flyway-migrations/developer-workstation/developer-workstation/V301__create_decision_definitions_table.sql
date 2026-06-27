-- =====================================================
-- Decision Definitions Table (dw_decision_definitions)
-- Stores DMN decision table definitions per function unit
-- Validates: Requirements 2.1, 2.2, 2.5, 16.1, 16.2, 16.3, 16.4, 16.5
-- =====================================================

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
        FOREIGN KEY (function_unit_id)
        REFERENCES dw_function_units(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_decision_fu_key
        UNIQUE (function_unit_id, decision_key)
);

CREATE INDEX IF NOT EXISTS idx_decision_function_unit_id ON dw_decision_definitions(function_unit_id);

COMMENT ON TABLE dw_decision_definitions IS 'DMN decision table definitions associated with function units';
COMMENT ON COLUMN dw_decision_definitions.decision_key IS 'Unique decision key within a function unit';
COMMENT ON COLUMN dw_decision_definitions.dmn_xml IS 'DMN 1.3 XML content';
COMMENT ON COLUMN dw_decision_definitions.hit_policy IS 'FIRST, UNIQUE, ANY, PRIORITY, COLLECT, RULE_ORDER, OUTPUT_ORDER';
