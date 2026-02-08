# 数据存储架构 - 真相揭秘

## 重要发现

**Table Design 中定义的表（如 `purchase_request`）并不是真实的数据库表！**

数据实际上存储在：
1. **Flowable 流程变量表** - `act_ru_variable` (运行时)
2. **Flowable 历史变量表** - `act_hi_varinst` (历史)
3. **以 JSON 格式存储**

---

## 实际的数据存储方式

### 1. 元数据层（设计时）

```
dw_table_definitions (元数据 - 不是真实表)
├── id: 1
├── table_name: "purchase_request"  ← 这只是一个逻辑名称！
├── table_type: MAIN
└── ...

dw_field_definitions (字段元数据)
├── table_id: 1
├── field_name: "request_no"
├── data_type: "VARCHAR"
└── ...

dw_form_definitions (表单定义)
├── id: 1
├── form_name: "purchase_request_form"
├── bound_table_id: 1  ← 绑定到逻辑表
└── config_json: {...}
```

### 2. 数据层（运行时）

**数据实际存储在 Flowable 的变量表中：**

```sql
-- Flowable 流程变量表
act_ru_variable
├── id_: "var-123"
├── name_: "purchase_request"  ← 变量名（对应 table_name）
├── type_: "json"
├── text_: '{"request_no": "PR-2026-001", "title": "采购办公用品", ...}'  ← JSON 数据
├── proc_inst_id_: "proc-456"
└── ...
```

---

## 完整的数据流

### 场景：用户提交采购申请

#### 步骤 1：用户填写表单

```javascript
// 前端获取表单配置
GET /api/developer/forms/purchase_request_form

Response:
{
    "formName": "purchase_request_form",
    "boundTableId": 1,  // ← 绑定到逻辑表 purchase_request
    "configJson": {
        "rule": [
            {"field": "request_no", "type": "input"},
            {"field": "title", "type": "input"},
            {"field": "total_amount", "type": "number"}
        ]
    }
}
```

#### 步骤 2：用户提交数据

```javascript
// 前端提交数据
POST /api/workflow/processes/start

Request:
{
    "processKey": "purchase_approval_process",
    "variables": {
        "purchase_request": {  // ← 变量名 = table_name
            "request_no": "PR-2026-001",
            "title": "采购办公用品",
            "department": "IT",
            "total_amount": 5000.00
        }
    }
}
```

#### 步骤 3：后端存储数据

```java
// Flowable 引擎将数据存储为流程变量
runtimeService.startProcessInstanceByKey(
    "purchase_approval_process",
    variables  // ← Map<String, Object>
);

// Flowable 内部操作：
// INSERT INTO act_ru_variable (name_, type_, text_, proc_inst_id_)
// VALUES ('purchase_request', 'json', '{"request_no":"PR-2026-001",...}', 'proc-456')
```

#### 步骤 4：查询数据

```java
// 查询流程变量
Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
Map<String, Object> purchaseRequest = (Map) variables.get("purchase_request");

// Flowable 内部操作：
// SELECT name_, text_ FROM act_ru_variable WHERE proc_inst_id_ = 'proc-456'
// 然后将 JSON 字符串解析为 Map
```

---

## Table Design 的真实作用

### 1. 逻辑数据模型（不是物理表）

**Table Definition 定义的是逻辑数据结构，而不是物理数据库表！**

```
逻辑层（设计时）
├── Table: purchase_request
│   ├── field: request_no (VARCHAR)
│   ├── field: title (VARCHAR)
│   └── field: total_amount (DECIMAL)
│
物理层（运行时）
└── act_ru_variable
    ├── name_: "purchase_request"
    └── text_: '{"request_no":"PR-2026-001","title":"采购办公用品","total_amount":5000.00}'
```

### 2. 表单字段验证

**验证表单字段是否在逻辑表中定义：**

```java
// 检查表单字段是否存在于 Table Definition 中
boolean fieldExists = table.getFieldDefinitions().stream()
    .anyMatch(field -> field.getFieldName().equals("request_no"));
```

### 3. 数据类型验证

**根据 Field Definition 验证数据类型：**

```java
// 验证字段值的类型
FieldDefinition field = getFieldDefinition("purchase_request", "total_amount");
if (field.getDataType() == DataType.DECIMAL) {
    // 验证值是否为数字
    if (!(value instanceof Number)) {
        throw new ValidationException("total_amount 必须是数字");
    }
}
```

### 4. 表单生成

**根据 Table Definition 自动生成表单：**

```java
// 读取 Table Definition 的所有字段
List<FieldDefinition> fields = table.getFieldDefinitions();

// 为每个字段生成表单控件
for (FieldDefinition field : fields) {
    FormField formField = new FormField();
    formField.setFieldName(field.getFieldName());
    formField.setLabel(field.getDescription());
    formField.setType(mapDataTypeToControlType(field.getDataType()));
    formField.setRequired(!field.isNullable());
}
```

---

## DataTableManagerComponent 的真实作用

### 误解：直接操作物理表

我之前以为 `DataTableManagerComponent` 是这样工作的：

```java
// ❌ 错误理解
String sql = "INSERT INTO purchase_request (...) VALUES (...)";
jdbcTemplate.update(sql);
```

### 真相：操作流程变量

实际上应该是这样：

```java
// ✅ 正确理解
// DataTableManagerComponent 可能是用于：
// 1. 操作 Flowable 流程变量
// 2. 或者操作系统表（如 sys_users, sys_roles）
// 3. 但不是操作业务数据表（如 purchase_request）

// 业务数据存储为流程变量
runtimeService.setVariable(processInstanceId, "purchase_request", data);

// 查询业务数据
Object data = runtimeService.getVariable(processInstanceId, "purchase_request");
```

---

## 为什么使用这种架构？

### 优势

1. **灵活性**
   - 不需要为每个业务创建物理表
   - 数据结构可以动态变化
   - 支持任意 JSON 结构

2. **简化部署**
   - 不需要执行 DDL 脚本
   - 不需要数据库迁移
   - 新增业务只需要配置元数据

3. **流程集成**
   - 数据天然与流程绑定
   - 流程变量自动管理生命周期
   - 支持流程历史查询

4. **版本管理**
   - 流程变量支持版本控制
   - 可以查询历史数据
   - 支持数据回滚

### 劣势

1. **查询性能**
   - JSON 字段查询效率低
   - 不支持复杂的 SQL 查询
   - 不支持索引优化

2. **数据完整性**
   - 缺少数据库级别的约束
   - 需要应用层验证
   - 外键关系难以维护

3. **数据分析**
   - 难以进行数据统计
   - 难以生成报表
   - 需要额外的 ETL 处理

---

## 混合存储策略

### 方案 1：纯流程变量存储（当前方案）

```
所有业务数据 → Flowable 流程变量 (JSON)
```

**适用场景：**
- 流程驱动的业务
- 数据结构经常变化
- 不需要复杂查询

### 方案 2：混合存储

```
核心业务数据 → 物理表（如 sys_users, sys_roles）
流程相关数据 → Flowable 流程变量 (JSON)
```

**适用场景：**
- 需要复杂查询的数据用物理表
- 流程临时数据用流程变量
- 平衡性能和灵活性

### 方案 3：物理表 + 流程变量引用

```
业务数据 → 物理表 purchase_request
流程变量 → 只存储 ID 引用
```

**示例：**

```sql
-- 物理表存储业务数据
CREATE TABLE purchase_request (
    id BIGINT PRIMARY KEY,
    request_no VARCHAR(50),
    title VARCHAR(200),
    total_amount DECIMAL(15,2)
);

-- 流程变量只存储 ID
act_ru_variable
├── name_: "purchase_request_id"
├── type_: "long"
└── long_: 123  ← 引用 purchase_request.id
```

**优势：**
- 支持复杂查询
- 数据完整性约束
- 流程与数据解耦

---

## 当前系统的实际架构

### 系统表（物理表）

```
sys_users          ← 真实的物理表
sys_roles          ← 真实的物理表
sys_permissions    ← 真实的物理表
dw_function_units  ← 真实的物理表
dw_table_definitions  ← 真实的物理表（元数据）
dw_form_definitions   ← 真实的物理表（元数据）
```

### 业务数据（流程变量）

```
purchase_request   ← 逻辑表（元数据），数据存储在 act_ru_variable
purchase_items     ← 逻辑表（元数据），数据存储在 act_ru_variable
suppliers          ← 逻辑表（元数据），数据存储在 act_ru_variable
```

### 数据存储位置

```
act_ru_variable (Flowable 流程变量表)
├── name_: "purchase_request"
│   └── text_: '{"request_no":"PR-2026-001",...}'
│
├── name_: "purchase_items"
│   └── text_: '[{"item_name":"笔记本",...}, {...}]'
│
└── name_: "approver_comments"
    └── text_: '{"result":"APPROVED","comments":"同意"}'
```

---

## 总结

### Table Design 的真实作用

1. **逻辑数据模型** - 定义数据结构，但不创建物理表
2. **表单生成** - 为表单提供字段列表
3. **数据验证** - 验证字段类型和约束
4. **文档说明** - 描述业务数据结构

### 数据实际存储位置

1. **系统数据** → 物理表（sys_*, dw_*, admin_*）
2. **业务数据** → Flowable 流程变量（JSON 格式）
3. **流程数据** → Flowable 流程表（act_*）

### 关键理解

**Table Design 不是用来创建物理表的！**
- 它是逻辑数据模型
- 数据存储在 Flowable 流程变量中
- 以 JSON 格式存储
- 与流程实例绑定

---

## 验证方法

### 查看实际数据

```sql
-- 查看流程变量
SELECT 
    name_,
    type_,
    text_,
    proc_inst_id_
FROM act_ru_variable
WHERE name_ LIKE '%purchase%';

-- 查看历史变量
SELECT 
    name_,
    var_type_,
    text_,
    proc_inst_id_
FROM act_hi_varinst
WHERE name_ LIKE '%purchase%';
```

### 查看元数据

```sql
-- 查看逻辑表定义
SELECT 
    table_name,
    table_type,
    description
FROM dw_table_definitions;

-- 查看字段定义
SELECT 
    t.table_name,
    f.field_name,
    f.data_type,
    f.length
FROM dw_field_definitions f
JOIN dw_table_definitions t ON f.table_id = t.id;
```

---

**结论：Table Design 定义的是逻辑数据模型，实际数据以 JSON 格式存储在 Flowable 流程变量表中！**
