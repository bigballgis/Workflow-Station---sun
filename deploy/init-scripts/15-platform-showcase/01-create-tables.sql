-- =============================================================================
-- 15-platform-showcase: 表设计（MAIN / SUB / RELATION / ACTION）
-- =============================================================================

DO $tables$
DECLARE
    v_fu_id         BIGINT;
    v_main_id       BIGINT;
    v_sub_id        BIGINT;
    v_relation_id   BIGINT;
    v_action_tbl_id BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'PLATFORM_SHOWCASE';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'PLATFORM_SHOWCASE not found. Run 00-create-function-unit.sql first.';
    END IF;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_fu_id, 'ShowcaseApp', 'MAIN', '演示主表', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_main_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_main_id;
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_main_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, '主键', 1),
    (v_main_id, 'app_no', 'VARCHAR', 50, NULL, NULL, false, NULL, false, true, '申请编号', 2),
    (v_main_id, 'title', 'VARCHAR', 200, NULL, NULL, false, NULL, false, false, '标题', 3),
    (v_main_id, 'amount', 'DECIMAL', NULL, 14, 2, false, NULL, false, false, '金额', 4),
    (v_main_id, 'tier', 'VARCHAR', 20, NULL, NULL, true, NULL, false, false, '分层', 5),
    (v_main_id, 'status', 'VARCHAR', 30, NULL, NULL, false, 'DRAFT', false, false, '状态', 6),
    (v_main_id, 'created_by', 'VARCHAR', 100, NULL, NULL, false, NULL, false, false, '创建人', 7),
    (v_main_id, 'created_at', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, '创建时间', 8);

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_fu_id, 'ShowcaseLine', 'SUB', '演示子表', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_sub_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_sub_id;
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_sub_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, '行 ID', 1),
    (v_sub_id, 'showcase_app_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, '主表外键', 2),
    (v_sub_id, 'line_desc', 'VARCHAR', 500, NULL, NULL, true, NULL, false, false, '行说明', 3),
    (v_sub_id, 'quantity', 'INTEGER', NULL, NULL, NULL, false, '1', false, false, '数量', 4);

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_fu_id, 'ShowcaseDoc', 'RELATION', '演示关联附件表', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_relation_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_relation_id;
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_relation_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'ID', 1),
    (v_relation_id, 'showcase_app_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, '主表外键', 2),
    (v_relation_id, 'file_name', 'VARCHAR', 255, NULL, NULL, false, NULL, false, false, '文件名', 3),
    (v_relation_id, 'file_type', 'VARCHAR', 100, NULL, NULL, true, NULL, false, false, '类型', 4);

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_fu_id, 'ShowcaseAudit', 'ACTION', '演示动作审计表', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type, description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_tbl_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_action_tbl_id;
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_action_tbl_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'ID', 1),
    (v_action_tbl_id, 'showcase_app_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, '主表外键', 2),
    (v_action_tbl_id, 'action_name', 'VARCHAR', 100, NULL, NULL, false, NULL, false, false, '动作名', 3),
    (v_action_tbl_id, 'action_at', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, '时间', 4),
    (v_action_tbl_id, 'comments', 'TEXT', NULL, NULL, NULL, true, NULL, false, false, '备注', 5);

    RAISE NOTICE 'Showcase tables: main=%, sub=%, relation=%, action_tbl=%',
        v_main_id, v_sub_id, v_relation_id, v_action_tbl_id;
    RAISE NOTICE 'Next: 02-create-bpmn-process.sql';

END $tables$;
