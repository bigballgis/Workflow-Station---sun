# 采购流程 Actions 说明

## 已创建的 Actions

### 1. Submit (提交采购申请)
- **Action Type**: PROCESS_SUBMIT
- **Icon**: send
- **Button Color**: primary
- **Is Default**: true
- **用途**: 提交采购申请，启动审批流程
- **配置**:
  - processKey: purchase_approval_process
  - requireComment: false

### 2. Save Draft (保存草稿)
- **Action Type**: SAVE
- **Icon**: save
- **Button Color**: default
- **用途**: 保存采购申请为草稿，不启动流程
- **配置**:
  - requireComment: false

### 3. Department Approve (部门经理批准)
- **Action Type**: APPROVE
- **Icon**: check
- **Button Color**: success
- **用途**: 部门经理批准采购申请
- **配置**:
  - targetStatus: DEPT_APPROVED
  - requireComment: true

### 4. Department Reject (部门经理拒绝)
- **Action Type**: REJECT
- **Icon**: close
- **Button Color**: danger
- **用途**: 部门经理拒绝采购申请
- **配置**:
  - targetStatus: REJECTED
  - requireComment: true

### 5. Finance Approve (财务批准)
- **Action Type**: APPROVE
- **Icon**: check-circle
- **Button Color**: success
- **用途**: 财务部门批准采购申请
- **配置**:
  - targetStatus: APPROVED
  - requireComment: true

### 6. Finance Reject (财务拒绝)
- **Action Type**: REJECT
- **Icon**: close-circle
- **Button Color**: danger
- **用途**: 财务部门拒绝采购申请
- **配置**:
  - targetStatus: REJECTED
  - requireComment: true

### 7. Withdraw (撤回申请)
- **Action Type**: CANCEL
- **Icon**: rollback
- **Button Color**: warning
- **用途**: 申请人撤回采购申请
- **配置**:
  - targetStatus: WITHDRAWN
  - requireComment: true

### 8. Print (打印采购单)
- **Action Type**: EXPORT
- **Icon**: printer
- **Button Color**: default
- **用途**: 打印采购单为 PDF
- **配置**:
  - exportType: PDF

### 9. Export (导出到 Excel)
- **Action Type**: EXPORT
- **Icon**: download
- **Button Color**: default
- **用途**: 导出采购明细到 Excel
- **配置**:
  - exportType: EXCEL

---

## Action Types 说明

### PROCESS_SUBMIT
- 提交表单并启动工作流程
- 通常是主要操作按钮
- 会创建流程实例

### SAVE
- 保存数据但不启动流程
- 用于草稿保存
- 不会创建流程实例

### APPROVE
- 审批通过操作
- 需要审批意见
- 更新流程状态

### REJECT
- 审批拒绝操作
- 需要拒绝原因
- 终止流程或退回

### CANCEL
- 取消/撤回操作
- 终止当前流程
- 需要撤回原因

### EXPORT
- 导出数据操作
- 生成 PDF 或 Excel
- 不影响流程状态

### FORM_POPUP
- 打开弹窗表单
- 用于选择数据或查看详情
- 可以是只读或可编辑

### API_CALL
- 调用后端 API
- 用于计算或数据处理
- 可以自动触发

---

## 工作流程中的 Action 使用

### 申请阶段
```
用户填写表单
    ↓
[Save Draft] - 保存草稿（可选）
    ↓
[Submit] - 提交申请（启动流程）
```

### 部门经理审批阶段
```
部门经理收到任务
    ↓
查看申请详情
    ↓
[Department Approve] - 批准 → 转财务审批
或
[Department Reject] - 拒绝 → 流程结束
```

### 财务审批阶段
```
财务人员收到任务
    ↓
查看申请详情
    ↓
[Finance Approve] - 批准 → 流程完成
或
[Finance Reject] - 拒绝 → 流程结束
```

### 其他操作
```
[Withdraw] - 申请人可以在审批过程中撤回
[Print] - 审批完成后打印采购单
[Export] - 导出采购明细数据
```

---

## 扩展 Actions（可选）

如果需要更多功能，可以添加以下 Actions：

### 1. Select Supplier (选择供应商)
```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, 
    config_json, icon, button_color
)
SELECT 
    id, 'select_supplier', 'FORM_POPUP', 'Select Supplier',
    '{"formName":"supplier_selector_form","popupWidth":"900px"}'::jsonb,
    'team', 'info'
FROM dw_function_units WHERE code = 'PURCHASE';
```

### 2. View History (查看审批历史)
```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, 
    config_json, icon, button_color
)
SELECT 
    id, 'view_history', 'FORM_POPUP', 'View Approval History',
    '{"formName":"approval_action_form","readOnly":true}'::jsonb,
    'history', 'default'
FROM dw_function_units WHERE code = 'PURCHASE';
```

### 3. Calculate Total (计算总金额)
```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, 
    config_json, icon, button_color
)
SELECT 
    id, 'calculate_total', 'API_CALL', 'Calculate Total Amount',
    '{"url":"/api/purchase/calculate-total","method":"POST"}'::jsonb,
    'calculator', 'info'
FROM dw_function_units WHERE code = 'PURCHASE';
```

### 4. Request More Info (要求补充信息)
```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description, 
    config_json, icon, button_color
)
SELECT 
    id, 'request_info', 'FORM_POPUP', 'Request Additional Information',
    '{"requireComment":true,"targetStatus":"INFO_REQUIRED"}'::jsonb,
    'question-circle', 'warning'
FROM dw_function_units WHERE code = 'PURCHASE';
```

---

## 验证 Actions

查看所有已创建的 Actions：

```sql
SELECT 
    a.id,
    a.action_name,
    a.action_type,
    a.description,
    a.icon,
    a.button_color,
    a.is_default,
    a.config_json
FROM dw_action_definitions a
JOIN dw_function_units fu ON a.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE'
ORDER BY a.id;
```

---

## 总结

采购流程现在有 **9 个 Actions**：
1. ✅ Submit - 提交申请
2. ✅ Save Draft - 保存草稿
3. ✅ Department Approve - 部门批准
4. ✅ Department Reject - 部门拒绝
5. ✅ Finance Approve - 财务批准
6. ✅ Finance Reject - 财务拒绝
7. ✅ Withdraw - 撤回申请
8. ✅ Print - 打印采购单
9. ✅ Export - 导出数据

这些 Actions 覆盖了采购流程的完整生命周期！
