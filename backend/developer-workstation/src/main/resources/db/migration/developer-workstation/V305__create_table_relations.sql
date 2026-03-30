CREATE TABLE dw_table_relations (
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

CREATE INDEX idx_table_relations_fu_id ON dw_table_relations(function_unit_id);
CREATE INDEX idx_table_relations_source ON dw_table_relations(source_table_id);
CREATE INDEX idx_table_relations_target ON dw_table_relations(target_table_id);
