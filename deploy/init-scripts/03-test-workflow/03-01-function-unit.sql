-- 功能单元 (Function Unit)
INSERT INTO dw_function_units (code, name, description, icon_id, status, current_version, created_by)
VALUES (
    'fu-leave-request',
    '请假申请',
    '员工请假申请流程，支持年假、病假、事假等多种假期类型，包含审批流程',
    (SELECT id FROM dw_icons WHERE name = 'approval-check'),
    'DRAFT',
    NULL,
    'system'
);
