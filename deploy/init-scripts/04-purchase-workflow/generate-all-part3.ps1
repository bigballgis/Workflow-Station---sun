# 采购申请功能单元 - 生成SQL文件 Part 3

# 6. 表单定义
$sql6 = @"
-- 采购申请 - 表单定义

-- 主表单: 采购申请表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description, created_by)
SELECT f.id, '采购申请主表单', 'MAIN', '采购申请的主表单，包含申请基本信息', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 子表单: 采购明细表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description, created_by)
SELECT f.id, '采购明细表单', 'SUB', '采购物品明细子表单', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 审批表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description, created_by)
SELECT f.id, '审批表单', 'ACTION', '审批操作表单', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 弹出表单: 供应商选择
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description, created_by)
SELECT f.id, '供应商选择', 'POPUP', '供应商选择弹出表单', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 弹出表单: 预算查询
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description, created_by)
SELECT f.id, '预算查询', 'POPUP', '预算信息查询弹出表单', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 动作表单: 会签表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description, created_by)
SELECT f.id, '会签表单', 'ACTION', '会签操作表单', 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@
[System.IO.File]::WriteAllText("04-06-forms.sql", $sql6, [System.Text.Encoding]::UTF8)

# 7. 动作定义
$sql7 = @"
-- 采购申请 - 动作定义

-- 1. 提交流程
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '提交申请', 'PROCESS_SUBMIT', '提交', '提交采购申请', 1, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 2. 同意
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '同意', 'APPROVE', '同意', '同意审批', 2, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 3. 拒绝
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '拒绝', 'REJECT', '拒绝', '拒绝审批', 3, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 4. 转办
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '转办', 'TRANSFER', '转办', '转办给其他人处理', 4, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 5. 回退
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '回退', 'ROLLBACK', '回退', '回退到上一节点', 5, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 6. 撤回
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '撤回', 'WITHDRAW', '撤回', '撤回已提交的申请', 6, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 7. API调用 - 查询预算
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '查询预算', 'API_CALL', '查询预算', '调用API查询预算信息', 7, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 8. 脚本执行 - 计算金额
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '计算金额', 'SCRIPT', '计算', '执行脚本计算总金额', 8, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 9. 保存草稿
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '保存草稿', 'SCRIPT', '保存', '保存为草稿', 9, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 10. 发起会签
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, action_label, description, sort_order, created_by)
SELECT f.id, '发起会签', 'API_CALL', '会签', '发起多部门会签', 10, 'system'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@
[System.IO.File]::WriteAllText("04-07-actions.sql", $sql7, [System.Text.Encoding]::UTF8)

Write-Host "Generated: 04-06-forms.sql, 04-07-actions.sql"
