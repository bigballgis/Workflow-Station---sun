-- =====================================================
-- 采购工作流 - 操作按钮定义（简化版）
-- =====================================================

-- 删除旧的 Action 定义
DELETE FROM dw_action_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'PURCHASE');

-- 1. 提交采购申请
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'submit_purchase_request', 'PROCESS_SUBMIT', '提交采购申请进入审批流程',
    '{"processKey":"purchase_approval_process","requireComment":false,"confirmMessage":"确认提交采购申请吗？","successMessage":"采购申请已提交，等待审批","updateFields":{"status":"PENDING"}}'::jsonb,
    'send', 'primary', true
FROM dw_function_units WHERE code = 'PURCHASE';

-- 2. 保存草稿
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'save_draft', 'SAVE', '保存采购申请为草稿',
    '{"requireComment":false,"successMessage":"草稿已保存","updateFields":{"status":"DRAFT"}}'::jsonb,
    'save', 'default', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 3. 部门经理审批
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'dept_manager_approve', 'APPROVE', '部门经理批准采购申请',
    '{"targetStatus":"DEPT_APPROVED","requireComment":true,"commentLabel":"审批意见","confirmMessage":"确认批准此采购申请吗？","allowedRoles":["DEPT_MANAGER","MANAGER"],"successMessage":"采购申请已批准，转交财务审批"}'::jsonb,
    'check', 'success', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 4. 部门经理拒绝
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'dept_manager_reject', 'REJECT', '部门经理拒绝采购申请',
    '{"targetStatus":"REJECTED","requireComment":true,"requireReason":true,"commentLabel":"拒绝原因","confirmMessage":"确认拒绝此采购申请吗？","allowedRoles":["DEPT_MANAGER","MANAGER"],"successMessage":"采购申请已拒绝"}'::jsonb,
    'close', 'danger', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 5. 财务审批
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'finance_approve', 'APPROVE', '财务批准采购申请',
    '{"targetStatus":"APPROVED","requireComment":true,"commentLabel":"审批意见","confirmMessage":"确认批准此采购申请吗？","allowedRoles":["FINANCE_MANAGER","CFO"],"successMessage":"采购申请已完成审批"}'::jsonb,
    'check-circle', 'success', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 6. 财务拒绝
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'finance_reject', 'REJECT', '财务拒绝采购申请',
    '{"targetStatus":"REJECTED","requireComment":true,"requireReason":true,"commentLabel":"拒绝原因","confirmMessage":"确认拒绝此采购申请吗？","allowedRoles":["FINANCE_MANAGER","CFO"],"successMessage":"采购申请已拒绝"}'::jsonb,
    'close-circle', 'danger', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 7. 选择供应商
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    fu.id, 'select_supplier', 'FORM_POPUP', '选择供应商',
    ('{"formId":' || COALESCE(f.id::text, 'null') || ',"formName":"supplier_selector_form","popupWidth":"900px","popupTitle":"选择供应商","selectionMode":"single","successMessage":"供应商已选择"}')::jsonb,
    'team', 'info', false
FROM dw_function_units fu
LEFT JOIN dw_form_definitions f ON f.form_name = 'supplier_selector_form' AND f.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE';

-- 8. 查看审批历史
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    fu.id, 'view_approval_history', 'FORM_POPUP', '查看审批历史记录',
    ('{"formId":' || COALESCE(f.id::text, 'null') || ',"formName":"approval_action_form","popupWidth":"800px","popupTitle":"审批历史","readOnly":true,"showSubmitButton":false}')::jsonb,
    'history', 'default', false
FROM dw_function_units fu
LEFT JOIN dw_form_definitions f ON f.form_name = 'approval_action_form' AND f.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE';

-- 9. 撤回申请
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'withdraw_request', 'CANCEL', '撤回采购申请',
    '{"targetStatus":"WITHDRAWN","requireComment":true,"commentLabel":"撤回原因","confirmMessage":"确认撤回此采购申请吗？撤回后需要重新提交。","allowedRoles":["APPLICANT"],"allowedStatuses":["PENDING","DEPT_APPROVED"],"successMessage":"采购申请已撤回"}'::jsonb,
    'rollback', 'warning', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 10. 打印采购单
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'print_purchase_order', 'EXPORT', '打印采购单',
    '{"exportType":"PDF","template":"purchase_order_template","fileName":"采购单_{{request_no}}.pdf","includeItems":true,"includeApprovalHistory":true,"allowedStatuses":["APPROVED"],"successMessage":"采购单已生成"}'::jsonb,
    'printer', 'default', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 11. 导出采购明细
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'export_items', 'EXPORT', '导出采购明细到 Excel',
    '{"exportType":"EXCEL","fileName":"采购明细_{{request_no}}.xlsx","includeFields":["item_name","specification","quantity","unit","unit_price","subtotal","remarks"],"successMessage":"采购明细已导出"}'::jsonb,
    'download', 'default', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 12. 计算总金额
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, config_json, icon, button_color, is_default
)
SELECT 
    id, 'calculate_total', 'API_CALL', '自动计算采购总金额',
    '{"url":"/api/purchase/calculate-total","method":"POST","parameters":{"requestId":"{{current.id}}","items":"{{purchase_items}}"},"updateFields":{"total_amount":"{{response.totalAmount}}"},"autoTrigger":true,"triggerOn":["items_changed"],"successMessage":"总金额已更新"}'::jsonb,
    'calculator', 'info', false
FROM dw_function_units WHERE code = 'PURCHASE';

-- 验证结果
SELECT 
    a.id,
    a.action_name,
    a.action_type,
    a.description,
    a.icon,
    a.button_color,
    a.is_default
FROM dw_action_definitions a
JOIN dw_function_units fu ON a.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE'
ORDER BY a.id;

