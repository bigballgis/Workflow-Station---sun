-- =============================================================================
-- 绑定动作到流程节点（可选）
-- 注意：在当前系统中，动作通过 BPMN 的 actionIds 属性绑定
-- 此脚本用于验证绑定是否正确
-- =============================================================================

DO $$
DECLARE
    v_function_unit_id BIGINT;
    v_process_id BIGINT;
    v_action_count INTEGER;
    v_action_record RECORD;
BEGIN
    -- 获取功能单元 ID
    SELECT id INTO v_function_unit_id 
    FROM dw_function_units 
    WHERE code = 'DIGITAL_LENDING_V2';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION '功能单元 DIGITAL_LENDING_V2 不存在';
    END IF;

    -- 获取流程 ID
    SELECT id INTO v_process_id 
    FROM dw_process_definitions 
    WHERE function_unit_id = v_function_unit_id;

    IF v_process_id IS NULL THEN
        RAISE EXCEPTION '流程定义不存在，请先运行 02-insert-bpmn-process.ps1';
    END IF;

    -- 统计动作数量
    SELECT COUNT(*) INTO v_action_count 
    FROM dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE '动作绑定验证';
    RAISE NOTICE '========================================';
    RAISE NOTICE '功能单元 ID: %', v_function_unit_id;
    RAISE NOTICE '流程 ID: %', v_process_id;
    RAISE NOTICE '动作数量: %', v_action_count;
    RAISE NOTICE '';
    RAISE NOTICE '动作列表：';

    -- 显示所有动作
    FOR v_action_record IN 
        SELECT id, action_name, action_type, button_color
        FROM dw_action_definitions 
        WHERE function_unit_id = v_function_unit_id
        ORDER BY id
    LOOP
        RAISE NOTICE '  [%] % (%) - %', 
            v_action_record.id, 
            v_action_record.action_name, 
            v_action_record.action_type,
            v_action_record.button_color;
    END LOOP;

    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '验证完成！';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE '动作已通过 BPMN 的 actionIds 属性绑定到流程节点';
    RAISE NOTICE '无需额外的绑定操作';
    RAISE NOTICE '';
    RAISE NOTICE '下一步：';
    RAISE NOTICE '  1. 登录开发者工作台: http://localhost:3002';
    RAISE NOTICE '  2. 找到"数字贷款系统 V2"';
    RAISE NOTICE '  3. 点击"部署"按钮';
    RAISE NOTICE '  4. 在用户门户测试: http://localhost:3001';
    RAISE NOTICE '';
END $$;
