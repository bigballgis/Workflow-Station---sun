-- =============================================================================
-- 16-meeting-participant-collection: Form stage bindings (增量补丁)
-- 若已执行过旧版 03 而未含阶段绑定，可单独执行本脚本。
-- 依赖: 00 → 01 → 03（表单与表绑定已存在）
-- =============================================================================

DO $stage$
DECLARE
    v_function_unit_id     BIGINT;
    v_create_form_id       BIGINT;
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units
    WHERE code = 'fu-20260403-a1b2c5';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c5 not found.';
    END IF;

    SELECT id INTO v_create_form_id FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Create Meeting Form';

    IF v_create_form_id IS NULL THEN
        RAISE EXCEPTION 'Create Meeting Form not found; run 00/03 first.';
    END IF;

    DELETE FROM dw_form_stage_bindings
    WHERE form_id IN (
        SELECT id FROM dw_form_definitions WHERE function_unit_id = v_function_unit_id
    );

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name) VALUES
    (v_create_form_id, 'Task_CreateMeeting', '创建会议'),
    (v_create_form_id, 'Task_AssignParticipants', '分配参与人');

    RAISE NOTICE 'Form stage bindings: form_id=% (Task_CreateMeeting + Task_AssignParticipants)', v_create_form_id;
END $stage$;
