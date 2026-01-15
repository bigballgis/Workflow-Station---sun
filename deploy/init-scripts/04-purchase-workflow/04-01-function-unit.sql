-- 采购申请功能单元
INSERT INTO dw_function_units (code, name, description, icon_id, status, current_version, created_by, created_at, updated_at)
VALUES (
    'fu-purchase-request',
    '采购申请',
    '采购申请流程，支持多级审批、金额分级、部门会签等功能，覆盖所有任务分配类型和8种动作类型',
    (SELECT id FROM dw_icons WHERE name = 'credit-card' LIMIT 1),
    'DRAFT',
    NULL,
    'system',
    NOW(),
    NOW()
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();
