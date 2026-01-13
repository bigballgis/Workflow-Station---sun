# 功能单元SQL生成指南

## 概述
本指南用于在developer-workstation模块中生成功能单元的测试数据SQL。

## 关键规则

### 1. BPMN流程图必须包含图形信息
- BPMN XML必须包含`bpmndi:BPMNDiagram`元素，否则前端无法渲染流程图
- 必须包含的命名空间：
  - `xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"`
  - `xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"`
  - `xmlns:di="http://www.omg.org/spec/DD/20100524/DI"`
- 每个流程元素都需要对应的`bpmndi:BPMNShape`（节点）或`bpmndi:BPMNEdge`（连线）

### 2. BPMN XML必须Base64编码存储
- 代码使用`XmlEncodingUtil.encode()`进行编码
- 读取时使用`XmlEncodingUtil.smartDecode()`自动解码
- 这是为了避免数据库存储时的特殊字符转义问题

### 3. SQL文件分模块组织
由于Base64字符串很长，建议分模块生成SQL文件：
```
03-01-function-unit.sql  # 功能单元基本信息
03-02-tables.sql         # 表定义
03-03-fields-main.sql    # 主表字段
03-04-fields-sub.sql     # 子表/关联表/动作表字段
03-05-foreign-keys.sql   # 外键关系（dw_foreign_keys表）
03-06-forms.sql          # 表单定义
03-07-actions.sql        # 动作定义
03-08-process.sql        # 流程定义
03-09-form-bindings.sql  # 表单-表绑定关系（重要！）
```

### 4. 使用PowerShell生成长Base64字符串（重要！）

由于AI工具对单行长字符串有限制，**必须**使用PowerShell生成包含Base64的SQL文件。

**关键注意事项：**

1. **BPMN XML中避免使用中文** - 在BPMN XML中使用英文，避免编码问题
2. **使用 `[System.IO.File]::WriteAllText()` 写入文件** - 确保UTF-8编码正确
3. **先查询实际表结构** - 数据库表结构可能与预期不同，先用 `\d table_name` 查询

**正确的PowerShell脚本模板：**
```powershell
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
    id="Definitions_xxx" 
    targetNamespace="http://workflow.example.com/xxx">
  <bpmn:process id="Process_xxx" name="Process Name" isExecutable="true">
    <!-- 流程内容 -->
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <!-- 图形信息 -->
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
'@

# 转换为Base64
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($bpmnXml))

# 生成SQL - 注意：只包含实际存在的列！
$sql = @"
INSERT INTO dw_process_definitions (function_unit_id, bpmn_xml)
SELECT f.id, '$base64'
FROM dw_function_units f WHERE f.code = 'fu-xxx';
"@

# 使用 WriteAllText 确保UTF-8编码正确（不要用 Out-File）
[System.IO.File]::WriteAllText("04-08-process.sql", $sql, [System.Text.Encoding]::UTF8)

Write-Host "SQL file generated"
Write-Host "Base64 length: $($base64.Length)"
```

**执行PowerShell脚本：**
```bash
powershell -ExecutionPolicy Bypass -File generate-process.ps1
```

**执行生成的SQL：**
```bash
Get-Content -Path "04-08-process.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
```

### 5. 图标引用
- 图标存储在`dw_icons`表中
- 引用方式：`(SELECT id FROM dw_icons WHERE name = 'icon-name')`
- 可用图标查看：`deploy/init-scripts/03-test-workflow/02-sample-icons.sql`

### 6. 表类型枚举
- `MAIN` - 主表
- `SUB` - 子表
- `RELATION` - 关联表
- `ACTION` - 动作表

### 7. 表单类型枚举
- `MAIN` - 主表单
- `SUB` - 子表单
- `POPUP` - 弹出表单
- `ACTION` - 动作表单

### 8. 动作类型枚举

#### 审批操作
- `APPROVE` - 批准
- `REJECT` - 拒绝
- `TRANSFER` - 转办
- `DELEGATE` - 委托
- `ROLLBACK` - 回退
- `WITHDRAW` - 撤回

#### 流程操作
- `PROCESS_SUBMIT` - 流程提交
- `PROCESS_REJECT` - 流程驳回
- `COMPOSITE` - 组合动作

#### 自定义操作
- `API_CALL` - API调用
- `FORM_POPUP` - 表单弹出（关联ACTION类型表单）
- `CUSTOM_SCRIPT` - 自定义脚本

**注意**：前端使用 `CUSTOM_SCRIPT` 而不是 `SCRIPT`，`FORM_POPUP` 用于关联ACTION类型的表单。

#### 动作配置示例（config_json）

**API_CALL 配置：**
```json
{
  "url": "/api/budget/query",
  "method": "GET",
  "headers": "{\"Content-Type\": \"application/json\"}",
  "body": "{}"
}
```

**FORM_POPUP 配置（关联ACTION表单）：**
```json
{
  "formId": 13,
  "dialogTitle": "审批意见",
  "dialogWidth": "600px"
}
```

**CUSTOM_SCRIPT 配置：**
```json
{
  "script": "// JavaScript code here\nfunction calculateTotal(items) {\n  return items.reduce((sum, item) => sum + item.amount, 0);\n}"
}
```

**APPROVE/REJECT 配置：**
```json
{
  "targetStatus": "APPROVED",
  "requireComment": true,
  "confirmMessage": "确定要批准此申请吗？"
}
```

**TRANSFER/DELEGATE 配置：**
```json
{
  "requireAssignee": true,
  "requireComment": true
}
```

**ROLLBACK 配置：**
```json
{
  "targetStep": "previous",
  "requireComment": true
}
```

**WITHDRAW 配置：**
```json
{
  "targetStatus": "CANCELLED",
  "allowedFromStatus": ["PENDING", "IN_PROGRESS"]
}
```

### 9. 表单-表绑定（重要！）

**表单必须绑定到表才能正常工作！** 绑定关系存储在 `dw_form_table_bindings` 表中。

#### 绑定类型枚举
- `PRIMARY` - 主表绑定（每个表单必须有一个）
- `SUB` - 子表绑定（需要指定 foreign_key_field）
- `RELATED` - 关联表绑定（需要指定 foreign_key_field）

#### 绑定模式枚举
- `EDITABLE` - 可编辑
- `READONLY` - 只读

#### dw_form_table_bindings 表结构
```sql
CREATE TABLE dw_form_table_bindings (
    id BIGSERIAL PRIMARY KEY,
    form_id BIGINT NOT NULL REFERENCES dw_form_definitions(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id),
    binding_type VARCHAR(20) NOT NULL,  -- PRIMARY, SUB, RELATED
    binding_mode VARCHAR(20) NOT NULL DEFAULT 'READONLY',  -- EDITABLE, READONLY
    foreign_key_field VARCHAR(100),  -- SUB和RELATED类型需要指定外键字段
    sort_order INTEGER DEFAULT 0,
    UNIQUE (form_id, table_id)
);
```

#### 绑定规则
1. **每个表单必须有一个 PRIMARY 绑定**
2. **SUB 和 RELATED 绑定必须指定 foreign_key_field**（通常是 `request_id`）
3. **审批表单通常将主表设为 READONLY，动作表设为 EDITABLE**

#### 绑定SQL示例
```sql
-- 主表单绑定主表（可编辑）
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order)
SELECT fd.id, td.id, 'PRIMARY', 'EDITABLE', NULL, 0
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Main Form' AND td.table_name = 'main_table'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-xxx')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 主表单绑定子表（可编辑，需要外键）
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order)
SELECT fd.id, td.id, 'SUB', 'EDITABLE', 'request_id', 1
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Main Form' AND td.table_name = 'sub_table'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-xxx')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;

-- 审批表单绑定主表（只读）
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order)
SELECT fd.id, td.id, 'PRIMARY', 'READONLY', NULL, 0
FROM dw_form_definitions fd, dw_table_definitions td
WHERE fd.form_name = 'Approval Form' AND td.table_name = 'main_table'
  AND fd.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-xxx')
  AND td.function_unit_id = fd.function_unit_id
ON CONFLICT (form_id, table_id) DO NOTHING;
```

### 10. 节点绑定（表单和动作）
表单和动作与流程节点的绑定存储在BPMN XML的扩展属性中：

#### 命名空间（必须）
```xml
xmlns:custom="http://custom.bpmn.io/schema"
```

#### 表单绑定属性
在`userTask`节点的`extensionElements`中添加：
- `formId` - 表单ID（数字）
- `formName` - 表单名称（字符串）
- `formReadOnly` - 是否只读（"true"/"false"）

#### 动作绑定属性
在`userTask`节点的`extensionElements`中添加：
- `actionIds` - 动作ID数组（JSON格式，如`"[1,2,3]"`）

#### 处理人分配方式
在`userTask`节点的`extensionElements`中添加：
- `assigneeType` - 分配方式类型（必须使用下面7种标准类型之一）
- `assigneeLabel` - 分配方式显示标签
- `assigneeValue` - 分配值（仅 FIXED_DEPT 和 VIRTUAL_GROUP 类型需要，指定部门ID或虚拟组ID）

**分配方式类型枚举（7种标准方式）：**

| 类型 | 代码 | 说明 | 是否需要认领 | 是否需要assigneeValue |
|------|------|------|-------------|---------------------|
| 职能经理 | `FUNCTION_MANAGER` | 当前人的职能经理 | 否（直接分配） | 否 |
| 实体经理 | `ENTITY_MANAGER` | 当前人的实体经理 | 否（直接分配） | 否 |
| 流程发起人 | `INITIATOR` | 流程发起人 | 否（直接分配） | 否 |
| 本部门其他人 | `DEPT_OTHERS` | 当前人部门的非本人 | 是 | 否 |
| 上级部门 | `PARENT_DEPT` | 当前人上级部门 | 是 | 否 |
| 指定部门 | `FIXED_DEPT` | 某个部门的所有人 | 是 | 是（部门ID） |
| 虚拟组 | `VIRTUAL_GROUP` | 某个虚拟组 | 是 | 是（虚拟组ID） |

**重要规则：非具体到人的分配都采用认领机制**

- 直接分配类型（FUNCTION_MANAGER, ENTITY_MANAGER, INITIATOR）：任务直接分配给特定用户
- 认领类型（DEPT_OTHERS, PARENT_DEPT, FIXED_DEPT, VIRTUAL_GROUP）：任务分配给候选人列表，需要用户主动认领

**前端实现说明：**
- 处理人配置在 `UserTaskProperties.vue` 组件中实现
- 选择 `FIXED_DEPT` 时，会显示部门树选择器（数据来自 admin-center `/departments/tree` API）
- 选择 `VIRTUAL_GROUP` 时，会显示虚拟组下拉选择器（数据来自 admin-center `/virtual-groups` API）
- 前端通过 `/api/admin-center` 代理访问 admin-center 服务（端口 8090）

**相关文件：**
- `frontend/developer-workstation/src/components/designer/properties/UserTaskProperties.vue`
- `frontend/developer-workstation/src/api/adminCenter.ts`
- `frontend/developer-workstation/vite.config.ts`（代理配置）

#### 全局动作绑定
在`process`节点的`extensionElements`中添加：
- `globalActionIds` - 全局动作ID数组（JSON格式）

## 完整BPMN模板（含节点绑定）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions 
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:custom="http://custom.bpmn.io/schema"
    id="Definitions_xxx" 
    targetNamespace="http://workflow.example.com/xxx">
  <bpmn:process id="Process_xxx" name="流程名称" isExecutable="true">
    <!-- 全局动作绑定 -->
    <bpmn:extensionElements>
      <custom:properties>
        <custom:property name="globalActionIds" value="[7,8]"/>
      </custom:properties>
    </bpmn:extensionElements>
    
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- 带表单和动作绑定的用户任务 - 发起人提交 -->
    <bpmn:userTask id="Task_Submit" name="提交申请">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="1"/>
          <custom:property name="formName" value="申请表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[1,6]"/>
          <custom:property name="assigneeType" value="INITIATOR"/>
          <custom:property name="assigneeLabel" value="流程发起人"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 审批节点示例 - 实体经理审批（直接分配） -->
    <bpmn:userTask id="Task_Approval" name="主管审批">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="ENTITY_MANAGER"/>
          <custom:property name="assigneeLabel" value="实体经理"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 认领任务示例 - 指定部门（需要认领） -->
    <!--
    <bpmn:userTask id="Task_DeptReview" name="部门审核">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="4"/>
          <custom:property name="formName" value="审核表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3]"/>
          <custom:property name="assigneeType" value="FIXED_DEPT"/>
          <custom:property name="assigneeLabel" value="财务部"/>
          <custom:property name="assigneeValue" value="dept-finance-001"/>
        </custom:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
    -->
    
    <!-- 其他流程元素... -->
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_xxx">
      <!-- 每个元素的图形定义 -->
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="100" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Submit_di" bpmnElement="Task_Submit">
        <dc:Bounds x="200" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="136" y="118"/>
        <di:waypoint x="200" y="118"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

### 10. SQL文件执行顺序
SQL文件需要按顺序执行，因为存在外键依赖：
```
03-01-function-unit.sql  # 先创建功能单元
03-02-tables.sql         # 创建表定义（依赖功能单元）
03-03-fields-main.sql    # 创建主表字段（依赖表定义）
03-04-fields-sub.sql     # 创建子表字段（依赖表定义）
03-05-foreign-keys.sql   # 创建外键（依赖字段定义）
03-06-forms.sql          # 创建表单（依赖表定义）
03-07-actions.sql        # 创建动作（依赖功能单元）
03-08-process.sql        # 创建流程定义（依赖功能单元）
03-09-process-bindings.sql # 更新流程绑定（依赖表单和动作ID）
```

**注意**：`03-09-process-bindings.sql`必须在表单和动作创建后执行，因为绑定中引用的formId和actionIds需要是数据库中实际存在的ID。

## 数据库表归属
- `dw_*` 表属于 developer-workstation 模块
- `sys_*` 表属于 platform-security 模块
- `admin_*` 表属于 admin-center 模块

## 数据库连接信息
- 数据库名：`workflow_platform`
- 用户名：`platform`
- 密码：`platform123`
- Docker容器名：`platform-postgres`

**查询数据库表结构：**
```bash
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "\d dw_process_definitions"
```

**执行SQL文件：**
```bash
Get-Content -Path "xxx.sql" -Encoding UTF8 | docker exec -i platform-postgres psql -U platform -d workflow_platform
```

## 实际表结构（重要！）

**关键规则：在编写INSERT语句前，必须先查询实际表结构！**

```bash
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "\d table_name"
```

### dw_field_definitions 表
```sql
-- 实际结构
CREATE TABLE dw_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id),
    field_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,  -- 注意：是 data_type，不是 field_type
    length INTEGER,
    precision_value INTEGER,
    scale INTEGER,
    nullable BOOLEAN DEFAULT true,
    default_value VARCHAR(500),
    is_primary_key BOOLEAN DEFAULT false,
    is_unique BOOLEAN DEFAULT false,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0
    -- 注意：没有 field_label, is_required, max_length, decimal_places, created_by 等列！
);
```

### dw_form_definitions 表
```sql
CREATE TABLE dw_form_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id),
    form_name VARCHAR(100) NOT NULL,
    form_type VARCHAR(20) NOT NULL,  -- MAIN, SUB, ACTION, POPUP
    config_json JSONB NOT NULL DEFAULT '{}',
    description TEXT,
    bound_table_id BIGINT REFERENCES dw_table_definitions(id)
    -- 注意：没有 created_by 列！
);
```

### dw_action_definitions 表
```sql
CREATE TABLE dw_action_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id),
    action_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(20) NOT NULL,  -- PROCESS_SUBMIT, APPROVE, REJECT, TRANSFER, ROLLBACK, WITHDRAW, API_CALL, SCRIPT
    config_json JSONB NOT NULL DEFAULT '{}',
    icon VARCHAR(50),
    button_color VARCHAR(20),
    description TEXT,
    is_default BOOLEAN DEFAULT false
    -- 注意：没有 action_label, sort_order, created_by 等列！
);
```

### dw_process_definitions 表
```sql
CREATE TABLE dw_process_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL REFERENCES dw_function_units(id),
    bpmn_xml TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- 注意：没有 process_key, process_name, description, status 等列！
);
```

### dw_foreign_keys 表
```sql
CREATE TABLE dw_foreign_keys (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    field_id BIGINT NOT NULL REFERENCES dw_field_definitions(id) ON DELETE CASCADE,
    ref_table_id BIGINT NOT NULL REFERENCES dw_table_definitions(id) ON DELETE CASCADE,
    ref_field_id BIGINT NOT NULL REFERENCES dw_field_definitions(id) ON DELETE CASCADE,
    on_delete VARCHAR(20) DEFAULT 'NO ACTION',
    on_update VARCHAR(20) DEFAULT 'NO ACTION'
);
```

**外键关系说明：**
- `table_id` - 源表ID（子表/关联表）
- `field_id` - 源字段ID（通常是 request_id 等外键字段）
- `ref_table_id` - 目标表ID（主表）
- `ref_field_id` - 目标字段ID（通常是主表的 id 字段）

**外键关系在前端的显示：**
- 外键关系通过 `/api/v1/function-units/{id}/tables/foreign-keys` API 获取
- 前端 TableDesigner.vue 的"关联"列会显示每个表的外键关系数量
- 主表会显示被引用的次数，子表会显示引用主表的次数

## 常见问题和解决方案

### 问题1：中文乱码
**原因：** PowerShell `Out-File` 或 here-string (@" "@) 编码问题
**解决：** 
- 使用 `[System.IO.File]::WriteAllText()` 并指定UTF-8编码
- **重要：** 即使使用 WriteAllText，here-string 中的中文仍可能乱码
- **最佳实践：** SQL文件中使用英文，中文名称存储在数据库的 `name` 字段中（如功能单元的 name 字段）

### 问题2：SQL语法错误（Base64字符串中有特殊字符）
**原因：** BPMN XML中的中文字符在Base64编码后可能产生问题
**解决：** BPMN XML中使用英文，中文名称可以在数据库的其他字段中存储

### 问题3：列不存在错误
**原因：** 假设的表结构与实际不符
**解决：** 先用 `\d table_name` 查询实际表结构

### 问题4：重复键冲突
**原因：** 表单/动作名称有唯一约束
**解决：** 确保名称唯一，或先删除已存在的数据

### 问题5：PowerShell here-string 中文编码问题
**原因：** PowerShell here-string (@' '@ 或 @" "@) 在某些环境下无法正确处理中文
**解决：** 
- 使用 StringBuilder 动态构建SQL
- 或使用数组定义数据，循环生成SQL语句
- 示例见 `generate-fields.ps1`

### 问题6：BPMN条件表达式中的特殊字符
**原因：** BPMN XML中的条件表达式包含 `<` 或 `>` 字符会导致XML解析错误
**错误示例：** `<bpmn:conditionExpression>${amount < 10000}</bpmn:conditionExpression>`
**解决方案：**
1. 使用 CDATA 包裹：`<bpmn:conditionExpression><![CDATA[${amount < 10000}]]></bpmn:conditionExpression>`
2. 或使用 XML 实体：`&lt;` 代替 `<`，`&gt;` 代替 `>`

### 问题7：外键关系不显示
**原因：** 前端 TableDesigner.vue 之前使用 localStorage 存储关联关系，而不是从数据库读取
**解决：** 
- 已添加 `/api/v1/function-units/{id}/tables/foreign-keys` API 端点
- 前端现在从数据库 API 获取外键关系
- 相关文件：
  - `backend/developer-workstation/src/main/java/com/developer/dto/ForeignKeyDTO.java`
  - `backend/developer-workstation/src/main/java/com/developer/controller/TableDesignController.java`
  - `frontend/developer-workstation/src/api/functionUnit.ts`
  - `frontend/developer-workstation/src/components/designer/TableDesigner.vue`

### 问题8：字段定义不显示（字段数为0）
**原因：** JPA 查询没有使用 FETCH JOIN 加载关联的字段定义
**解决：** 
- 在 `TableDefinitionRepository` 中添加 `findByFunctionUnitIdWithFields` 方法
- 使用 `LEFT JOIN FETCH t.fieldDefinitions` 加载字段
- 相关文件：`backend/developer-workstation/src/main/java/com/developer/repository/TableDefinitionRepository.java`

### 问题9：表单绑定不显示（显示"未绑定"）
**原因：** 
- 后端 `FormDefinitionRepository.findByFunctionUnitIdWithBoundTable()` 只加载旧的 `boundTable` 字段
- 没有加载新的 `tableBindings` 集合
- 前端使用 `row.boundTableId` 显示绑定表，而不是 `tableBindings`

**解决：**
1. 在 `FormDefinitionRepository` 中添加 `findByFunctionUnitIdWithBindings` 方法：
   ```java
   @Query("SELECT DISTINCT f FROM FormDefinition f LEFT JOIN FETCH f.boundTable LEFT JOIN FETCH f.tableBindings tb LEFT JOIN FETCH tb.table WHERE f.functionUnit.id = :functionUnitId")
   List<FormDefinition> findByFunctionUnitIdWithBindings(@Param("functionUnitId") Long functionUnitId);
   ```
2. 更新 `FormDesignComponentImpl.getByFunctionUnitId()` 使用新查询
3. 在 `FormTableBinding` 实体的 `table` 字段添加 `@JsonIgnore` 避免循环引用
4. 前端添加 `getPrimaryBinding()` 函数从 `tableBindings` 获取 PRIMARY 绑定

**相关文件：**
- `backend/developer-workstation/src/main/java/com/developer/repository/FormDefinitionRepository.java`
- `backend/developer-workstation/src/main/java/com/developer/component/impl/FormDesignComponentImpl.java`
- `backend/developer-workstation/src/main/java/com/developer/entity/FormTableBinding.java`
- `frontend/developer-workstation/src/components/designer/FormDesigner.vue`

### 问题10：节点绑定不显示（BPMN中formId与数据库不匹配）
**原因：** 
- BPMN XML中的`formId`使用的是相对值（如1, 3, 6），而不是实际的数据库ID（如11, 13, 16）
- 前端从BPMN XML解析`formId`后，与`store.forms`中的表单ID进行匹配
- 如果ID不匹配，`getFormBoundNodes(formId)`返回空数组

**解决：**
1. 生成BPMN XML时，必须使用实际的数据库表单ID
2. 先查询表单ID：`SELECT id, form_name FROM dw_form_definitions WHERE function_unit_id = X;`
3. 在BPMN XML中使用查询到的实际ID
4. 或者使用动态脚本在生成SQL时替换占位符

**示例：**
```xml
<!-- 错误：使用相对ID -->
<custom:property name="formId" value="1"/>

<!-- 正确：使用实际数据库ID -->
<custom:property name="formId" value="11"/>
```

**修复脚本示例（generate-process-fixed.ps1）：**
```powershell
# 查询实际的表单ID
# SELECT id, form_name FROM dw_form_definitions WHERE function_unit_id = 3;
# 结果：
#  id |         form_name          
# ----+----------------------------
#  11 | Purchase Request Main Form  -> Task_Submit
#  13 | Approval Form               -> 审批节点
#  16 | Countersign Form            -> Task_Countersign

# 在BPMN XML中使用实际ID
$bpmnXml = @'
...
<custom:property name="formId" value="11"/>  <!-- 使用实际ID 11，而不是 1 -->
...
'@
```

**验证formId是否正确：**
```powershell
# 解码BPMN XML并检查formId
$bpmn = docker exec -i platform-postgres psql -U platform -d workflow_platform -t -c "SELECT bpmn_xml FROM dw_process_definitions WHERE function_unit_id = 3;"
$decoded = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($bpmn.Trim()))
$decoded | Select-String -Pattern 'formId.*value="(\d+)"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value }
```

**相关文件：**
- `deploy/init-scripts/04-purchase-workflow/generate-process-fixed.ps1`
- `frontend/developer-workstation/src/components/designer/FormDesigner.vue` (parseFormBindingsFromBpmn函数)

### 问题11：动作绑定节点不显示（BPMN中actionIds与数据库不匹配）
**原因：** 
- BPMN XML中的`actionIds`使用的是相对值（如`[1,2,3]`），而不是实际的数据库ID（如`[21,22,23]`）
- 前端从BPMN XML解析`actionIds`后，与`store.actions`中的动作ID进行匹配
- 如果ID不匹配，`getActionBoundNodes(actionId)`返回空数组

**解决：**
1. 生成BPMN XML时，必须使用实际的数据库动作ID
2. 先查询动作ID：`SELECT id, action_name FROM dw_action_definitions WHERE function_unit_id = X;`
3. 在BPMN XML中使用查询到的实际ID

**示例：**
```xml
<!-- 错误：使用相对ID -->
<custom:property name="actionIds" value="[1,2,3]"/>

<!-- 正确：使用实际数据库ID -->
<custom:property name="actionIds" value="[21,22,23]"/>
```

**验证actionIds是否正确：**
```powershell
# 解码BPMN XML并检查actionIds
$bpmn = docker exec -i platform-postgres psql -U platform -d workflow_platform -t -c "SELECT bpmn_xml FROM dw_process_definitions WHERE function_unit_id = 3;"
$decoded = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($bpmn.Trim()))
$decoded | Select-String -Pattern 'actionIds.*value="([^"]+)"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value }
```

**相关文件：**
- `deploy/init-scripts/04-purchase-workflow/generate-process-fixed-v2.ps1`
- `frontend/developer-workstation/src/components/designer/ActionDesigner.vue` (parseActionBindingsFromBpmn函数)

### 问题12：动作类型不匹配（SCRIPT vs CUSTOM_SCRIPT）
**原因：** 
- 数据库中使用了 `SCRIPT` 类型，但前端 ActionDesigner.vue 期望的是 `CUSTOM_SCRIPT`
- 前端的 `actionTypeOptions` 定义了以下类型：`PROCESS_SUBMIT`, `APPROVE`, `REJECT`, `TRANSFER`, `DELEGATE`, `ROLLBACK`, `WITHDRAW`, `API_CALL`, `FORM_POPUP`, `CUSTOM_SCRIPT`
- 如果数据库中的 `action_type` 不在前端支持的列表中，动作将无法正确显示或处理

**解决：**
1. 使用正确的动作类型枚举值（参见"8. 动作类型枚举"）
2. 如果已有数据使用了错误的类型，需要更新数据库：
   ```sql
   -- 将 SCRIPT 类型更新为 CUSTOM_SCRIPT
   UPDATE dw_action_definitions 
   SET action_type = 'CUSTOM_SCRIPT' 
   WHERE action_type = 'SCRIPT';
   ```
3. 同时确保 `config_json` 包含正确的配置（如 `script` 字段）

**动作类型与config_json对应关系：**

| 动作类型 | config_json 必需字段 | 说明 |
|---------|---------------------|------|
| `PROCESS_SUBMIT` | `targetStatus`, `confirmMessage` | 流程提交 |
| `APPROVE` | `targetStatus`, `requireComment`, `confirmMessage` | 批准 |
| `REJECT` | `targetStatus`, `requireComment`, `confirmMessage` | 拒绝 |
| `TRANSFER` | `requireAssignee`, `requireComment` | 转办 |
| `DELEGATE` | `requireAssignee`, `requireComment` | 委托 |
| `ROLLBACK` | `targetStep`, `requireComment` | 回退 |
| `WITHDRAW` | `targetStatus`, `allowedFromStatus` | 撤回 |
| `API_CALL` | `url`, `method`, `headers`, `body` | API调用 |
| `FORM_POPUP` | `formId`, `dialogTitle`, `dialogWidth` | 表单弹出 |
| `CUSTOM_SCRIPT` | `script` | 自定义脚本 |

**修复SQL示例（04-11-action-configs.sql）：**
```sql
-- 修复动作类型和配置
UPDATE dw_action_definitions SET 
    action_type = 'PROCESS_SUBMIT',
    config_json = '{"targetStatus":"SUBMITTED","confirmMessage":"确定要提交此申请吗？"}'::jsonb
WHERE action_name = 'Submit' AND function_unit_id = 3;

UPDATE dw_action_definitions SET 
    action_type = 'CUSTOM_SCRIPT',
    config_json = '{"script":"// Calculate total amount\nfunction calculateTotal(items) {\n  return items.reduce((sum, item) => sum + (item.quantity * item.unit_price), 0);\n}\n\nconst total = calculateTotal(formData.purchase_items);\nsetFieldValue(''total_amount'', total);"}'::jsonb
WHERE action_name = 'Calculate Total' AND function_unit_id = 3;
```

**相关文件：**
- `frontend/developer-workstation/src/components/designer/ActionDesigner.vue` (actionTypeOptions定义)
- `deploy/init-scripts/04-purchase-workflow/04-11-action-configs.sql`

### 问题13：ACTION表单未被动作使用
**原因：** 
- 创建了 ACTION 类型的表单（如审批表单），但没有创建 `FORM_POPUP` 类型的动作来关联它
- ACTION 表单需要通过 `FORM_POPUP` 动作来触发显示

**解决：**
1. 创建 `FORM_POPUP` 类型的动作，在 `config_json` 中指定 `formId`
2. 将该动作绑定到流程节点的 `actionIds` 中

**示例：**
```sql
-- 创建关联ACTION表单的动作
INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, config_json, icon, button_color, description)
SELECT f.id, 'Fill Approval Form', 'FORM_POPUP', 
    '{"formId": 13, "dialogTitle": "Approval Form", "dialogWidth": "800px"}'::jsonb,
    'el-icon-edit', 'primary', 'Open approval form dialog'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
```

**注意：** `formId` 必须是实际存在的 ACTION 类型表单的数据库ID。

## 参考文件
- 实体定义：`backend/developer-workstation/src/main/java/com/developer/entity/`
- XML工具类：`backend/developer-workstation/src/main/java/com/developer/util/XmlEncodingUtil.java`
- 示例SQL：`deploy/init-scripts/03-test-workflow/`
- 采购申请示例：`deploy/init-scripts/04-purchase-workflow/`


## 完整工作示例（采购申请）

参考 `deploy/init-scripts/04-purchase-workflow/` 目录下的文件：

### 文件结构
```
04-purchase-workflow/
├── 04-01-function-unit.sql    # 功能单元
├── 04-02-tables.sql           # 6个表定义
├── 04-03-fields-main.sql      # 主表字段（由generate-fields.ps1生成）
├── 04-04-fields-sub.sql       # 子表字段（由generate-fields.ps1生成）
├── 04-05-foreign-keys.sql     # 外键关系（dw_foreign_keys表）
├── 04-06-forms.sql            # 表单定义（由generate-forms-actions.ps1生成）
├── 04-07-actions.sql          # 动作定义（由generate-forms-actions.ps1生成）
├── 04-08-process.sql          # 流程定义（由generate-process-v3.ps1生成）
├── 04-08-process-fixed.sql    # 修复后的流程定义（使用实际数据库formId）
├── 04-08-process-fixed-v2.sql # 修复后的流程定义（使用实际数据库formId和actionIds）
├── 04-09-form-bindings.sql    # 表单-表绑定关系（重要！）
├── 04-10-form-configs.sql     # 表单配置（form-create规则，由generate-form-configs.ps1生成）
├── 04-11-action-configs.sql   # 动作配置（修复action_type和config_json）
├── generate-fields.ps1        # 生成字段SQL的脚本
├── generate-forms-actions.ps1 # 生成表单和动作SQL的脚本
├── generate-process-v3.ps1    # 生成流程SQL的脚本
├── generate-process-fixed.ps1 # 修复formId的脚本（使用实际数据库ID）
├── generate-process-fixed-v2.ps1 # 修复formId和actionIds的脚本（推荐使用）
└── generate-form-configs.ps1  # 生成表单配置的脚本（支持多Tab页）
```

### 执行顺序
```powershell
# 1. 进入目录
cd deploy/init-scripts/04-purchase-workflow

# 2. 生成SQL文件（使用PowerShell脚本）
powershell -ExecutionPolicy Bypass -File generate-fields.ps1
powershell -ExecutionPolicy Bypass -File generate-forms-actions.ps1
powershell -ExecutionPolicy Bypass -File generate-process-v3.ps1

# 3. 按顺序执行SQL（使用 -Raw 参数读取完整文件）
Get-Content -Path "04-01-function-unit.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-02-tables.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-03-fields-main.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-04-fields-sub.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-05-foreign-keys.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-06-forms.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-07-actions.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-08-process.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
Get-Content -Path "04-09-form-bindings.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 5. 生成并执行表单配置（form-create规则）
powershell -ExecutionPolicy Bypass -File generate-form-configs.ps1
Get-Content -Path "04-10-form-configs.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 6. 更新动作配置（修复action_type和config_json）
Get-Content -Path "04-11-action-configs.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform

# 7. 如果节点绑定不显示，需要修复formId（使用实际数据库ID）
# 先查询表单ID
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT id, form_name FROM dw_form_definitions WHERE function_unit_id = 3;"
# 然后生成并执行修复后的流程SQL
powershell -ExecutionPolicy Bypass -File generate-process-fixed.ps1
Get-Content -Path "04-08-process-fixed.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
```

**注意：** 使用 `-Raw` 参数确保完整读取文件，避免管道传输时的编码问题。

### 验证结果
```sql
-- 查看功能单元
SELECT f.id, f.code, f.name, f.status,
  (SELECT COUNT(*) FROM dw_table_definitions t WHERE t.function_unit_id = f.id) as tables,
  (SELECT COUNT(*) FROM dw_form_definitions fm WHERE fm.function_unit_id = f.id) as forms,
  (SELECT COUNT(*) FROM dw_action_definitions a WHERE a.function_unit_id = f.id) as actions,
  (SELECT COUNT(*) FROM dw_process_definitions p WHERE p.function_unit_id = f.id) as processes
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';
```

## 表单配置生成（form-create规则）

### 概述
表单配置存储在 `dw_form_definitions.config_json` 字段中，使用 form-create 库的规则格式。

### config_json 结构
```json
{
  "rule": [...],      // form-create 规则数组
  "options": {...}    // form-create 选项
}
```

### 支持的组件类型
| 组件类型 | 说明 | 对应数据类型 |
|---------|------|-------------|
| `input` | 输入框 | VARCHAR, TEXT |
| `inputNumber` | 数字输入 | INTEGER, BIGINT, DECIMAL |
| `select` | 下拉选择 | VARCHAR (枚举) |
| `datePicker` | 日期选择 | DATE, TIMESTAMP |
| `switch` | 开关 | BOOLEAN |
| `upload` | 文件上传 | TEXT (附件) |
| `subForm` | 子表单 | 子表数据 |
| `el-tabs` | 多Tab页容器 | - |
| `el-tab-pane` | Tab页 | - |

### 多Tab页布局示例
```json
{
  "rule": [
    {
      "type": "el-tabs",
      "props": { "type": "border-card", "modelValue": "basic" },
      "children": [
        {
          "type": "el-tab-pane",
          "props": { "label": "Basic Info", "name": "basic" },
          "children": [
            { "type": "input", "field": "title", "title": "Title", "props": { "placeholder": "Enter title" } }
          ]
        },
        {
          "type": "el-tab-pane",
          "props": { "label": "Details", "name": "details" },
          "children": [...]
        }
      ]
    }
  ],
  "options": {
    "submitBtn": false,
    "resetBtn": false,
    "form": { "labelWidth": "120px" }
  }
}
```

### 子表单（subForm）示例
```json
{
  "type": "subForm",
  "field": "purchase_items",
  "title": "Purchase Items",
  "props": { "maxLength": 50, "minLength": 1 },
  "children": [
    { "type": "input", "field": "item_name", "title": "Item Name" },
    { "type": "inputNumber", "field": "quantity", "title": "Quantity" }
  ]
}
```

### 验证规则示例
```json
{
  "type": "input",
  "field": "title",
  "title": "Title",
  "validate": [
    { "required": true, "message": "Title is required", "trigger": "blur" }
  ]
}
```

### 表单配置生成脚本模板
```powershell
# generate-form-configs.ps1

# 定义表单配置
$mainFormConfig = @{
    rule = @(
        @{
            type = "el-tabs"
            props = @{ type = "border-card"; modelValue = "basic" }
            children = @(
                @{
                    type = "el-tab-pane"
                    props = @{ label = "Basic Info"; name = "basic" }
                    children = @(
                        @{ type = "input"; field = "title"; title = "Title"; props = @{ placeholder = "Enter title" } }
                    )
                }
            )
        }
    )
    options = @{
        submitBtn = $false
        resetBtn = $false
        form = @{ labelWidth = "120px" }
    }
}

# 转换为JSON（使用足够的深度）
$mainJson = $mainFormConfig | ConvertTo-Json -Depth 100 -Compress

# 转义单引号
$mainJson = $mainJson -replace "'", "''"

# 生成SQL
$sql = @"
UPDATE dw_form_definitions SET config_json = '$mainJson'::jsonb WHERE id = 11;
"@

[System.IO.File]::WriteAllText("04-10-form-configs.sql", $sql, [System.Text.Encoding]::UTF8)
```

### 表单类型与配置建议

| 表单类型 | 配置建议 |
|---------|---------|
| MAIN (主表单) | 多Tab页布局，包含所有主表字段和子表 |
| SUB (子表单) | 简单列表布局，用于子表数据编辑 |
| ACTION (动作表单) | 只读主表信息 + 可编辑动作字段 |
| POPUP (弹出表单) | 简单表单，用于选择或查询 |

### 审批表单配置要点
1. 主表信息设为只读（`props: { disabled: true }`）
2. 审批意见字段可编辑
3. 使用多Tab页分离"申请信息"和"审批意见"

## 外键关系生成

### 外键SQL生成脚本示例
```powershell
# generate-foreign-keys.ps1
$sql = New-Object System.Text.StringBuilder

# 获取主表ID字段
[void]$sql.AppendLine("-- Foreign Key Relations")
[void]$sql.AppendLine("-- 子表.request_id -> 主表.id")
[void]$sql.AppendLine("")

# 定义外键关系：源表 -> 目标表
$relations = @(
    @{ sourceTable = "purchase_item"; targetTable = "purchase_request" },
    @{ sourceTable = "supplier_info"; targetTable = "purchase_request" },
    @{ sourceTable = "budget_info"; targetTable = "purchase_request" },
    @{ sourceTable = "purchase_approval"; targetTable = "purchase_request" },
    @{ sourceTable = "countersign_record"; targetTable = "purchase_request" }
)

foreach ($rel in $relations) {
    [void]$sql.AppendLine("INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)")
    [void]$sql.AppendLine("SELECT ")
    [void]$sql.AppendLine("    t.id,")
    [void]$sql.AppendLine("    (SELECT f.id FROM dw_field_definitions f WHERE f.table_id = t.id AND f.field_name = 'request_id'),")
    [void]$sql.AppendLine("    rt.id,")
    [void]$sql.AppendLine("    (SELECT f.id FROM dw_field_definitions f WHERE f.table_id = rt.id AND f.field_name = 'id'),")
    [void]$sql.AppendLine("    'CASCADE',")
    [void]$sql.AppendLine("    'CASCADE'")
    [void]$sql.AppendLine("FROM dw_table_definitions t, dw_table_definitions rt")
    [void]$sql.AppendLine("WHERE t.table_name = '$($rel.sourceTable)'")
    [void]$sql.AppendLine("  AND rt.table_name = '$($rel.targetTable)'")
    [void]$sql.AppendLine("  AND t.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request')")
    [void]$sql.AppendLine("  AND rt.function_unit_id = t.function_unit_id;")
    [void]$sql.AppendLine("")
}

[System.IO.File]::WriteAllText("04-05-fk-relations.sql", $sql.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "Foreign keys SQL generated"
```

### 外键关系验证
```sql
-- 查看外键关系
SELECT 
    fk.id,
    t1.table_name as source_table,
    f1.field_name as source_field,
    t2.table_name as target_table,
    f2.field_name as target_field,
    fk.on_delete,
    fk.on_update
FROM dw_foreign_keys fk
JOIN dw_table_definitions t1 ON fk.table_id = t1.id
JOIN dw_field_definitions f1 ON fk.field_id = f1.id
JOIN dw_table_definitions t2 ON fk.ref_table_id = t2.id
JOIN dw_field_definitions f2 ON fk.ref_field_id = f2.id
WHERE t1.function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'fu-purchase-request');
```

## API端点参考

### 表设计相关API
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/function-units/{id}/tables` | 获取所有表定义（含字段） |
| GET | `/api/v1/function-units/{id}/tables/{tableId}` | 获取单个表详情 |
| POST | `/api/v1/function-units/{id}/tables` | 创建表 |
| PUT | `/api/v1/function-units/{id}/tables/{tableId}` | 更新表 |
| DELETE | `/api/v1/function-units/{id}/tables/{tableId}` | 删除表 |
| GET | `/api/v1/function-units/{id}/tables/{tableId}/ddl` | 生成DDL |
| GET | `/api/v1/function-units/{id}/tables/validate` | 验证表结构 |
| GET | `/api/v1/function-units/{id}/tables/foreign-keys` | 获取外键关系 |

## API测试方法

**重要：本项目使用 PowerShell 作为终端，不要使用 curl 命令！**

### 使用 Invoke-RestMethod 测试API

```powershell
# GET 请求
$headers = @{ "Content-Type" = "application/json" }
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/function-units/3/tables/foreign-keys" -Method GET -Headers $headers | ConvertTo-Json -Depth 5

# POST 请求（带JSON body）
$body = @{
    tableName = "test_table"
    tableType = "MAIN"
    description = "Test table"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8083/api/v1/function-units/3/tables" -Method POST -Headers $headers -Body $body | ConvertTo-Json -Depth 5

# 带认证的请求
$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $token"
}
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/function-units" -Method GET -Headers $headers | ConvertTo-Json -Depth 5
```

### 常用测试命令
```powershell
# 测试功能单元列表
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/function-units" -Method GET | ConvertTo-Json -Depth 3

# 测试外键关系
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/function-units/3/tables/foreign-keys" -Method GET | ConvertTo-Json -Depth 5

# 测试表定义
Invoke-RestMethod -Uri "http://localhost:8083/api/v1/function-units/3/tables" -Method GET | ConvertTo-Json -Depth 5
```
