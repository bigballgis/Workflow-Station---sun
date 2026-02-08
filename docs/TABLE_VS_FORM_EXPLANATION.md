# Table Design vs Form Design - 为什么需要两层设计？

## 核心区别

### Table Design（数据层）
**作用：定义数据库结构**
- 数据如何存储
- 数据类型和约束
- 表之间的关系
- 数据完整性规则

### Form Design（展示层）
**作用：定义用户界面**
- 数据如何显示
- 用户如何输入
- 字段的交互行为
- 界面布局和样式

---

## 为什么不能只用 Form？

### 原因 1：一个 Table 可以对应多个 Form

**示例：采购申请表（purchase_request）**

```
purchase_request 表（数据层）
├── id
├── request_no
├── title
├── department
├── applicant
├── apply_date
├── total_amount
├── status
└── remarks

可以有多个表单：

1. 创建表单（Create Form）
   ├── title          ← 可编辑
   ├── department     ← 可编辑
   ├── applicant      ← 可编辑
   ├── apply_date     ← 可编辑
   └── remarks        ← 可编辑

2. 审批表单（Approval Form）
   ├── request_no     ← 只读
   ├── title          ← 只读
   ├── department     ← 只读
   ├── total_amount   ← 只读
   └── status         ← 只读

3. 查看表单（View Form）
   ├── 所有字段       ← 全部只读

4. 编辑表单（Edit Form）
   ├── title          ← 可编辑
   ├── remarks        ← 可编辑
   └── 其他字段       ← 只读
```

**如果只有 Form，你需要在每个表单中重复定义所有字段的数据类型！**

### 原因 2：Form 字段 ≠ Table 字段

**Form 可以有 Table 中不存在的字段：**

```sql
-- Table 字段
purchase_request
├── total_amount (DECIMAL)  ← 存储在数据库

-- Form 字段
purchase_request_form
├── total_amount (number, readonly)     ← 显示总金额
├── total_amount_cny (computed)         ← 显示人民币格式（¥1,234.56）
├── total_amount_usd (computed)         ← 显示美元格式（$123.45）
└── confirm_amount (checkbox)           ← 确认金额（不存储）
```

**Form 可以不显示 Table 的某些字段：**

```sql
-- Table 有这些字段
purchase_request
├── id
├── created_at
├── created_by
├── updated_at
├── updated_by
├── deleted_at
└── version

-- Form 不需要显示这些技术字段
purchase_request_form
└── （不包含上述字段）
```

### 原因 3：Form 可以组合多个 Table

**示例：采购申请详情表单**

```
purchase_request_detail_form (一个表单)
├── 来自 purchase_request 表
│   ├── request_no
│   ├── title
│   └── total_amount
│
├── 来自 purchase_items 表（子表）
│   ├── item_name
│   ├── quantity
│   └── unit_price
│
└── 来自 suppliers 表（关联表）
    ├── supplier_name
    └── contact_phone
```

**如果没有 Table Design，系统怎么知道这些字段来自哪个表？**

### 原因 4：数据验证和业务规则

**Table 层的验证（数据库级别）：**
```sql
-- 在 Table Design 中定义
total_amount DECIMAL(15,2) NOT NULL CHECK (total_amount >= 0)
email VARCHAR(100) UNIQUE
status VARCHAR(20) CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED'))
```

**Form 层的验证（界面级别）：**
```json
{
  "field": "total_amount",
  "type": "number",
  "required": true,
  "min": 0,
  "max": 1000000,
  "precision": 2,
  "errorMessage": "金额必须在 0 到 1,000,000 之间"
}
```

**两层验证的作用不同：**
- Table 验证：保证数据完整性（即使通过 API 直接插入也会验证）
- Form 验证：提供更好的用户体验（实时反馈）

---

## 实际场景对比

### 场景 1：修改字段显示方式

**需求：** 将"申请日期"从输入框改为日期选择器

**只需修改 Form：**
```json
// 修改前
{"field": "apply_date", "type": "input"}

// 修改后
{"field": "apply_date", "type": "date", "format": "YYYY-MM-DD"}
```

**Table 不需要改动！** 数据库仍然存储 DATE 类型。

### 场景 2：添加计算字段

**需求：** 显示"请假天数"（结束日期 - 开始日期）

**Table 层：**
```sql
-- 不需要存储 days 字段（可以实时计算）
leave_requests
├── start_date (DATE)
└── end_date (DATE)
```

**Form 层：**
```json
{
  "field": "days",
  "label": "请假天数",
  "type": "number",
  "readonly": true,
  "computed": "DATEDIFF(end_date, start_date) + 1"
}
```

**如果没有 Table Design，系统怎么知道 start_date 和 end_date 是什么类型？**

### 场景 3：国际化

**需求：** 支持中英文界面

**Table 层（不变）：**
```sql
status VARCHAR(20)  -- 存储 'DRAFT', 'PENDING', 'APPROVED'
```

**Form 层（中文）：**
```json
{
  "field": "status",
  "label": "状态",
  "options": [
    {"label": "草稿", "value": "DRAFT"},
    {"label": "待审批", "value": "PENDING"},
    {"label": "已批准", "value": "APPROVED"}
  ]
}
```

**Form 层（英文）：**
```json
{
  "field": "status",
  "label": "Status",
  "options": [
    {"label": "Draft", "value": "DRAFT"},
    {"label": "Pending", "value": "PENDING"},
    {"label": "Approved", "value": "APPROVED"}
  ]
}
```

**Table 只定义一次，Form 可以有多个语言版本！**

---

## 能否简化设计流程？

### 方案 1：自动生成 Form（推荐）

**在 Developer Workstation 中实现：**

```
1. 用户设计 Table
   ├── 定义字段：name, type, length, nullable
   └── 保存到 dw_table_definitions

2. 点击"生成默认表单"按钮
   ├── 系统自动读取 Table 字段
   ├── 根据字段类型生成表单控件
   │   ├── VARCHAR → input
   │   ├── TEXT → textarea
   │   ├── DATE → date
   │   ├── INTEGER → number
   │   └── BOOLEAN → checkbox
   └── 生成 Form 配置到 dw_form_definitions

3. 用户微调 Form
   ├── 调整字段顺序
   ├── 修改控件类型
   ├── 添加验证规则
   └── 设置只读/必填
```

**这样用户只需要：**
1. 详细设计 Table（一次）
2. 点击生成 Form（自动）
3. 微调 Form（可选）

### 方案 2：智能字段映射

**系统自动推断：**

```javascript
// 根据字段名称自动推断控件类型
const fieldTypeMapping = {
  // 字段名包含这些关键词 → 使用特定控件
  'email': 'email',
  'phone': 'tel',
  'password': 'password',
  'date': 'date',
  'time': 'time',
  'amount|price|cost': 'number',
  'status': 'select',
  'description|remarks|comment': 'textarea',
  'is_*|has_*': 'checkbox'
};

// 根据数据类型自动推断
const dataTypeMapping = {
  'VARCHAR': 'input',
  'TEXT': 'textarea',
  'INTEGER': 'number',
  'DECIMAL': 'number',
  'DATE': 'date',
  'TIMESTAMP': 'datetime',
  'BOOLEAN': 'checkbox'
};
```

### 方案 3：模板化设计

**提供常用模板：**

```
模板库
├── 审批流程模板
│   ├── Table: 主表 + 审批记录表
│   └── Form: 申请表单 + 审批表单
│
├── 主子表模板
│   ├── Table: 主表 + 子表
│   └── Form: 主表单（含子表格）
│
└── 基础 CRUD 模板
    ├── Table: 单表
    └── Form: 创建/编辑/查看表单
```

---

## 最佳实践建议

### 设计顺序

```
1. Table Design（数据层）
   ├── 定义所有字段
   ├── 设置数据类型和约束
   └── 建立表关系

2. 点击"生成默认表单"
   └── 系统自动生成基础 Form

3. Form Design（展示层）
   ├── 调整字段顺序
   ├── 修改控件类型
   ├── 添加计算字段
   ├── 设置显示/隐藏规则
   └── 配置验证规则

4. 创建多个 Form（如需要）
   ├── 创建表单
   ├── 编辑表单
   ├── 查看表单
   └── 审批表单
```

### 什么时候需要手动设计 Form？

**需要手动设计的情况：**
1. 表单需要组合多个表的字段
2. 需要添加计算字段或虚拟字段
3. 需要特殊的布局（如分组、标签页）
4. 需要动态显示/隐藏字段
5. 需要自定义验证规则

**可以使用自动生成的情况：**
1. 简单的 CRUD 表单
2. 字段与表完全对应
3. 标准的垂直布局
4. 基本的数据类型验证

---

## 总结

### Table Design 的价值

1. **数据完整性** - 保证数据库级别的约束
2. **可重用性** - 一个表可以被多个表单使用
3. **数据关系** - 定义表之间的外键关系
4. **性能优化** - 定义索引、分区等
5. **数据迁移** - 可以生成 DDL 脚本

### Form Design 的价值

1. **用户体验** - 友好的界面和交互
2. **灵活性** - 同一数据可以有不同展示方式
3. **业务逻辑** - 字段联动、动态验证
4. **国际化** - 多语言支持
5. **权限控制** - 不同角色看到不同字段

### 建议

**对于 Developer Workstation 产品改进：**

1. ✅ **保留 Table 和 Form 的分离设计**
2. ✅ **添加"从 Table 生成 Form"功能**
3. ✅ **提供常用模板**
4. ✅ **智能字段类型推断**
5. ✅ **可视化的字段映射工具**

这样既保持了架构的灵活性，又减少了用户的重复工作！

---

**结论：Table 和 Form 的分离是必要的，但可以通过工具自动化来减少重复工作。**
