-- =============================================================================
-- 15-platform-showcase: 表关系（MAIN 1:N SUB）
-- =============================================================================

DO $rel$
DECLARE
    v_fu_id   BIGINT;
    v_main_id BIGINT;
    v_sub_id  BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'PLATFORM_SHOWCASE';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'PLATFORM_SHOWCASE not found.';
    END IF;

    SELECT id INTO v_main_id FROM dw_table_definitions WHERE function_unit_id = v_fu_id AND table_name = 'ShowcaseApp';
    SELECT id INTO v_sub_id FROM dw_table_definitions WHERE function_unit_id = v_fu_id AND table_name = 'ShowcaseLine';

    DELETE FROM dw_table_relations WHERE function_unit_id = v_fu_id;

    INSERT INTO dw_table_relations (
        function_unit_id, source_table_id, source_field_name, relation_type,
        target_table_id, target_field_name, created_at, updated_at
    ) VALUES (
        v_fu_id, v_main_id, 'id', 'ONE_TO_MANY', v_sub_id, 'showcase_app_id',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE 'PLATFORM_SHOWCASE table relation ShowcaseApp 1:N ShowcaseLine OK.';

END $rel$;
