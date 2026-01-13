# 采购申请功能单元 - 生成所有SQL文件
# 使用 [System.IO.File]::WriteAllText() 确保UTF-8编码正确

$outputDir = $PSScriptRoot

# ========== 04-03-fields-main.sql ==========
$fieldsMainSql = @"
-- 采购申请 - 主表字段 (purchase_request)
-- 实际表结构: table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order

-- 申请编号
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, is_unique, description, sort_order)
SELECT t.id, 'request_no', 'VARCHAR', 50, false, true, '申请编号', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请标题
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'title', 'VARCHAR', 200, false, '申请标题', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请人
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'applicant', 'VARCHAR', 50, false, '申请人', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请部门
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'department', 'VARCHAR', 100, false, '申请部门', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 申请日期
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'apply_date', 'DATE', false, '申请日期', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 采购类型
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'purchase_type', 'VARCHAR', 50, false, '采购类型', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 紧急程度
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'urgency', 'VARCHAR', 20, false, '紧急程度', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 预计总金额
INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, nullable, description, sort_order)
SELECT t.id, 'total_amount', 'DECIMAL', 18, 2, false, '预计总金额', 8
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 币种
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, default_value, description, sort_order)
SELECT t.id, 'currency', 'VARCHAR', 10, 'CNY', '币种', 9
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 采购原因
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'reason', 'TEXT', 2000, false, '采购原因', 10
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 期望交付日期
INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'expected_delivery_date', 'DATE', '期望交付日期', 11
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 收货地址
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'delivery_address', 'VARCHAR', 500, '收货地址', 12
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 联系人
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'contact_person', 'VARCHAR', 50, '联系人', 13
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 联系电话
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'contact_phone', 'VARCHAR', 20, '联系电话', 14
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 附件
INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'attachments', 'TEXT', '附件', 15
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 备注
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'remarks', 'TEXT', 1000, '备注', 16
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';

-- 状态
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, default_value, description, sort_order)
SELECT t.id, 'status', 'VARCHAR', 20, 'DRAFT', '状态', 17
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_request';
"@

[System.IO.File]::WriteAllText("$outputDir\04-03-fields-main.sql", $fieldsMainSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-03-fields-main.sql"


# ========== 04-04-fields-sub.sql ==========
$fieldsSubSql = @"
-- 采购申请 - 子表/关联表/动作表字段

-- ========== 采购明细子表字段 (purchase_item) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'item_name', 'VARCHAR', 200, false, '物品名称', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'specification', 'VARCHAR', 200, '规格型号', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'unit', 'VARCHAR', 20, '单位', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'quantity', 'INTEGER', false, '数量', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'unit_price', 'DECIMAL', 18, 2, '单价', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'amount', 'DECIMAL', 18, 2, '金额', 6
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'item_remarks', 'TEXT', 500, '备注', 7
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_item';

-- ========== 供应商信息关联表字段 (supplier_info) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'supplier_name', 'VARCHAR', 200, false, '供应商名称', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_code', 'VARCHAR', 50, '供应商编码', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_contact', 'VARCHAR', 50, '联系人', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_phone', 'VARCHAR', 20, '联系电话', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'supplier_address', 'VARCHAR', 500, '地址', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'supplier_info';

-- ========== 预算信息关联表字段 (budget_info) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'budget_code', 'VARCHAR', 50, false, '预算编码', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'budget_name', 'VARCHAR', 200, '预算名称', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'budget_amount', 'DECIMAL', 18, 2, '预算金额', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'used_amount', 'DECIMAL', 18, 2, '已用金额', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, precision_value, scale, description, sort_order)
SELECT t.id, 'available_amount', 'DECIMAL', 18, 2, '可用金额', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'budget_info';

-- ========== 审批记录动作表字段 (purchase_approval) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approver', 'VARCHAR', 50, false, '审批人', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description, sort_order)
SELECT t.id, 'approve_time', 'TIMESTAMP', false, '审批时间', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'approve_result', 'VARCHAR', 20, false, '审批结果', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'approve_comment', 'TEXT', 1000, '审批意见', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'task_name', 'VARCHAR', 100, '任务节点', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'purchase_approval';

-- ========== 会签记录动作表字段 (countersign_record) ==========
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description, sort_order)
SELECT t.id, 'signer', 'VARCHAR', 50, false, '会签人', 1
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'signer_dept', 'VARCHAR', 100, '会签部门', 2
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, description, sort_order)
SELECT t.id, 'sign_time', 'TIMESTAMP', '会签时间', 3
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'sign_result', 'VARCHAR', 20, '会签结果', 4
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';

INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, description, sort_order)
SELECT t.id, 'sign_comment', 'TEXT', 1000, '会签意见', 5
FROM dw_table_definitions t JOIN dw_function_units f ON t.function_unit_id = f.id
WHERE f.code = 'fu-purchase-request' AND t.table_name = 'countersign_record';
"@

[System.IO.File]::WriteAllText("$outputDir\04-04-fields-sub.sql", $fieldsSubSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-04-fields-sub.sql"


# ========== 04-06-forms.sql ==========
$formsSql = @"
-- 采购申请 - 表单定义
-- 实际表结构: function_unit_id, form_name, form_type, config_json, description, bound_table_id

-- 主表单: 采购申请表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '采购申请主表单', 'MAIN', '采购申请的主表单，包含申请基本信息'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 子表单: 采购明细表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '采购明细表单', 'SUB', '采购物品明细子表单'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 动作表单: 审批表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '审批表单', 'ACTION', '审批操作表单'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 弹出表单: 供应商选择
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '供应商选择', 'POPUP', '供应商选择弹出表单'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 弹出表单: 预算查询
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '预算查询', 'POPUP', '预算信息查询弹出表单'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 动作表单: 会签表单
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, description)
SELECT f.id, '会签表单', 'ACTION', '会签操作表单'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@

[System.IO.File]::WriteAllText("$outputDir\04-06-forms.sql", $formsSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-06-forms.sql"

# ========== 04-07-actions.sql ==========
$actionsSql = @"
-- 采购申请 - 动作定义
-- 实际表结构: function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default

-- 1. 提交流程
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '提交申请', 'PROCESS_SUBMIT', '提交采购申请', 'primary', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 2. 同意
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '同意', 'APPROVE', '同意审批', 'success', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 3. 拒绝
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '拒绝', 'REJECT', '拒绝审批', 'danger', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 4. 转办
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '转办', 'TRANSFER', '转办给其他人处理', 'warning', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 5. 回退
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '回退', 'ROLLBACK', '回退到上一节点', 'warning', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 6. 撤回
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default)
SELECT f.id, '撤回', 'WITHDRAW', '撤回已提交的申请', 'default', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 7. API调用 - 查询预算
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '查询预算', 'API_CALL', '调用API查询预算信息', 'info', false, '{"url":"/api/budget/query","method":"GET"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 8. 脚本执行 - 计算金额
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '计算金额', 'SCRIPT', '执行脚本计算总金额', 'default', false, '{"script":"calculateTotal"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 9. 保存草稿
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '保存草稿', 'SCRIPT', '保存为草稿', 'default', false, '{"script":"saveDraft"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- 10. 发起会签
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, button_color, is_default, config_json)
SELECT f.id, '发起会签', 'API_CALL', '发起多部门会签', 'info', false, '{"url":"/api/countersign/start","method":"POST"}'::jsonb
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@

[System.IO.File]::WriteAllText("$outputDir\04-07-actions.sql", $actionsSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-07-actions.sql"


# ========== 04-08-process.sql (BPMN with all 7 assignment types) ==========
# BPMN XML - 使用英文避免编码问题
$bpmnXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions 
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:custom="http://custom.bpmn.io/schema"
    id="Definitions_PurchaseRequest" 
    targetNamespace="http://workflow.example.com/purchase-request">
  <bpmn:process id="Process_PurchaseRequest" name="Purchase Request Process" isExecutable="true">
    <bpmn:extensionElements>
      <custom:properties>
        <custom:property name="globalActionIds" value="[6,9]"/>
      </custom:properties>
    </bpmn:extensionElements>
    
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <bpmn:userTask id="Task_Submit" name="Submit Request">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="1"/>
          <custom:property name="formName" value="Purchase Request Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[1,9]"/>
          <custom:property name="assigneeType" value="INITIATOR"/>
          <custom:property name="assigneeLabel" value="Initiator"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_DeptReview" name="Department Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="DEPT_OTHERS"/>
          <custom:property name="assigneeLabel" value="Department Others"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_ManagerApproval" name="Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="ENTITY_MANAGER"/>
          <custom:property name="assigneeLabel" value="Entity Manager"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_Amount" name="Amount Check">
      <bpmn:incoming>Flow_4</bpmn:incoming>
      <bpmn:outgoing>Flow_5</bpmn:outgoing>
      <bpmn:outgoing>Flow_6</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:userTask id="Task_FunctionManagerApproval" name="Function Manager Approval">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="FUNCTION_MANAGER"/>
          <custom:property name="assigneeLabel" value="Function Manager"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_7</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_ParentDeptReview" name="Parent Department Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="PARENT_DEPT"/>
          <custom:property name="assigneeLabel" value="Parent Department"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_7</bpmn:incoming>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_FinanceReview" name="Finance Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="Approval Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,7]"/>
          <custom:property name="assigneeType" value="FIXED_DEPT"/>
          <custom:property name="assigneeLabel" value="Finance Department"/>
          <custom:property name="assigneeValue" value="dept-finance-001"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_8</bpmn:incoming>
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_9</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:userTask id="Task_Countersign" name="Multi-Department Countersign">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="6"/>
          <custom:property name="formName" value="Countersign Form"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,10]"/>
          <custom:property name="assigneeType" value="VIRTUAL_GROUP"/>
          <custom:property name="assigneeLabel" value="Countersign Group"/>
          <custom:property name="assigneeValue" value="vg-countersign-001"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_9</bpmn:incoming>
      <bpmn:outgoing>Flow_10</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_10</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Submit"/>
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Submit" targetRef="Task_DeptReview"/>
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_DeptReview" targetRef="Task_ManagerApproval"/>
    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_ManagerApproval" targetRef="Gateway_Amount"/>
    <bpmn:sequenceFlow id="Flow_5" name="Amount >= 10000" sourceRef="Gateway_Amount" targetRef="Task_FunctionManagerApproval">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${total_amount >= 10000}]]></bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_6" name="Amount &lt; 10000" sourceRef="Gateway_Amount" targetRef="Task_FinanceReview">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${total_amount < 10000}]]></bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_7" sourceRef="Task_FunctionManagerApproval" targetRef="Task_ParentDeptReview"/>
    <bpmn:sequenceFlow id="Flow_8" sourceRef="Task_ParentDeptReview" targetRef="Task_FinanceReview"/>
    <bpmn:sequenceFlow id="Flow_9" sourceRef="Task_FinanceReview" targetRef="Task_Countersign"/>
    <bpmn:sequenceFlow id="Flow_10" sourceRef="Task_Countersign" targetRef="EndEvent_1"/>
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_PurchaseRequest">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="100" y="200" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Submit_di" bpmnElement="Task_Submit">
        <dc:Bounds x="200" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_DeptReview_di" bpmnElement="Task_DeptReview">
        <dc:Bounds x="360" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="520" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Amount_di" bpmnElement="Gateway_Amount" isMarkerVisible="true">
        <dc:Bounds x="680" y="193" width="50" height="50"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FunctionManagerApproval_di" bpmnElement="Task_FunctionManagerApproval">
        <dc:Bounds x="800" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ParentDeptReview_di" bpmnElement="Task_ParentDeptReview">
        <dc:Bounds x="960" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FinanceReview_di" bpmnElement="Task_FinanceReview">
        <dc:Bounds x="1120" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Countersign_di" bpmnElement="Task_Countersign">
        <dc:Bounds x="1280" y="178" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="1440" y="200" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="136" y="218"/>
        <di:waypoint x="200" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="300" y="218"/>
        <di:waypoint x="360" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
        <di:waypoint x="460" y="218"/>
        <di:waypoint x="520" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4">
        <di:waypoint x="620" y="218"/>
        <di:waypoint x="680" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_5_di" bpmnElement="Flow_5">
        <di:waypoint x="705" y="193"/>
        <di:waypoint x="705" y="120"/>
        <di:waypoint x="800" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_6_di" bpmnElement="Flow_6">
        <di:waypoint x="730" y="218"/>
        <di:waypoint x="1120" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_7_di" bpmnElement="Flow_7">
        <di:waypoint x="900" y="120"/>
        <di:waypoint x="960" y="120"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_8_di" bpmnElement="Flow_8">
        <di:waypoint x="1060" y="120"/>
        <di:waypoint x="1090" y="120"/>
        <di:waypoint x="1090" y="218"/>
        <di:waypoint x="1120" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_9_di" bpmnElement="Flow_9">
        <di:waypoint x="1220" y="218"/>
        <di:waypoint x="1280" y="218"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_10_di" bpmnElement="Flow_10">
        <di:waypoint x="1380" y="218"/>
        <di:waypoint x="1440" y="218"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
'@

# 转换为Base64
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($bpmnXml))

# 生成SQL
$processSql = @"
-- 采购申请 - 流程定义 (BPMN XML Base64编码)
-- 包含所有7种分配类型: INITIATOR, DEPT_OTHERS, ENTITY_MANAGER, FUNCTION_MANAGER, PARENT_DEPT, FIXED_DEPT, VIRTUAL_GROUP

INSERT INTO dw_process_definitions (function_unit_id, bpmn_xml)
SELECT f.id, '$base64'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
"@

[System.IO.File]::WriteAllText("$outputDir\04-08-process.sql", $processSql, [System.Text.Encoding]::UTF8)
Write-Host "Generated 04-08-process.sql"
Write-Host "Base64 length: $($base64.Length)"

Write-Host ""
Write-Host "All SQL files generated successfully!"
Write-Host "Execute in order:"
Write-Host "  04-03-fields-main.sql"
Write-Host "  04-04-fields-sub.sql"
Write-Host "  04-06-forms.sql"
Write-Host "  04-07-actions.sql"
Write-Host "  04-08-process.sql"
