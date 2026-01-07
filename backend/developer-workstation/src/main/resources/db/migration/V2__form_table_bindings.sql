-- 表单多表绑定功能
-- 创建表单表绑定关系表

CREATE TABLE dw_form_table_bindings (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES dw_form_definitions(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id),
    binding_type VARCHAR(20) NOT NULL, -- PRIMARY, SUB, RELATED
    binding_mode VARCHAR(20) NOT NULL DEFAULT 'READONLY', -- EDITABLE, READONLY
    foreign_key_field VARCHAR(100), -- 子表关联主表的外键字段名
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(form_id, table_id)
);

-- 创建索引
CREATE INDEX idx_form_table_bindings_form ON dw_form_table_bindings(form_id);
CREATE INDEX idx_form_table_bindings_table ON dw_form_table_bindings(table_id);

-- 迁移现有的 bound_table_id 数据到新表
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order, created_at)
SELECT id, bound_table_id, 'PRIMARY', 'EDITABLE', 0, CURRENT_TIMESTAMP
FROM dw_form_definitions
WHERE bound_table_id IS NOT NULL;

-- 添加注释
COMMENT ON TABLE dw_form_table_bindings IS '表单表绑定关系表';
COMMENT ON COLUMN dw_form_table_bindings.form_id IS '表单ID';
COMMENT ON COLUMN dw_form_table_bindings.table_id IS '绑定的表ID';
COMMENT ON COLUMN dw_form_table_bindings.binding_type IS '绑定类型: PRIMARY-主表, SUB-子表, RELATED-关联表';
COMMENT ON COLUMN dw_form_table_bindings.binding_mode IS '绑定模式: EDITABLE-可编辑, READONLY-只读';
COMMENT ON COLUMN dw_form_table_bindings.foreign_key_field IS '子表/关联表的外键字段名';
COMMENT ON COLUMN dw_form_table_bindings.sort_order IS '排序顺序';
