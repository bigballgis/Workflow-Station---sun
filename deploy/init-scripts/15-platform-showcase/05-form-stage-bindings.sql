-- =============================================================================
-- 15-platform-showcase: 表单阶段绑定（BPMN userTask id ↔ 表单）
-- 与 digital-lending / meeting 脚本一致：stage_id = BPMN 中 userTask 的 id
-- 依赖: 00（表单已建）、02（BPMN 中 Task_SubmitShowcase / Task_ManagerShowcase）
-- =============================================================================

DO $stage$
DECLARE
    v_fu_id            BIGINT;
    v_request_form_id  BIGINT;
    v_approval_form_id BIGINT;
BEGIN
    SELECT id INTO v_fu_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c4';
    IF v_fu_id IS NULL THEN
        RAISE EXCEPTION 'fu-20260403-a1b2c4 not found.';
    END IF;

    SELECT id INTO v_request_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_fu_id AND form_name = 'Showcase Request Form';
    SELECT id INTO v_approval_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_fu_id AND form_name = 'Showcase Approval Form';

    IF v_request_form_id IS NULL OR v_approval_form_id IS NULL THEN
        RAISE EXCEPTION 'Showcase forms missing; run 00-create-function-unit.sql first.';
    END IF;

    DELETE FROM dw_form_stage_bindings
    WHERE form_id IN (v_request_form_id, v_approval_form_id);

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name) VALUES
    (v_request_form_id, 'Task_SubmitShowcase', '提交申请（发起人）'),
    (v_approval_form_id, 'Task_ManagerShowcase', '经理审批');

    RAISE NOTICE 'fu-20260403-a1b2c4 form_stage_bindings: request form -> Task_SubmitShowcase, approval -> Task_ManagerShowcase';
END $stage$;
