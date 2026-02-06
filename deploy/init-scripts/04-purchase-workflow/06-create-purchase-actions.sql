-- =====================================================
-- 采购工作流 - 操作按钮定义
-- =====================================================
-- 此脚本为采购工作流创建操作按钮（Actions）
-- 前置条件：需要先创建功能单元和表单定义

-- =====================================================
-- 1. 提交采购申请
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'submit_purchase_request',
    'PROCESS_SUBMIT',
    '提交采购申请进入审批流程',
    '{
        "processKey": "purchase_approval_process",
        "requireComment": false,
        "confirmMessage": "确认提交采购申请吗？",
        "successMessage": "采购申请已提交，等待审批",
        "updateFields": {
            "status": "PENDING"
        },
        "validation": {
            "requiredFields": ["request_no", "title", "department", "total_amount"],
            "minAmount": 0,
            "maxAmount": 1000000
        }
    }'::jsonb,
    'send',
    'primary',
    true
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 2. 保存草稿
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'save_draft',
    'SAVE',
    '保存采购申请为草稿',
    jsonb_build_object(
        'requireComment', false,
        'confirmMessage', null,
        'successMessage', '草稿已保存',
        'updateFields', jsonb_build_object(
            'status', 'DRAFT'
        ),
        'validation', jsonb_build_object(
            'requiredFields', jsonb_build_array('title')
        )
    ),
    'save',
    'default',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 3. 部门经理审批
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'dept_manager_approve',
    'APPROVE',
    '部门经理批准采购申请',
    jsonb_build_object(
        'targetStatus', 'DEPT_APPROVED',
        'requireComment', true,
        'commentLabel', '审批意见',
        'confirmMessage', '确认批准此采购申请吗？',
        'allowedRoles', jsonb_build_array('DEPT_MANAGER', 'MANAGER'),
        'successMessage', '采购申请已批准，转交财务审批',
        'nextStep', 'FINANCE_APPROVAL',
        'updateFields', jsonb_build_object(
            'dept_approval_status', 'APPROVED',
            'dept_approval_time', 'CURRENT_TIMESTAMP',
            'dept_approver', '{{currentUser}}'
        )
    ),
    'check',
    'success',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 4. 部门经理拒绝
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'dept_manager_reject',
    'REJECT',
    '部门经理拒绝采购申请',
    jsonb_build_object(
        'targetStatus', 'REJECTED',
        'requireComment', true,
        'requireReason', true,
        'commentLabel', '拒绝原因',
        'confirmMessage', '确认拒绝此采购申请吗？',
        'allowedRoles', jsonb_build_array('DEPT_MANAGER', 'MANAGER'),
        'successMessage', '采购申请已拒绝',
        'updateFields', jsonb_build_object(
            'status', 'REJECTED',
            'dept_approval_status', 'REJECTED',
            'dept_approval_time', 'CURRENT_TIMESTAMP',
            'dept_approver', '{{currentUser}}'
        )
    ),
    'close',
    'danger',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 5. 财务审批
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'finance_approve',
    'APPROVE',
    '财务批准采购申请',
    jsonb_build_object(
        'targetStatus', 'APPROVED',
        'requireComment', true,
        'commentLabel', '审批意见',
        'confirmMessage', '确认批准此采购申请吗？',
        'allowedRoles', jsonb_build_array('FINANCE_MANAGER', 'CFO'),
        'successMessage', '采购申请已完成审批',
        'updateFields', jsonb_build_object(
            'status', 'APPROVED',
            'finance_approval_status', 'APPROVED',
            'finance_approval_time', 'CURRENT_TIMESTAMP',
            'finance_approver', '{{currentUser}}'
        )
    ),
    'check-circle',
    'success',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 6. 财务拒绝
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'finance_reject',
    'REJECT',
    '财务拒绝采购申请',
    jsonb_build_object(
        'targetStatus', 'REJECTED',
        'requireComment', true,
        'requireReason', true,
        'commentLabel', '拒绝原因',
        'confirmMessage', '确认拒绝此采购申请吗？',
        'allowedRoles', jsonb_build_array('FINANCE_MANAGER', 'CFO'),
        'successMessage', '采购申请已拒绝',
        'updateFields', jsonb_build_object(
            'status', 'REJECTED',
            'finance_approval_status', 'REJECTED',
            'finance_approval_time', 'CURRENT_TIMESTAMP',
            'finance_approver', '{{currentUser}}'
        )
    ),
    'close-circle',
    'danger',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 7. 选择供应商（弹窗表单）
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'select_supplier',
    'FORM_POPUP',
    '选择供应商',
    jsonb_build_object(
        'formId', (SELECT id FROM dw_form_definitions WHERE form_name = 'supplier_selector_form' AND function_unit_id = fu.id),
        'formName', 'supplier_selector_form',
        'popupWidth', '900px',
        'popupTitle', '选择供应商',
        'selectionMode', 'single',
        'requireComment', false,
        'successMessage', '供应商已选择',
        'updateFields', jsonb_build_object(
            'supplier_id', '{{selected.id}}',
            'supplier_name', '{{selected.supplier_name}}',
            'supplier_code', '{{selected.supplier_code}}'
        )
    ),
    'team',
    'info',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 8. 查看审批历史（弹窗表单）
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'view_approval_history',
    'FORM_POPUP',
    '查看审批历史记录',
    jsonb_build_object(
        'formId', (SELECT id FROM dw_form_definitions WHERE form_name = 'approval_action_form' AND function_unit_id = fu.id),
        'formName', 'approval_action_form',
        'popupWidth', '800px',
        'popupTitle', '审批历史',
        'readOnly', true,
        'showSubmitButton', false,
        'dataSource', jsonb_build_object(
            'table', 'purchase_approvals',
            'filter', jsonb_build_object(
                'request_id', '{{current.id}}'
            ),
            'orderBy', 'approval_time DESC'
        )
    ),
    'history',
    'default',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 9. 撤回申请
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'withdraw_request',
    'CANCEL',
    '撤回采购申请',
    jsonb_build_object(
        'targetStatus', 'WITHDRAWN',
        'requireComment', true,
        'commentLabel', '撤回原因',
        'confirmMessage', '确认撤回此采购申请吗？撤回后需要重新提交。',
        'allowedRoles', jsonb_build_array('APPLICANT'),
        'allowedStatuses', jsonb_build_array('PENDING', 'DEPT_APPROVED'),
        'successMessage', '采购申请已撤回',
        'updateFields', jsonb_build_object(
            'status', 'WITHDRAWN',
            'withdrawn_time', 'CURRENT_TIMESTAMP',
            'withdrawn_by', '{{currentUser}}'
        )
    ),
    'rollback',
    'warning',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 10. 打印采购单
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'print_purchase_order',
    'EXPORT',
    '打印采购单',
    jsonb_build_object(
        'exportType', 'PDF',
        'template', 'purchase_order_template',
        'fileName', '采购单_{{request_no}}.pdf',
        'includeItems', true,
        'includeApprovalHistory', true,
        'allowedStatuses', jsonb_build_array('APPROVED'),
        'successMessage', '采购单已生成'
    ),
    'printer',
    'default',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 11. 导出采购明细
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'export_items',
    'EXPORT',
    '导出采购明细到 Excel',
    jsonb_build_object(
        'exportType', 'EXCEL',
        'fileName', '采购明细_{{request_no}}.xlsx',
        'includeFields', jsonb_build_array(
            'item_name', 'specification', 'quantity', 'unit', 'unit_price', 'subtotal', 'remarks'
        ),
        'successMessage', '采购明细已导出'
    ),
    'download',
    'default',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 12. 计算总金额（API 调用）
-- =====================================================
INSERT INTO dw_action_definitions (
    function_unit_id,
    action_name,
    action_type,
    description,
    config_json,
    icon,
    button_color,
    is_default
)
SELECT 
    fu.id,
    'calculate_total',
    'API_CALL',
    '自动计算采购总金额',
    jsonb_build_object(
        'url', '/api/purchase/calculate-total',
        'method', 'POST',
        'parameters', jsonb_build_object(
            'requestId', '{{current.id}}',
            'items', '{{purchase_items}}'
        ),
        'updateFields', jsonb_build_object(
            'total_amount', '{{response.totalAmount}}'
        ),
        'autoTrigger', true,
        'triggerOn', jsonb_build_array('items_changed'),
        'successMessage', '总金额已更新'
    ),
    'calculator',
    'info',
    false
FROM dw_function_units fu
WHERE fu.code = 'PURCHASE'
ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 查询所有操作定义
SELECT 
    a.id,
    a.action_name,
    a.action_type,
    a.description,
    a.icon,
    a.button_color,
    a.is_default,
    fu.name as function_unit_name
FROM dw_action_definitions a
JOIN dw_function_units fu ON a.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE'
ORDER BY 
    CASE a.action_type
        WHEN 'PROCESS_SUBMIT' THEN 1
        WHEN 'SAVE' THEN 2
        WHEN 'APPROVE' THEN 3
        WHEN 'REJECT' THEN 4
        WHEN 'FORM_POPUP' THEN 5
        WHEN 'CANCEL' THEN 6
        WHEN 'EXPORT' THEN 7
        WHEN 'API_CALL' THEN 8
        ELSE 9
    END,
    a.action_name;

