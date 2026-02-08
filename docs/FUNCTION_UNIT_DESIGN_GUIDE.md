# Function Unit 设计完整指南

## 目录
1. [概述](#概述)
2. [核心概念](#核心概念)
3. [设计步骤](#设计步骤)
4. [采购系统案例分析](#采购系统案例分析)
5. [字段对应关系](#字段对应关系)
6. [页面设计流程](#页面设计流程)

---

## 概述

Function Unit（功能单元）是 Developer Workstation 中的顶层组织单位，用于封装一个完整的业务功能模块。

### 核心组成部分
1. **Function Unit** - 功能单元定义
2. **Table Definitions** - 数据表定义
3. **Field Definitions** - 字段定义
4. **Form Definitions** - 表单定义
5. **Form-Table Bindings** - 表单与表的绑定关系
6. **Action Definitions** - 操作按钮定义
7. **Process Definitions** - 工作流程定义（BPMN）

---

## 核心概念

### 1. 数据层（Table & Field）

**Table Types（表类型）：**
- `MAIN` - 主表：业务主数据表
- `SUB` - 子表：明细数据表（一对多关系）
- `RELATION` - 关联表：基础数据表（如供应商、客户）
- `ACTION` - 操作表：记录操作历史（如审批记录）

**Field Types（字段类型）：**
- `BIGINT`, `INTEGER` - 整数
- `VARCHAR(n)` - 字符串
- `TEXT` - 长文本
- `DECIMAL(p,s)` - 小数
- `DATE`, `TIMESTAMP` - 日期时间
- `BOOLEAN` - 布尔值

### 2. 表单层（Form）

**Form Types（表单类型）：**
- `MAIN` - 主表单：显示主表数据
- `SUB` - 子表单：显示子表数据（通常是表格形式）
- `ACTION` - 操作表单：执行操作时的输入表单
- `POPUP` - 弹窗表单：选择器、查询弹窗

**Form 与 Table 的绑定：**
- `bound_table_id` - 表单直接绑定的表（在 dw_form_definitions 中）
- `dw_form_table_bindings` - 表单与多个表的绑定关系表

### 3. 绑定关系（Form-Table Binding）

**Binding Types（绑定类型）：**

- `PRIMARY` - 主绑定：表单的主要数据来源
- `SUB` - 子绑定：子表数据（一对多）
- `RELATED` - 关联绑定：关联表数据（外键关系）

**Binding Modes（绑定模式）：**
- `EDITABLE` - 可编辑
- `READONLY` - 只读

---

## 设计步骤

### 步骤 1：创建 Function Unit

```sql
INSERT INTO dw_function_units (code, name, description, status)
VALUES ('PURCHASE', '采购管理', '采购申请、审批和管理流程', 'DRAFT');
```

### 步骤 2：定义数据表

```sql
-- 主表
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'purchase_request', 'MAIN', '采购申请主表'
FROM dw_function_units WHERE code = 'PURCHASE';

-- 子表
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'purchase_items', 'SUB', '采购明细表'
FROM dw_function_units WHERE code = 'PURCHASE';
```

### 步骤 3：定义字段

```sql
-- 主表字段
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description)
SELECT id, 'request_no', 'VARCHAR', 50, false, '申请编号'
FROM dw_table_definitions WHERE table_name = 'purchase_request';
```

### 步骤 4：创建表单


```sql
INSERT INTO dw_form_definitions (
    function_unit_id,
    form_name,
    form_type,
    bound_table_id,
    config_json
)
SELECT 
    fu.id,
    'purchase_request_form',
    'MAIN',
    td.id,  -- 绑定到 purchase_request 表
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'request_no',  -- 对应 field_definitions 中的 field_name
                'label', '申请编号',
                'type', 'input',
                'required', true
            )
        )
    )
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_request';
```

### 步骤 5：创建表单-表绑定（可选）

如果表单需要访问多个表的数据：

```sql
INSERT INTO dw_form_table_bindings (
    form_id,
    table_id,
    binding_type,
    binding_mode,
    foreign_key_field
)
SELECT 
    f.id,
    t.id,
    'SUB',
    'EDITABLE',
    'request_id'  -- 子表中的外键字段
FROM dw_form_definitions f
JOIN dw_table_definitions t ON t.function_unit_id = f.function_unit_id
WHERE f.form_name = 'purchase_request_form' 
  AND t.table_name = 'purchase_items';
```

---

## 采购系统案例分析


### 数据结构

```
采购系统 (PURCHASE)
├── purchase_request (MAIN)          - 采购申请主表
│   ├── id (BIGINT, PK)
│   ├── request_no (VARCHAR)         - 申请编号
│   ├── title (VARCHAR)              - 标题
│   ├── department (VARCHAR)         - 部门
│   ├── applicant (VARCHAR)          - 申请人
│   ├── apply_date (DATE)            - 申请日期
│   ├── total_amount (DECIMAL)       - 总金额
│   └── status (VARCHAR)             - 状态
│
├── purchase_items (SUB)             - 采购明细子表
│   ├── id (BIGINT, PK)
│   ├── request_id (BIGINT, FK)      - 外键 -> purchase_request.id
│   ├── item_name (VARCHAR)          - 物品名称
│   ├── quantity (INTEGER)           - 数量
│   ├── unit_price (DECIMAL)         - 单价
│   └── subtotal (DECIMAL)           - 小计
│
├── suppliers (RELATION)             - 供应商表
│   ├── id (BIGINT, PK)
│   ├── supplier_code (VARCHAR)
│   └── supplier_name (VARCHAR)
│
└── purchase_approvals (ACTION)      - 审批记录表
    ├── id (BIGINT, PK)
    ├── request_id (BIGINT, FK)
    ├── approver (VARCHAR)
    └── result (VARCHAR)
```

### 表单设计

```
表单层
├── purchase_request_form (MAIN)
│   ├── bound_table_id -> purchase_request
│   └── config_json.rule[] - 表单字段配置
│       ├── field: 'request_no'  -> purchase_request.request_no
│       ├── field: 'title'       -> purchase_request.title
│       └── ...
│
├── purchase_items_form (SUB)
│   ├── bound_table_id -> purchase_items
│   └── config_json.columns[] - 表格列配置
│       ├── field: 'item_name'   -> purchase_items.item_name
│       ├── field: 'quantity'    -> purchase_items.quantity
│       └── ...
│
└── approval_action_form (ACTION)
    ├── bound_table_id -> purchase_approvals
    └── config_json.fields[] - 操作表单字段
```

---

## 字段对应关系详解


### 1. Table -> Field 对应

**dw_table_definitions** (表定义)
```
id: 101
table_name: 'purchase_request'
```

**dw_field_definitions** (字段定义)
```
table_id: 101  ← 关联到表
field_name: 'request_no'
data_type: 'VARCHAR'
length: 50
```

### 2. Form -> Table 对应

**方式 A：直接绑定（bound_table_id）**

**dw_form_definitions**
```
id: 201
form_name: 'purchase_request_form'
bound_table_id: 101  ← 直接绑定到 purchase_request 表
```

**方式 B：通过绑定表（dw_form_table_bindings）**

**dw_form_table_bindings**
```
form_id: 201  ← 表单 ID
table_id: 101  ← 表 ID
binding_type: 'PRIMARY'
binding_mode: 'EDITABLE'
```

### 3. Form Field -> Table Field 对应

**dw_form_definitions.config_json**
```json
{
  "rule": [
    {
      "field": "request_no",  ← 这个名称必须匹配 dw_field_definitions.field_name
      "label": "申请编号",
      "type": "input"
    }
  ]
}
```

**对应关系：**
```
Form Field (config_json.rule[].field)
    ↓
Table Field (dw_field_definitions.field_name)
    ↓
Database Column (实际数据库表的列名)
```

---

## 页面设计流程

### 在 Developer Workstation 中设计

#### 1. 创建 Function Unit


**页面路径：** Developer Workstation → Function Units → Create New

**填写内容：**
- Code: `PURCHASE` (唯一标识)
- Name: `采购管理`
- Description: `采购申请、审批和管理流程`
- Status: `DRAFT`

#### 2. 设计数据表（Table Design）

**页面路径：** Function Unit Details → Tables Tab → Add Table

**主表设计：**
```
Table Name: purchase_request
Table Type: MAIN
Description: 采购申请主表
```

**添加字段：**
| Field Name | Data Type | Length | Nullable | Description |
|------------|-----------|--------|----------|-------------|
| id | BIGINT | - | No | 主键 |
| request_no | VARCHAR | 50 | No | 申请编号 |
| title | VARCHAR | 200 | No | 标题 |
| department | VARCHAR | 100 | No | 部门 |
| total_amount | DECIMAL | 15,2 | No | 总金额 |
| status | VARCHAR | 20 | No | 状态 |

**子表设计：**
```
Table Name: purchase_items
Table Type: SUB
Description: 采购明细表
```

**添加字段：**
| Field Name | Data Type | Length | Nullable | Description |
|------------|-----------|--------|----------|-------------|
| id | BIGINT | - | No | 主键 |
| request_id | BIGINT | - | No | 外键 |
| item_name | VARCHAR | 200 | No | 物品名称 |
| quantity | INTEGER | - | No | 数量 |
| unit_price | DECIMAL | 15,2 | No | 单价 |
| subtotal | DECIMAL | 15,2 | No | 小计 |

#### 3. 设计表单（Form Design）

**页面路径：** Function Unit Details → Forms Tab → Add Form

**主表单设计：**
```
Form Name: purchase_request_form
Form Type: MAIN
Bound Table: purchase_request  ← 选择绑定的表
```

**配置表单字段（Form Fields）：**


在表单设计器中，添加字段：

| Field | Label | Type | Required | Options |
|-------|-------|------|----------|---------|
| request_no | 申请编号 | input | Yes | readonly: true |
| title | 申请标题 | input | Yes | - |
| department | 申请部门 | select | Yes | IT/HR/FINANCE/ADMIN |
| applicant | 申请人 | input | Yes | - |
| apply_date | 申请日期 | date | Yes | defaultValue: today |
| total_amount | 总金额 | number | Yes | readonly: true, precision: 2 |
| status | 状态 | select | Yes | DRAFT/PENDING/APPROVED/REJECTED |
| remarks | 备注 | textarea | No | rows: 4 |

**关键点：**
- `Field` 列的值必须与 Table Design 中的 `field_name` 完全一致
- 系统会自动根据 `bound_table_id` 加载可用字段列表

**子表单设计：**
```
Form Name: purchase_items_form
Form Type: SUB
Bound Table: purchase_items
Layout: table  ← 子表单通常使用表格布局
```

**配置表格列（Columns）：**

| Field | Label | Type | Width | Options |
|-------|-------|------|-------|---------|
| item_name | 物品名称 | input | 200px | required: true |
| specification | 规格型号 | input | 150px | - |
| quantity | 数量 | number | 100px | required: true, min: 1 |
| unit | 单位 | select | 80px | 个/台/套/箱/件 |
| unit_price | 单价 | number | 120px | precision: 2 |
| subtotal | 小计 | number | 120px | readonly: true, computed: 'quantity * unit_price' |

#### 4. 绑定表单与表（Form-Table Binding）

**页面路径：** Form Details → Bindings Tab → Add Binding

**主表单绑定主表：**
```
Form: purchase_request_form
Table: purchase_request
Binding Type: PRIMARY
Binding Mode: EDITABLE
```

**主表单绑定子表：**
```
Form: purchase_request_form
Table: purchase_items
Binding Type: SUB
Binding Mode: EDITABLE
Foreign Key Field: request_id  ← 子表中指向主表的外键
```

这样，主表单就可以同时显示主表数据和子表数据了。

#### 5. 设计操作按钮（Action Design）



**页面路径：** Function Unit Details → Actions Tab → Add Action

**提交审批按钮：**
```
Action Name: submit_for_approval
Action Type: WORKFLOW
Label: 提交审批
Icon: send
Button Color: primary
```

**配置：**
```json
{
  "triggerWorkflow": true,
  "workflowKey": "purchase_approval_process",
  "confirmMessage": "确认提交审批吗？",
  "successMessage": "提交成功",
  "updateFields": {
    "status": "PENDING"
  }
}
```

**审批按钮：**
```
Action Name: approve
Action Type: APPROVAL
Label: 审批
Icon: check
Button Color: success
Form: approval_action_form  ← 绑定审批表单
```

#### 6. 设计工作流程（Process Design）

**页面路径：** Function Unit Details → Process Tab → BPMN Designer

使用 BPMN 设计器绘制流程：

```
[开始] → [部门经理审批] → [财务审批] → [结束]
           ↓ 拒绝                ↓ 拒绝
        [拒绝结束]            [拒绝结束]
```

**配置用户任务：**
- Task ID: `deptManagerApproval`
- Task Name: `部门经理审批`
- Candidate Groups: `MANAGERS`
- Form Key: `approval_action_form`

---

## 完整的数据流

### 1. 用户填写表单

```
用户在前端填写表单
    ↓
前端读取 dw_form_definitions.config_json
    ↓
根据 config_json.rule[] 渲染表单控件
    ↓
用户输入数据
```

### 2. 提交数据

```
前端收集表单数据
    ↓
根据 bound_table_id 确定目标表
    ↓
根据 field 名称映射到 dw_field_definitions
    ↓
验证数据类型和约束
    ↓
保存到实际数据库表
```

### 3. 显示数据

```
前端请求数据
    ↓
后端根据 bound_table_id 查询表
    ↓
根据 dw_form_table_bindings 加载关联数据
    ↓
返回数据到前端
    ↓
前端根据 config_json 渲染表单
```

---

## 关键要点总结



### 1. 字段名称必须一致

```
dw_field_definitions.field_name = 'request_no'
                ↕
dw_form_definitions.config_json.rule[].field = 'request_no'
```

### 2. 表单通过 bound_table_id 绑定表

```
dw_form_definitions.bound_table_id → dw_table_definitions.id
```

### 3. 复杂关系使用 dw_form_table_bindings

当一个表单需要访问多个表时：
```
主表单 (purchase_request_form)
  ├── PRIMARY binding → purchase_request (主表)
  └── SUB binding → purchase_items (子表)
```

### 4. 表单类型决定渲染方式

- `MAIN` → 垂直表单布局
- `SUB` → 表格布局（可编辑行）
- `ACTION` → 弹窗表单
- `POPUP` → 选择器弹窗

### 5. Action 可以绑定表单

```
dw_action_definitions.config_json = {
  "formId": 201,  ← 点击按钮时打开的表单
  "triggerWorkflow": true
}
```

---

## 实际操作示例

### 场景：创建一个简单的请假申请系统

#### 步骤 1：创建 Function Unit
```sql
INSERT INTO dw_function_units (code, name, description, status)
VALUES ('LEAVE', '请假管理', '员工请假申请和审批', 'DRAFT');
```

#### 步骤 2：创建主表
```sql
INSERT INTO dw_table_definitions (function_unit_id, table_name, table_type, description)
SELECT id, 'leave_requests', 'MAIN', '请假申请表'
FROM dw_function_units WHERE code = 'LEAVE';
```

#### 步骤 3：添加字段
```sql
-- 申请编号
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description)
SELECT id, 'request_no', 'VARCHAR', 50, false, '申请编号'
FROM dw_table_definitions WHERE table_name = 'leave_requests';

-- 申请人
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description)
SELECT id, 'applicant', 'VARCHAR', 100, false, '申请人'
FROM dw_table_definitions WHERE table_name = 'leave_requests';

-- 请假类型
INSERT INTO dw_field_definitions (table_id, field_name, data_type, length, nullable, description)
SELECT id, 'leave_type', 'VARCHAR', 20, false, '请假类型'
FROM dw_table_definitions WHERE table_name = 'leave_requests';

-- 开始日期
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description)
SELECT id, 'start_date', 'DATE', false, '开始日期'
FROM dw_table_definitions WHERE table_name = 'leave_requests';

-- 结束日期
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description)
SELECT id, 'end_date', 'DATE', false, '结束日期'
FROM dw_table_definitions WHERE table_name = 'leave_requests';

-- 请假天数
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description)
SELECT id, 'days', 'INTEGER', false, '请假天数'
FROM dw_table_definitions WHERE table_name = 'leave_requests';

-- 请假原因
INSERT INTO dw_field_definitions (table_id, field_name, data_type, nullable, description)
SELECT id, 'reason', 'TEXT', true, '请假原因'
FROM dw_table_definitions WHERE table_name = 'leave_requests';
```

#### 步骤 4：创建表单

```sql
INSERT INTO dw_form_definitions (
    function_unit_id,
    form_name,
    form_type,
    bound_table_id,
    config_json
)
SELECT 
    fu.id,
    'leave_request_form',
    'MAIN',
    td.id,
    jsonb_build_object(
        'layout', 'vertical',
        'labelWidth', '120px',
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'request_no',
                'label', '申请编号',
                'type', 'input',
                'required', true,
                'readonly', true,
                'placeholder', '系统自动生成'
            ),
            jsonb_build_object(
                'field', 'applicant',
                'label', '申请人',
                'type', 'input',
                'required', true,
                'defaultValue', '${currentUser}'
            ),
            jsonb_build_object(
                'field', 'leave_type',
                'label', '请假类型',
                'type', 'select',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', '年假', 'value', 'ANNUAL'),
                    jsonb_build_object('label', '病假', 'value', 'SICK'),
                    jsonb_build_object('label', '事假', 'value', 'PERSONAL'),
                    jsonb_build_object('label', '调休', 'value', 'COMPENSATORY')
                )
            ),
            jsonb_build_object(
                'field', 'start_date',
                'label', '开始日期',
                'type', 'date',
                'required', true
            ),
            jsonb_build_object(
                'field', 'end_date',
                'label', '结束日期',
                'type', 'date',
                'required', true
            ),
            jsonb_build_object(
                'field', 'days',
                'label', '请假天数',
                'type', 'number',
                'required', true,
                'readonly', true,
                'computed', 'DATEDIFF(end_date, start_date) + 1'
            ),
            jsonb_build_object(
                'field', 'reason',
                'label', '请假原因',
                'type', 'textarea',
                'rows', 4,
                'placeholder', '请输入请假原因'
            )
        )
    )
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'LEAVE' AND td.table_name = 'leave_requests';
```

---

## 常见问题

### Q1: 为什么我的表单字段没有显示？

**A:** 检查以下几点：
1. `config_json.rule[].field` 是否与 `dw_field_definitions.field_name` 完全一致
2. `bound_table_id` 是否正确指向了包含该字段的表
3. 表单类型是否正确（MAIN 表单用于主表）

### Q2: 如何在主表单中显示子表数据？

**A:** 有两种方式：
1. 在主表单的 `config_json` 中添加子表单组件
2. 使用 `dw_form_table_bindings` 建立 SUB 类型的绑定关系

### Q3: Action Design 为什么没有值？

**A:** Action 是独立的操作定义，不直接关联到表单字段。Action 可以：
- 触发工作流
- 打开表单（通过 config_json.formId）
- 执行自定义脚本
- 更新字段值

### Q4: 如何实现字段联动（如：数量 × 单价 = 小计）？

**A:** 在表单配置中使用 `computed` 属性：
```json
{
  "field": "subtotal",
  "type": "number",
  "readonly": true,
  "computed": "quantity * unit_price"
}
```

### Q5: 如何实现下拉选项从数据库动态加载？

**A:** 使用 `dataSource` 配置：
```json
{
  "field": "supplier_id",
  "type": "select",
  "dataSource": {
    "type": "table",
    "table": "suppliers",
    "valueField": "id",
    "labelField": "supplier_name",
    "filter": {
      "status": "ACTIVE"
    }
  }
}
```

---

## 最佳实践

### 1. 命名规范
- Function Unit Code: 大写字母，下划线分隔（如：`PURCHASE_ORDER`）
- Table Name: 小写字母，下划线分隔（如：`purchase_orders`）
- Field Name: 小写字母，下划线分隔（如：`order_date`）
- Form Name: 小写字母，下划线分隔，加 `_form` 后缀（如：`purchase_order_form`）

### 2. 表设计
- 主表必须有主键（通常是 `id BIGINT`）
- 子表必须有外键指向主表
- 添加审计字段：`created_at`, `updated_at`, `created_by`
- 状态字段使用枚举值（如：`DRAFT`, `PENDING`, `APPROVED`）

### 3. 表单设计
- 主表单使用 `vertical` 布局
- 子表单使用 `table` 布局
- 只读字段设置 `readonly: true`
- 必填字段设置 `required: true`
- 提供合理的默认值和占位符

### 4. 工作流设计
- 使用清晰的任务名称
- 配置候选组或候选人
- 添加网关判断条件
- 记录审批历史

---

## 参考资料

- 采购系统完整示例：`deploy/init-scripts/04-purchase-workflow/`
- 请假管理示例：`deploy/init-scripts/05-demo-leave-management/`
- 数字借贷示例：`deploy/init-scripts/06-digital-lending/`
- 数据库 Schema：`deploy/init-scripts/00-schema/04-developer-workstation-schema.sql`

---

**文档版本：** 1.0  
**最后更新：** 2026-02-06  
**作者：** Kiro AI Assistant


---

## 附录：减少重复工作的方法

### 问题：Table 和 Form 字段重复定义

你可能会觉得在 Table Design 和 Form Design 中定义相同的字段是重复工作。

### 解决方案

#### 方案 1：使用"从表生成表单"功能

**在 Developer Workstation 中：**

1. 完成 Table Design
2. 点击"生成默认表单"按钮
3. 系统自动创建 Form，字段自动映射
4. 根据需要微调 Form

**自动映射规则：**
```
VARCHAR(n)  → input (text)
TEXT        → textarea
INTEGER     → number
DECIMAL     → number (with precision)
DATE        → date
TIMESTAMP   → datetime
BOOLEAN     → checkbox
```

#### 方案 2：使用 SQL 脚本批量生成

**示例脚本：**

```sql
-- 从 Table 字段自动生成 Form 配置
WITH table_fields AS (
    SELECT 
        fd.field_name,
        fd.data_type,
        fd.length,
        fd.nullable,
        CASE 
            WHEN fd.data_type = 'VARCHAR' THEN 'input'
            WHEN fd.data_type = 'TEXT' THEN 'textarea'
            WHEN fd.data_type IN ('INTEGER', 'BIGINT') THEN 'number'
            WHEN fd.data_type = 'DECIMAL' THEN 'number'
            WHEN fd.data_type = 'DATE' THEN 'date'
            WHEN fd.data_type = 'TIMESTAMP' THEN 'datetime'
            WHEN fd.data_type = 'BOOLEAN' THEN 'checkbox'
            ELSE 'input'
        END as form_type
    FROM dw_field_definitions fd
    JOIN dw_table_definitions td ON fd.table_id = td.id
    WHERE td.table_name = 'your_table_name'
    ORDER BY fd.sort_order
)
SELECT jsonb_agg(
    jsonb_build_object(
        'field', field_name,
        'label', field_name,  -- 可以改为更友好的标签
        'type', form_type,
        'required', NOT nullable
    )
) as form_config
FROM table_fields;
```

#### 方案 3：前端智能表单生成器

**在表单设计器中：**

1. 选择"绑定表"（Bound Table）
2. 系统自动加载该表的所有字段
3. 拖拽字段到表单设计区
4. 系统自动设置控件类型
5. 用户只需调整布局和样式

### 为什么仍然需要分开设计？

详细说明请参考：`docs/TABLE_VS_FORM_EXPLANATION.md`

**简要原因：**
1. 一个 Table 可以对应多个 Form（创建、编辑、查看、审批）
2. Form 可以有 Table 中不存在的字段（计算字段、UI 字段）
3. Form 可以组合多个 Table 的字段
4. Form 可以隐藏某些 Table 字段（如技术字段）
5. 支持国际化（同一 Table，不同语言的 Form）

### 实际工作流程

**推荐的设计流程：**

```
1. 设计 Table（10 分钟）
   └── 定义所有字段和约束

2. 生成默认 Form（1 秒）
   └── 点击按钮自动生成

3. 微调 Form（2 分钟）
   └── 调整布局、添加验证规则

总计：约 12 分钟
```

**vs 传统方式：**

```
1. 设计 Table（10 分钟）
2. 手动创建 Form（10 分钟）
3. 手动配置每个字段（10 分钟）

总计：约 30 分钟
```

**节省时间：60%**

---

**相关文档：**
- [Table vs Form 详细说明](./TABLE_VS_FORM_EXPLANATION.md)
