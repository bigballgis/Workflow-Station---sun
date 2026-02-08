# Table Design 在运行时的实际用途

## 核心发现

**Table Design 不仅仅是用来生成 Form！** 它在运行时有多个重要用途。

---

## 1. 设计时用途（Developer Workstation）

### 1.1 生成表单字段

**FormDesignComponent** 使用 Table Definition 来：

```java
// 绑定表单到表
if (request.getBoundTableId() != null) {
    TableDefinition boundTable = tableDefinitionRepository
        .findById(request.getBoundTableId())
        .orElseThrow(...);
    formDefinition.setBoundTable(boundTable);
}
```

### 1.2 验证表单字段

**验证表单字段是否存在于绑定的表中：**

```java
private void validateForeignKeyField(TableDefinition table, String foreignKeyField) {
    boolean fieldExists = table.getFieldDefinitions().stream()
        .anyMatch(field -> field.getFieldName().equals(foreignKeyField));
    
    if (!fieldExists) {
        throw new BusinessException("指定的外键字段在表中不存在: " + foreignKeyField);
    }
}
```

### 1.3 管理表关系

**通过 FormTableBinding 建立表之间的关系：**

```java
FormTableBinding binding = FormTableBinding.builder()
    .form(form)
    .table(table)  // ← 使用 TableDefinition
    .bindingType(BindingType.SUB)
    .foreignKeyField("request_id")  // ← 验证这个字段存在于 table 中
    .build();
```

---

## 2. 运行时用途（Workflow Engine & User Portal）

### 2.1 动态数据操作（最重要！）

**DataTableManagerComponent** 使用表名直接操作数据库：

```java
// 查询数据
public DataTableQueryResult queryTable(DataTableQueryRequest request) {
    String sql = "SELECT * FROM " + request.getTableName() + " WHERE ...";
    List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, params);
    return result;
}

// 插入数据
public DataTableOperationResult insertRecord(DataTableInsertRequest request) {
    String sql = "INSERT INTO " + request.getTableName() + " (...) VALUES (...)";
    jdbcTemplate.update(sql, params);
}

// 更新数据
public DataTableOperationResult updateRecord(DataTableUpdateRequest request) {
    String sql = "UPDATE " + request.getTableName() + " SET ... WHERE ...";
    jdbcTemplate.update(sql, params);
}
```

**关键点：**
- 系统使用 `table_name` 直接操作实际的数据库表
- 不需要为每个表创建 Entity 类
- 完全动态的 CRUD 操作

### 2.2 工作流变量管理

**VariableManagerComponent** 通过 DataTableManager 操作业务数据：

```java
public DataTableQueryResult queryDataTable(DataTableQueryRequest request) {
    log.info("通过变量管理器查询数据表: tableName={}", request.getTableName());
    return dataTableManagerComponent.queryTable(request);
}

public DataTableOperationResult insertDataTableRecord(DataTableInsertRequest request) {
    log.info("通过变量管理器插入数据表记录: tableName={}", request.getTableName());
    return dataTableManagerComponent.insertRecord(request);
}
```

**使用场景：**
- 工作流启动时插入业务数据
- 工作流执行中查询业务数据
- 工作流完成时更新业务数据

---

## 3. 完整的数据流

### 3.1 设计阶段

```
开发者在 Developer Workstation 中：
1. 创建 Table Definition
   ├── table_name: "purchase_request"
   ├── table_type: MAIN
   └── fields: [id, request_no, title, ...]

2. 创建 Form Definition
   ├── bound_table_id: → purchase_request
   └── config_json: {field: "request_no", ...}

3. 系统验证
   └── 检查 form 的 field 是否存在于 table 的 fields 中
```

### 3.2 部署阶段

```
系统根据 Table Definition 生成 DDL：
CREATE TABLE purchase_request (
    id BIGINT PRIMARY KEY,
    request_no VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    ...
);
```

### 3.3 运行阶段

```
用户在 User Portal 中：
1. 打开表单
   ├── 前端请求 Form Definition
   ├── 后端返回 config_json
   └── 前端渲染表单控件

2. 填写表单
   └── 用户输入数据

3. 提交表单
   ├── 前端发送数据到后端
   ├── 后端调用 DataTableManager.insertRecord()
   │   └── INSERT INTO purchase_request (request_no, title, ...) VALUES (?, ?, ...)
   └── 数据保存到实际数据库表

4. 启动工作流
   ├── 工作流引擎调用 VariableManager.queryDataTable()
   │   └── SELECT * FROM purchase_request WHERE id = ?
   ├── 获取业务数据
   └── 传递给工作流变量

5. 审批流程
   ├── 审批人查看数据
   │   └── SELECT * FROM purchase_request WHERE id = ?
   ├── 审批人提交意见
   │   └── INSERT INTO purchase_approvals (...)
   └── 更新主表状态
       └── UPDATE purchase_request SET status = 'APPROVED' WHERE id = ?
```

---

## 4. Table Design 的关键作用

### 4.1 元数据管理

**Table Definition 是系统的元数据：**

```
dw_table_definitions (元数据)
├── id: 101
├── table_name: "purchase_request"  ← 实际数据库表名
├── table_type: MAIN
└── fields: [...]

实际数据库表（运行时）
purchase_request
├── id: 1
├── request_no: "PR-2026-001"
├── title: "采购办公用品"
└── ...
```

### 4.2 动态 SQL 生成

**系统根据 table_name 动态生成 SQL：**

```java
// 不需要写死表名
String sql = "SELECT * FROM " + tableName + " WHERE id = ?";

// 而不是
String sql = "SELECT * FROM purchase_request WHERE id = ?";
```

**好处：**
- 一套代码支持所有表
- 新增表不需要修改代码
- 完全由配置驱动

### 4.3 字段验证

**在插入/更新数据时验证字段：**

```java
// 验证字段名是否合法（防止 SQL 注入）
private boolean isValidName(String name) {
    return name != null && name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
}

// 可以扩展为：检查字段是否存在于 Table Definition 中
private boolean isFieldExists(String tableName, String fieldName) {
    TableDefinition table = tableRepository.findByTableName(tableName);
    return table.getFieldDefinitions().stream()
        .anyMatch(f -> f.getFieldName().equals(fieldName));
}
```

### 4.4 类型转换和验证

**根据 Field Definition 的数据类型进行验证：**

```java
// 未来可以实现
private void validateFieldValue(FieldDefinition field, Object value) {
    switch (field.getDataType()) {
        case INTEGER:
            if (!(value instanceof Integer)) {
                throw new ValidationException("字段类型不匹配");
            }
            break;
        case VARCHAR:
            if (value instanceof String && 
                ((String) value).length() > field.getLength()) {
                throw new ValidationException("字符串长度超过限制");
            }
            break;
        // ...
    }
}
```

---

## 5. 为什么不能只用 Form？

### 场景 1：API 直接操作数据

```java
// 通过 API 直接插入数据（不通过表单）
POST /api/data-tables/purchase_request/records
{
    "request_no": "PR-2026-001",
    "title": "采购办公用品",
    "total_amount": 5000.00
}

// 系统需要知道：
// 1. purchase_request 表存在吗？ ← 查询 dw_table_definitions
// 2. 这些字段存在吗？ ← 查询 dw_field_definitions
// 3. 数据类型对吗？ ← 验证 data_type
```

### 场景 2：工作流自动操作数据

```java
// 工作流自动更新状态（不通过表单）
workflowEngine.updateBusinessData(
    "purchase_request",  // ← 表名
    Map.of("status", "APPROVED"),  // ← 字段和值
    Map.of("id", 123)  // ← 条件
);

// 系统需要：
// 1. 知道 purchase_request 表的结构
// 2. 验证 status 字段存在
// 3. 生成正确的 UPDATE SQL
```

### 场景 3：数据导入导出

```java
// 导出数据
GET /api/data-tables/purchase_request/export

// 系统需要：
// 1. 读取 Table Definition 获取所有字段
// 2. 生成 SELECT SQL
// 3. 导出为 CSV/Excel
```

### 场景 4：报表和统计

```java
// 生成报表
SELECT 
    department,
    COUNT(*) as request_count,
    SUM(total_amount) as total_amount
FROM purchase_request
WHERE status = 'APPROVED'
GROUP BY department

// 系统需要知道：
// 1. purchase_request 表的结构
// 2. 哪些字段可以用于分组
// 3. 哪些字段可以用于聚合
```

---

## 6. 实际运行示例

### 示例：用户提交采购申请

**步骤 1：用户填写表单**
```javascript
// 前端获取表单配置
GET /api/developer/forms/purchase_request_form

Response:
{
    "formName": "purchase_request_form",
    "boundTableId": 101,  // ← 绑定到 purchase_request 表
    "configJson": {
        "rule": [
            {"field": "request_no", "type": "input"},
            {"field": "title", "type": "input"},
            ...
        ]
    }
}
```

**步骤 2：用户提交数据**
```javascript
// 前端提交数据
POST /api/workflow/data-tables/purchase_request/records

Request:
{
    "request_no": "PR-2026-001",
    "title": "采购办公用品",
    "department": "IT",
    "total_amount": 5000.00
}
```

**步骤 3：后端处理**
```java
// DataTableManagerComponent.insertRecord()
String sql = "INSERT INTO purchase_request " +
             "(request_no, title, department, total_amount) " +
             "VALUES (?, ?, ?, ?)";
jdbcTemplate.update(sql, "PR-2026-001", "采购办公用品", "IT", 5000.00);
```

**步骤 4：启动工作流**
```java
// 工作流引擎查询业务数据
DataTableQueryRequest request = DataTableQueryRequest.builder()
    .tableName("purchase_request")  // ← 使用表名
    .whereConditions(Map.of("request_no", "PR-2026-001"))
    .build();

DataTableQueryResult result = dataTableManager.queryTable(request);
Map<String, Object> businessData = result.getData().get(0);

// 启动工作流，传入业务数据
processEngine.startProcess("purchase_approval_process", businessData);
```

---

## 7. 总结

### Table Design 的作用

| 用途 | 说明 | 使用场景 |
|------|------|----------|
| **元数据定义** | 定义数据库表结构 | 设计时 |
| **表单生成** | 为表单提供字段列表 | 设计时 |
| **字段验证** | 验证表单字段是否存在 | 设计时 |
| **动态 SQL** | 生成 CRUD SQL 语句 | 运行时 |
| **数据验证** | 验证数据类型和约束 | 运行时 |
| **工作流集成** | 工作流操作业务数据 | 运行时 |
| **API 操作** | 通过 API 直接操作数据 | 运行时 |
| **报表统计** | 生成报表和统计查询 | 运行时 |

### 关键点

1. **Table Design 是数据层的定义**
   - 定义数据如何存储
   - 定义数据的结构和约束
   - 是系统的元数据

2. **Form Design 是展示层的定义**
   - 定义数据如何显示
   - 定义用户如何交互
   - 是用户界面的配置

3. **运行时的核心作用**
   - 动态生成 SQL
   - 验证数据完整性
   - 支持工作流操作业务数据
   - 支持 API 直接操作数据

4. **不能只用 Form 的原因**
   - Form 只定义界面，不定义数据结构
   - 系统需要在非表单场景下操作数据
   - 需要元数据来生成动态 SQL
   - 需要验证数据类型和约束

---

## 8. 架构优势

### 传统方式 vs 低代码方式

**传统方式：**
```java
// 需要为每个表创建 Entity
@Entity
@Table(name = "purchase_request")
public class PurchaseRequest {
    @Id
    private Long id;
    private String requestNo;
    private String title;
    // ... 100 行代码
}

// 需要为每个表创建 Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {
    // ... 50 行代码
}

// 需要为每个表创建 Service
@Service
public class PurchaseRequestService {
    // ... 200 行代码
}
```

**低代码方式：**
```sql
-- 只需要配置元数据
INSERT INTO dw_table_definitions (table_name, table_type)
VALUES ('purchase_request', 'MAIN');

INSERT INTO dw_field_definitions (table_id, field_name, data_type)
VALUES (101, 'request_no', 'VARCHAR');
```

```java
// 一套代码支持所有表
dataTableManager.insertRecord("purchase_request", data);
dataTableManager.queryTable("purchase_request", conditions);
dataTableManager.updateRecord("purchase_request", data, conditions);
```

**优势：**
- 新增表不需要写代码
- 修改表结构只需要更新配置
- 一套 CRUD 代码支持所有表
- 完全由配置驱动

---

**结论：Table Design 是低代码平台的核心，它不仅用于生成表单，更重要的是在运行时提供元数据支持，实现动态的数据操作。**
