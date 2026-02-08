-- =============================================================================
-- 更新 Admin Center 中的表单配置
-- 将修复后的表单配置从 Developer Workstation 同步到 Admin Center
-- =============================================================================

DO $$
DECLARE
    v_function_unit_id UUID;
    v_form_record RECORD;
    v_form_json TEXT;
BEGIN
    -- 获取 Admin Center 中的功能单元 ID
    SELECT id INTO v_function_unit_id 
    FROM sys_function_units 
    WHERE code = 'DIGITAL_LENDING_V2_EN' 
    ORDER BY created_at DESC 
    LIMIT 1;

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION '功能单元不存在: DIGITAL_LENDING_V2_EN';
    END IF;

    RAISE NOTICE '功能单元 ID: %', v_function_unit_id;

    -- 删除旧的表单内容
    DELETE FROM sys_function_unit_contents 
    WHERE function_unit_id = v_function_unit_id::text 
    AND content_type = 'FORM';

    RAISE NOTICE '已删除旧的表单内容';

    -- 从 Developer Workstation 导出表单配置到 Admin Center
    FOR v_form_record IN 
        SELECT id, form_name, form_type, config_json::text as config_text
        FROM dw_form_definitions 
        WHERE function_unit_id = 10
        ORDER BY id
    LOOP
        -- 构建完整的表单 JSON
        v_form_json := json_build_object(
            'formName', v_form_record.form_name,
            'formType', v_form_record.form_type,
            'configJson', v_form_record.config_text::jsonb
        )::text;

        -- 插入到 Admin Center
        INSERT INTO sys_function_unit_contents (
            id,
            function_unit_id,
            content_type,
            content_name,
            content_data,
            source_id,
            created_at
        ) VALUES (
            gen_random_uuid()::text,
            v_function_unit_id::text,
            'FORM',
            v_form_record.form_name,
            v_form_json,
            v_form_record.id::text,
            CURRENT_TIMESTAMP
        );

        RAISE NOTICE '已导出表单: % (ID: %)', v_form_record.form_name, v_form_record.id;
    END LOOP;

    RAISE NOTICE '========================================';
    RAISE NOTICE '表单配置已成功同步到 Admin Center！';
    RAISE NOTICE '========================================';
END $$;
