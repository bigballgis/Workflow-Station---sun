# AI 功能单元生成指导框架

## 概述

本框架旨在指导 AI（如 GPT、Claude 等）通过对话方式生成完整的功能单元（Function Unit）。功能单元是工作流平台的核心组件，包含数据表、表单、流程、动作等完整的业务功能。

## 目标

通过结构化的对话流程，AI 能够：
1. 理解业务需求
2. 设计数据模型
3. 创建表单界面
4. 定义工作流程
5. 配置业务动作
6. 生成可执行的 SQL 脚本

---

## 第一阶段：需求收集与分析

### 1.1 业务场景理解

**AI 应询问的问题：**


1. **功能单元名称和编码**
   - 中文名称是什么？（如：数字贷款系统）
   - 英文编码是什么？（如：DIGITAL_LENDING，全大写，下划线分隔）

2. **业务目标**
   - 这个功能单元要解决什么业务问题？
   - 主要用户角色有哪些？（如：申请人、审批人、财务人员）
   - 预期的业务流程是什么？

3. **核心业务实体**
   - 主要管理什么数据？（如：贷款申请、客户信息）
   - 有哪些关联数据？（如：附件、审批记录）

### 1.2 数据需求分析

**AI 应收集的信息：**

1. **主表（MAIN）**
   - 核心业务对象的字段
   - 必填字段和可选字段
   - 字段类型和约束

2. **子表（SUB）**
   - 一对多关系的明细数据
   - 与主表的关联方式

3. **关联表（RELATION）**
   - 独立但相关的数据（如审批历史、附件）
   - 业务关联逻辑


### 1.3 流程需求分析

**AI 应明确的流程要素：**

1. **流程节点**
   - 有哪些审批环节？
   - 每个环节由谁处理？
   - 需要填写什么表单？

2. **流程分支**
   - 有哪些决策点？
   - 分支条件是什么？
   - 不同分支的后续流程

3. **流程结束**
   - 有哪些可能的结束状态？（如：批准、拒绝、撤回）

---

## 第二阶段：数据模型设计

### 2.1 表结构设计原则

**必须遵循的规则：**

1. **表类型分类**
   - MAIN：主表，每个功能单元只有一个
   - SUB：子表，与主表一对多关系
   - RELATION：关联表，独立但相关的数据

2. **标准字段**
   每个表都应包含：
   - `id`: BIGINT, 主键
   - `created_at`: TIMESTAMP, 创建时间
   - `updated_at`: TIMESTAMP, 更新时间
   
   主表额外包含：
   - `status`: VARCHAR(30), 业务状态
   - `created_by`: VARCHAR(100), 创建人


3. **外键关联**
   - 子表必须有 `{main_table}_id` 字段关联主表
   - 关联表可以有 `{main_table}_id` 字段

4. **字段命名规范**
   - 使用小写字母和下划线（snake_case）
   - 名称要有业务含义
   - 避免使用保留字

### 2.2 数据类型选择

**常用数据类型：**

| 业务场景 | 数据类型 | 示例 |
|---------|---------|------|
| 主键 | BIGINT | id |
| 短文本 | VARCHAR(长度) | name VARCHAR(100) |
| 长文本 | TEXT | description TEXT |
| 整数 | INTEGER | age INTEGER |
| 金额 | DECIMAL(15,2) | amount DECIMAL(15,2) |
| 日期 | DATE | birth_date DATE |
| 时间戳 | TIMESTAMP | created_at TIMESTAMP |
| 布尔值 | BOOLEAN | is_active BOOLEAN |

### 2.3 表设计检查清单

AI 在设计完表结构后应检查：

- [ ] 主表是否包含所有核心业务字段？
- [ ] 子表是否正确关联主表？
- [ ] 字段类型是否合适？
- [ ] 必填字段是否标记为 NOT NULL？
- [ ] 是否包含审计字段（created_at, updated_at）？


---

## 第三阶段：表单设计

### 3.1 表单类型

**系统支持的表单类型：**

1. **MAIN 表单**
   - 主要业务表单
   - 用于数据录入和展示
   - 可以绑定多个表

2. **POPUP 表单**
   - 弹窗表单
   - 用于快速操作（如信用检查、风险评估）
   - 通常绑定关联表

3. **SUB 表单**
   - 子表单（较少使用）
   - 嵌入在主表单中

### 3.2 表单配置

**标准配置项：**

```json
{
  "layout": "vertical",           // 布局方式：vertical/horizontal
  "labelWidth": "150px",          // 标签宽度
  "size": "default",              // 表单大小：default/small/large
  "showSubmitButton": true,       // 是否显示提交按钮
  "submitButtonText": "提交",     // 提交按钮文本
  "readonly": false               // 是否只读
}
```

**POPUP 表单额外配置：**

```json
{
  "width": "800px",               // 弹窗宽度
  "title": "信用检查"             // 弹窗标题
}
```


### 3.3 表单-表绑定

**绑定类型（binding_type）：**

- **PRIMARY**: 主表绑定（每个表单只能有一个）
- **SUB**: 子表绑定（一对多关系）
- **RELATED**: 关联表绑定（独立数据）

**绑定模式（binding_mode）：**

- **EDITABLE**: 可编辑
- **READONLY**: 只读

**示例：**

```sql
-- 申请表单绑定
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order)
VALUES
  (form_id, main_table_id, 'PRIMARY', 'EDITABLE', 1),
  (form_id, sub_table_id, 'SUB', 'EDITABLE', 2),
  (form_id, related_table_id, 'RELATED', 'READONLY', 3);
```

### 3.4 表单设计检查清单

- [ ] 是否为每个流程节点创建了对应的表单？
- [ ] 表单绑定的表是否正确？
- [ ] 绑定模式（可编辑/只读）是否符合业务需求？
- [ ] POPUP 表单是否配置了宽度和标题？

---

## 第四阶段：流程设计

### 4.1 BPMN 流程元素

**基本元素：**

1. **开始事件（Start Event）**
   ```xml
   <bpmn:startEvent id="StartEvent_1" name="Start">
     <bpmn:outgoing>Flow_1</bpmn:outgoing>
   </bpmn:startEvent>
   ```


2. **用户任务（User Task）**
   ```xml
   <bpmn:userTask id="Task_Submit" name="提交申请">
     <bpmn:extensionElements>
       <custom:properties>
         <custom:property name="assigneeType" value="INITIATOR" />
         <custom:property name="formId" value="1" />
         <custom:property name="formName" value="申请表单" />
         <custom:property name="formReadOnly" value="false" />
         <custom:property name="actionIds" value="[1,2]" />
         <custom:property name="actionNames" value="[&quot;提交&quot;,&quot;保存草稿&quot;]" />
       </custom:properties>
     </bpmn:extensionElements>
     <bpmn:incoming>Flow_1</bpmn:incoming>
     <bpmn:outgoing>Flow_2</bpmn:outgoing>
   </bpmn:userTask>
   ```

3. **排他网关（Exclusive Gateway）**
   ```xml
   <bpmn:exclusiveGateway id="Gateway_1" name="是否批准？">
     <bpmn:incoming>Flow_2</bpmn:incoming>
     <bpmn:outgoing>Flow_Approved</bpmn:outgoing>
     <bpmn:outgoing>Flow_Rejected</bpmn:outgoing>
   </bpmn:exclusiveGateway>
   ```

4. **结束事件（End Event）**
   ```xml
   <bpmn:endEvent id="EndEvent_Approved" name="已批准">
     <bpmn:incoming>Flow_Approved</bpmn:incoming>
   </bpmn:endEvent>
   ```


### 4.2 任务分配类型（assigneeType）

**支持的分配类型：**

| 类型 | 说明 | 使用场景 |
|-----|------|---------|
| INITIATOR | 流程发起人 | 提交申请、补充信息 |
| VIRTUAL_GROUP | 虚拟组 | 部门审批、专业团队处理 |
| ENTITY_MANAGER | 实体管理者 | 直属上级审批 |
| FUNCTION_MANAGER | 功能管理者 | 高级管理层审批 |
| SPECIFIC_USER | 指定用户 | 特定人员处理 |

**示例：**

```xml
<!-- 发起人 -->
<custom:property name="assigneeType" value="INITIATOR" />

<!-- 虚拟组 -->
<custom:property name="assigneeType" value="VIRTUAL_GROUP" />
<custom:property name="assigneeValue" value="CREDIT_OFFICERS" />

<!-- 实体管理者 -->
<custom:property name="assigneeType" value="ENTITY_MANAGER" />
```

### 4.3 流程设计检查清单

- [ ] 是否有明确的开始和结束事件？
- [ ] 每个用户任务是否配置了表单？
- [ ] 任务分配类型是否正确？
- [ ] 网关的分支逻辑是否清晰？
- [ ] 流程是否覆盖所有业务场景？


---

## 第五阶段：动作设计

### 5.1 动作类型

**系统支持的动作类型：**

1. **PROCESS_SUBMIT** - 流程提交
   - 提交申请、启动流程
   - 配置：requireComment, confirmMessage

2. **APPROVE** - 批准
   - 审批通过
   - 配置：targetStatus, requireComment, allowedRoles

3. **REJECT** - 拒绝
   - 审批拒绝
   - 配置：targetStatus, requireComment, requireReason

4. **WITHDRAW** - 撤回
   - 撤回申请
   - 配置：targetStatus, allowedFromStatus

5. **FORM_POPUP** - 弹窗表单
   - 打开弹窗填写数据
   - 配置：formId, popupWidth, popupTitle

6. **API_CALL** - API 调用
   - 调用后端接口
   - 配置：url, method, parameters


### 5.2 动作配置示例

**1. 流程提交动作**

```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description,
    config_json, icon, button_color
) VALUES (
    v_function_unit_id,
    'Submit Application',
    'PROCESS_SUBMIT',
    'Submit loan application for processing',
    '{
        "requireComment": false,
        "confirmMessage": "Submit this application?",
        "successMessage": "Application submitted successfully"
    }'::jsonb,
    'Upload',
    'primary'
);
```

**2. 弹窗表单动作**

```sql
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description,
    config_json, icon, button_color
) VALUES (
    v_function_unit_id,
    'Perform Credit Check',
    'FORM_POPUP',
    'Open credit check form',
    format('{
        "formId": %s,
        "formName": "Credit Check Form",
        "popupWidth": "800px",
        "popupTitle": "Credit Bureau Check",
        "requireComment": false,
        "allowedRoles": ["CREDIT_OFFICER"],
        "successMessage": "Credit check completed"
    }', v_credit_check_form_id)::jsonb,
    'FileSearch',
    'info'
);
```


**3. 批准/拒绝动作**

```sql
-- 批准
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description,
    config_json, icon, button_color
) VALUES (
    v_function_unit_id,
    'Approve',
    'APPROVE',
    'Approve the application',
    '{
        "targetStatus": "APPROVED",
        "requireComment": true,
        "confirmMessage": "Approve this application?",
        "allowedRoles": ["MANAGER"],
        "successMessage": "Application approved"
    }'::jsonb,
    'Check',
    'success'
);

-- 拒绝
INSERT INTO dw_action_definitions (
    function_unit_id, action_name, action_type, description,
    config_json, icon, button_color
) VALUES (
    v_function_unit_id,
    'Reject',
    'REJECT',
    'Reject the application',
    '{
        "targetStatus": "REJECTED",
        "requireComment": true,
        "requireReason": true,
        "confirmMessage": "Reject this application?",
        "successMessage": "Application rejected"
    }'::jsonb,
    'Close',
    'danger'
);
```


### 5.3 按钮颜色和图标

**标准按钮颜色：**

- `primary`: 主要操作（蓝色）- 提交、确认
- `success`: 成功操作（绿色）- 批准、完成
- `danger`: 危险操作（红色）- 拒绝、删除
- `warning`: 警告操作（橙色）- 撤回、暂停
- `info`: 信息操作（浅蓝）- 查询、查看
- `default`: 默认操作（灰色）- 取消、返回

**常用图标：**

- Upload: 上传、提交
- Check: 批准、确认
- Close: 拒绝、关闭
- FileSearch: 查询、检查
- Document: 文档、查看
- Calculator: 计算
- DollarCircle: 金额、支付
- Warning: 警告、风险
- QuestionCircle: 问题、帮助

### 5.4 动作设计检查清单

- [ ] 是否为每个流程节点配置了必要的动作？
- [ ] 动作类型是否正确？
- [ ] 配置参数是否完整？
- [ ] 按钮颜色和图标是否合适？
- [ ] 是否考虑了权限控制（allowedRoles）？


---

## 第六阶段：SQL 脚本生成

### 6.1 脚本结构

**标准 SQL 脚本应包含：**

1. **变量声明**
   ```sql
   DO $
   DECLARE
       v_function_unit_id BIGINT;
       v_main_table_id BIGINT;
       v_form_id BIGINT;
       -- 其他变量
   BEGIN
   ```

2. **创建功能单元**
   ```sql
   INSERT INTO dw_function_units (code, name, description, status, ...)
   VALUES (...) RETURNING id INTO v_function_unit_id;
   ```

3. **创建表定义**
   - 主表（MAIN）
   - 子表（SUB）
   - 关联表（RELATION）

4. **创建字段定义**
   - 为每个表创建字段

5. **创建表单定义**
   - 创建表单
   - 绑定表到表单

6. **创建流程定义**
   - 插入 BPMN XML

7. **创建动作定义**
   - 创建所有动作

8. **输出摘要信息**
   ```sql
   RAISE NOTICE '功能单元创建成功！';
   RAISE NOTICE 'ID: %', v_function_unit_id;
   END $;
   ```


### 6.2 代码规范

**必须遵循的规范：**

1. **使用 RETURNING 子句获取 ID**
   ```sql
   INSERT INTO dw_function_units (...) 
   VALUES (...) 
   RETURNING id INTO v_function_unit_id;
   ```

2. **使用 RAISE NOTICE 输出进度**
   ```sql
   RAISE NOTICE 'Created table: % (ID: %)', table_name, table_id;
   ```

3. **使用 format() 函数处理动态值**
   ```sql
   format('{
       "formId": %s,
       "formName": "%s"
   }', v_form_id, form_name)::jsonb
   ```

4. **正确转义 JSON 中的引号**
   ```sql
   -- 在 JSON 字符串中使用 &quot; 代替 "
   <custom:property name="actionNames" value="[&quot;Submit&quot;,&quot;Save&quot;]" />
   ```

5. **保持一致的缩进和格式**
   - 使用 4 空格缩进
   - SQL 关键字大写
   - 表名和字段名小写


---

## 第七阶段：验证与优化

### 7.1 完整性检查

**AI 生成脚本后应验证：**

- [ ] 功能单元信息完整（code, name, description）
- [ ] 至少有一个主表（MAIN）
- [ ] 每个表都有字段定义
- [ ] 至少有一个表单
- [ ] 表单正确绑定了表
- [ ] 有完整的 BPMN 流程
- [ ] 流程中的表单 ID 和动作 ID 正确
- [ ] 至少有基本的动作（提交、批准、拒绝）

### 7.2 业务逻辑检查

- [ ] 流程是否符合实际业务流程？
- [ ] 审批节点是否合理？
- [ ] 表单权限设置是否正确？
- [ ] 动作配置是否满足业务需求？

### 7.3 技术规范检查

- [ ] SQL 语法是否正确？
- [ ] 变量命名是否规范？
- [ ] JSON 格式是否正确？
- [ ] BPMN XML 是否有效？
- [ ] 是否有适当的注释？


---

## 对话流程模板

### 阶段 1：初始需求收集

**AI:** 你好！我将帮助你创建一个功能单元。首先，请告诉我：

1. 这个功能单元的名称是什么？（中文和英文编码）
2. 它要解决什么业务问题？
3. 主要涉及哪些用户角色？

**用户:** [回答]

### 阶段 2：数据模型设计

**AI:** 很好！现在让我们设计数据模型。

1. 主要管理什么数据？需要哪些字段？
2. 是否有明细数据（一对多关系）？
3. 是否需要记录审批历史、附件等关联数据？

**用户:** [回答]

**AI:** 根据你的描述，我设计了以下表结构：

[展示表结构]

这样设计是否符合你的需求？需要调整吗？

### 阶段 3：流程设计

**AI:** 接下来设计审批流程。请描述：

1. 从提交到完成，有哪些审批环节？
2. 每个环节由谁处理？
3. 是否有条件分支？

**用户:** [回答]

**AI:** 我设计了以下流程：

[展示流程图或描述]

是否需要调整？


### 阶段 4：表单和动作设计

**AI:** 现在设计表单和操作按钮：

1. 每个流程节点需要什么表单？
2. 需要哪些操作按钮？（提交、批准、拒绝等）
3. 是否需要弹窗表单？

**用户:** [回答]

**AI:** 我将创建以下表单和动作：

[展示表单和动作列表]

### 阶段 5：生成脚本

**AI:** 很好！我现在生成完整的 SQL 脚本。

[生成并展示脚本]

你可以：
1. 保存为 `.sql` 文件
2. 在数据库中执行
3. 在开发者工作台中查看和测试

需要我解释任何部分吗？

---

## 最佳实践

### 1. 从简单开始

- 先创建基本流程，再添加复杂功能
- 先实现核心字段，再添加扩展字段
- 逐步迭代优化

### 2. 保持一致性

- 命名规范统一
- 表结构设计一致
- 配置格式统一

### 3. 考虑扩展性

- 预留扩展字段
- 设计灵活的流程
- 支持配置化


### 4. 注重用户体验

- 表单字段分组合理
- 按钮位置和颜色符合习惯
- 提示信息清晰明确

### 5. 安全性考虑

- 设置适当的权限控制
- 敏感操作需要确认
- 记录操作日志

---

## 常见问题

### Q1: 如何确定表的类型（MAIN/SUB/RELATION）？

**A:** 
- MAIN: 核心业务对象，每个功能单元只有一个
- SUB: 与主表一对多的明细数据
- RELATION: 独立但相关的数据（如审批记录、附件）

### Q2: 什么时候使用 POPUP 表单？

**A:** 
- 快速录入补充信息
- 不影响主流程的数据采集
- 专业人员的专项操作（如信用检查、风险评估）

### Q3: 如何设计复杂的审批流程？

**A:**
- 使用排他网关处理条件分支
- 使用并行网关处理并行审批
- 合理设置任务分配类型

### Q4: 动作 ID 如何确定？

**A:**
- 在生成脚本时，动作 ID 由数据库自动生成
- 在 BPMN 中引用时，需要在脚本执行后手动更新
- 或者使用占位符，后续通过脚本批量更新


---

## 附录：完整示例参考

### 示例 1：员工请假管理

**业务场景：** 员工提交请假申请，经理审批，HR 备案

**表结构：**
- 主表：请假申请（Leave Request）
- 子表：请假明细（Leave Details）
- 关联表：审批记录（Approval Records）

**流程：** 提交 → 经理审批 → HR 审批 → 结束

**参考文件：** `deploy/init-scripts/05-demo-leave-management/03-create-complete-demo.sql`

### 示例 2：数字贷款系统

**业务场景：** 客户申请贷款，经过信用检查、风险评估、多级审批、最终放款

**表结构：**
- 主表：贷款申请（Loan Application）
- 子表：申请人信息、财务信息、抵押物信息
- 关联表：信用检查结果、审批历史、文档

**流程：** 提交 → 文档验证 → 信用检查 → 风险评估 → 经理审批 → 高级经理审批 → 放款

**参考文件：** `deploy/init-scripts/06-digital-lending/01-create-digital-lending.sql`

---

## 总结

使用本框架，AI 可以通过结构化的对话流程，帮助用户：

1. ✅ 明确业务需求
2. ✅ 设计合理的数据模型
3. ✅ 创建友好的表单界面
4. ✅ 定义清晰的工作流程
5. ✅ 配置实用的业务动作
6. ✅ 生成可执行的 SQL 脚本

**关键成功因素：**
- 充分理解业务需求
- 遵循设计规范
- 保持结构清晰
- 注重用户体验
- 持续验证优化

