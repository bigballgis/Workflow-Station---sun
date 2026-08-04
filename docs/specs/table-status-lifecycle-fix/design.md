# 表状态生命周期修复 Bugfix 设计

## 概述

本次修复针对 Admin Center 中 Relation Table 的状态生命周期管理缺陷。当前系统仅有 `DRAFT`、`DEPLOYED`、`ROLLBACK` 三种状态，无法区分"从未部署的新表"和"已部署后被编辑的表"。编辑已部署表后状态被重置为 `DRAFT`，导致该表从 Table Data 页面消失。

修复策略：引入 `INIT`（初始化）和 `UPDATED`（已更新）两个新状态，完善生命周期为 `INIT → DEPLOYED → UPDATED → DEPLOYED`，同时修复 disabled 表的过滤逻辑、Portal Visibility 禁用控制和 Actions 按钮排序。

## 术语表

- **Bug_Condition (C)**：触发 bug 的条件——创建表时状态设为 `DRAFT` 而非 `INIT`；编辑已部署表后状态被重置为 `DRAFT` 而非 `UPDATED`；Table Data 仅查询 `DEPLOYED` 状态
- **Property (P)**：期望行为——创建表状态为 `INIT`；编辑已部署表状态为 `UPDATED`；Table Data 同时查询 `DEPLOYED` 和 `UPDATED`
- **Preservation**：不受修复影响的现有行为——部署流程、回滚流程、数据 CRUD 操作、已启用表的正常显示
- **RelationTableStatus**：`RelationTableStatus.java` 中的枚举，定义表的生命周期状态
- **RelationTableStructureServiceImpl**：`RelationTableStructureServiceImpl.java` 中的服务，负责表结构的创建和编辑
- **RelationTableDataServiceImpl**：`RelationTableDataServiceImpl.java` 中的服务，负责 Table Data 页面的数据查询
- **RelationTableDeployServiceImpl**：`RelationTableDeployServiceImpl.java` 中的服务，负责表的部署和回滚

## Bug 详情

### Bug 条件

Bug 在以下场景中触发：
1. 创建新表时，状态被设为 `DRAFT` 而非 `INIT`
2. 编辑状态为 `DEPLOYED` 的表时，状态被重置为 `DRAFT`，导致表从 Table Data 消失
3. Table Data 的 `getDeployedTables()` 仅查询 `DEPLOYED` 状态，不包含 `UPDATED`
4. disabled 表未被过滤，仍在 Table Data 和 Admin Center 数据视图中显示
5. disabled 表的 Portal Visibility 开关未被禁用
6. Actions 按钮顺序不符合用户期望

**形式化规约：**
```
FUNCTION isBugCondition(input)
  INPUT: input of type TableOperation (create | edit | queryDeployedTables | togglePortalVisibility | renderActions)
  OUTPUT: boolean

  IF input.operation == "create" THEN
    RETURN input.resultStatus == DRAFT  // 应为 INIT
  
  IF input.operation == "edit" AND input.currentStatus == DEPLOYED THEN
    RETURN input.resultStatus == DRAFT  // 应为 UPDATED
  
  IF input.operation == "queryDeployedTables" THEN
    RETURN input.queryStatuses == [DEPLOYED]  // 应包含 UPDATED
           OR input.includesDisabledTables == true  // 应过滤 disabled
  
  IF input.operation == "togglePortalVisibility" AND input.tableEnabled == false THEN
    RETURN input.switchDisabled == false  // 应禁用开关
  
  IF input.operation == "renderActions" THEN
    RETURN input.buttonOrder != [Edit, Delete, Deploy, Rollback, Version, Access]
  
  RETURN false
END FUNCTION
```

### 示例

- 用户创建新表 "customer_orders"，期望状态为 `INIT`，实际状态为 `DRAFT`
- 用户编辑已部署表 "product_catalog"（当前状态 `DEPLOYED`），期望状态变为 `UPDATED`，实际变为 `DRAFT`，该表从 Table Data 页面消失
- Table Data 页面加载时，状态为 `UPDATED` 的表 "product_catalog" 不在列表中，因为仅查询了 `DEPLOYED`
- disabled 表 "archived_data" 仍然出现在 Table Data 左侧面板中
- disabled 表的 Portal Visibility 开关仍可点击切换

## 期望行为

### Preservation 需求

**不变行为：**
- 对 `INIT` 状态的表执行首次部署，正常创建物理表并将状态更新为 `DEPLOYED`
- 状态为 `DEPLOYED` 且未被编辑的表，继续在 Table Data 中正常显示
- Rollback 操作继续正常回滚到指定版本，状态设为 `ROLLBACK`
- enabled 为 `true` 的表，数据正常显示，Portal Visibility 开关保持可编辑
- Table Data 中的数据 CRUD 操作不受影响
- 创建、删除表和版本历史管理不受影响

**范围：**
所有不涉及以下操作的输入不受本次修复影响：
- 创建表时的初始状态设置
- 编辑已部署表时的状态转换
- Table Data 的已部署表查询逻辑
- disabled 表的可见性过滤
- Portal Visibility 开关的禁用控制
- Actions 按钮排序

## 假设根因

基于 bug 分析，最可能的问题如下：

1. **枚举缺失**：`RelationTableStatus` 枚举中缺少 `INIT` 和 `UPDATED` 状态值，导致无法表达完整的生命周期
   - 当前仅有 `DRAFT`、`DEPLOYED`、`ROLLBACK`
   - 创建和编辑操作只能使用 `DRAFT` 作为非部署状态

2. **创建逻辑硬编码**：`RelationTableStructureServiceImpl.createTable()` 中将新表状态硬编码为 `RelationTableStatus.DRAFT`
   - 位于 `createTable` 方法第 52 行：`.status(RelationTableStatus.DRAFT)`

3. **编辑逻辑无条件重置**：`RelationTableStructureServiceImpl.updateTable()` 中无条件将状态设为 `DRAFT`
   - 位于 `updateTable` 方法第 103 行：`tableDefinition.setStatus(RelationTableStatus.DRAFT)`
   - 未区分当前状态是否为 `DEPLOYED`

4. **查询条件不完整**：`RelationTableDataServiceImpl.getDeployedTables()` 仅查询 `DEPLOYED` 状态
   - `tableDefinitionRepository.findByStatus(RelationTableStatus.DEPLOYED)`
   - 未包含 `UPDATED` 状态，也未过滤 `enabled = false` 的表

5. **数据操作状态校验过严**：`getDeployedTableDefinition()` 方法严格检查 `status == DEPLOYED`
   - 状态为 `UPDATED` 的表无法执行数据操作

6. **前端缺少禁用控制**：Portal Visibility 开关未根据 `enabled` 字段禁用
   - `structure/index.vue` 中的 Portal Visibility `el-switch` 缺少 `:disabled` 属性

7. **前端按钮顺序不正确**：Actions 列中按钮顺序为 Access → Deploy → Versions → Edit → Rollback → Delete
   - 期望顺序为 Edit → Delete → Deploy → Rollback → Version → Access

8. **前端类型定义不完整**：`relationTable.ts` 中 `RelationTableStatus` 类型未包含 `INIT` 和 `UPDATED`

9. **Repository 查询方法缺失**：`RelationTableDefinitionRepository` 缺少按多状态 + enabled 条件查询的方法

## 正确性属性

Property 1: Bug 条件 - 表状态生命周期正确性

_对于任意_ 创建表操作，修复后的 `createTable` 函数 SHALL 将表状态设为 `INIT`；_对于任意_ 编辑操作，当表当前状态为 `DEPLOYED` 时，修复后的 `updateTable` 函数 SHALL 将状态设为 `UPDATED` 而非 `DRAFT`；_对于任意_ Table Data 查询，修复后的 `getDeployedTables` 函数 SHALL 返回状态为 `DEPLOYED` 或 `UPDATED` 且 `enabled = true` 的表。

**验证需求: 2.1, 2.2, 2.3, 2.4**

Property 2: Preservation - 非 Bug 条件行为保持不变

_对于任意_ 不涉及 Bug 条件的输入（部署操作、回滚操作、数据 CRUD、enabled 表的正常显示），修复后的代码 SHALL 产生与原始代码完全相同的结果，保持所有现有功能不变。

**验证需求: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## 修复实现

### 所需变更

假设根因分析正确：

**文件**: `backend/platform-common/src/main/java/com/platform/common/enums/RelationTableStatus.java`

**变更 1 - 新增枚举值**：
- 在枚举中添加 `INIT("INIT", "Init")` 和 `UPDATED("UPDATED", "Updated")`
- `INIT` 表示新创建但从未部署的表
- `UPDATED` 表示已部署后被编辑、尚未重新部署的表

**文件**: `backend/admin-center/src/main/java/com/admin/service/impl/RelationTableStructureServiceImpl.java`

**变更 2 - 创建表状态改为 INIT**：
- `createTable()` 方法中将 `.status(RelationTableStatus.DRAFT)` 改为 `.status(RelationTableStatus.INIT)`

**变更 3 - 编辑表状态条件判断**：
- `updateTable()` 方法中，当 `currentStatus == DEPLOYED` 时设为 `UPDATED`，其他情况保持原状态不变（`INIT` 编辑后仍为 `INIT`，`UPDATED` 编辑后仍为 `UPDATED`）

**文件**: `backend/admin-center/src/main/java/com/admin/service/impl/RelationTableDataServiceImpl.java`

**变更 4 - 查询已部署表包含 UPDATED 并过滤 disabled**：
- `getDeployedTables()` 方法改为查询状态为 `DEPLOYED` 或 `UPDATED` 且 `enabled = true` 的表

**变更 5 - 数据操作允许 UPDATED 状态**：
- `getDeployedTableDefinition()` 方法中，将状态校验从 `status != DEPLOYED` 改为 `status != DEPLOYED && status != UPDATED`

**文件**: `backend/admin-center/src/main/java/com/admin/repository/RelationTableDefinitionRepository.java`

**变更 6 - 新增 Repository 查询方法**：
- 添加 `findByStatusInAndEnabledTrue(List<RelationTableStatus> statuses)` 方法
- 更新 `findPortalVisibleAndDeployed()` 查询以包含 `UPDATED` 状态并过滤 disabled

**文件**: `backend/admin-center/src/main/java/com/admin/entity/RelationTableDefinition.java`

**变更 7 - 默认状态改为 INIT**：
- 将 `@Builder.Default` 的默认状态从 `DRAFT` 改为 `INIT`

**文件**: `frontend/admin-center/src/api/relationTable.ts`

**变更 8 - 前端类型更新**：
- `RelationTableStatus` 类型添加 `'INIT'` 和 `'UPDATED'`

**文件**: `frontend/admin-center/src/views/relation-table/structure/index.vue`

**变更 9 - Portal Visibility 禁用控制**：
- Portal Visibility 的 `el-switch` 添加 `:disabled="!row.enabled"` 属性

**变更 10 - Actions 按钮重排序**：
- 将按钮顺序调整为：Edit → Delete → Deploy → Rollback → Version → Access

**变更 11 - 状态标签映射更新**：
- `statusTagType` 函数添加 `INIT` 和 `UPDATED` 的标签颜色映射

## 测试策略

### 验证方法

测试策略分两阶段：首先在未修复代码上复现 bug 以确认根因，然后验证修复的正确性和行为保持。

### 探索性 Bug 条件检查

**目标**：在实施修复前复现 bug，确认或否定根因分析。如果否定，需要重新假设。

**测试计划**：编写单元测试验证当前代码中的状态设置逻辑，在未修复代码上运行以观察失败。

**测试用例**：
1. **创建表状态测试**：调用 `createTable()`，断言返回状态为 `INIT`（在未修复代码上将失败，返回 `DRAFT`）
2. **编辑已部署表状态测试**：对 `DEPLOYED` 状态的表调用 `updateTable()`，断言状态为 `UPDATED`（在未修复代码上将失败，返回 `DRAFT`）
3. **Table Data 查询测试**：调用 `getDeployedTables()`，断言包含 `UPDATED` 状态的表（在未修复代码上将失败）
4. **Disabled 表过滤测试**：调用 `getDeployedTables()`，断言不包含 `enabled=false` 的表（在未修复代码上将失败）

**预期反例**：
- `createTable()` 返回 `DRAFT` 而非 `INIT`
- `updateTable()` 对 `DEPLOYED` 表返回 `DRAFT` 而非 `UPDATED`
- `getDeployedTables()` 不包含 `UPDATED` 状态的表
- 可能原因：枚举缺失、状态设置硬编码、查询条件不完整

### Fix 检查

**目标**：验证对于所有满足 bug 条件的输入，修复后的函数产生期望行为。

**伪代码：**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedFunction(input)
  ASSERT expectedBehavior(result)
END FOR
```

### Preservation 检查

**目标**：验证对于所有不满足 bug 条件的输入，修复后的函数产生与原始函数相同的结果。

**伪代码：**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) = fixedFunction(input)
END FOR
```

**测试方法**：推荐使用基于属性的测试（Property-Based Testing）进行 Preservation 检查，因为：
- 自动生成大量测试用例覆盖输入域
- 捕获手动单元测试可能遗漏的边界情况
- 对非 bug 输入的行为不变性提供强保证

**测试计划**：先在未修复代码上观察非 bug 输入的行为，然后编写基于属性的测试捕获该行为。

**测试用例**：
1. **部署流程保持**：观察 `INIT` 状态表的部署流程在未修复代码上正常工作，验证修复后继续正常
2. **回滚流程保持**：观察回滚操作在未修复代码上正常工作，验证修复后继续正常
3. **数据 CRUD 保持**：观察 `DEPLOYED` 状态表的数据操作在未修复代码上正常工作，验证修复后继续正常
4. **Enabled 表显示保持**：观察 `enabled=true` 的表在未修复代码上正常显示，验证修复后继续正常

### 单元测试

- 测试 `RelationTableStatus` 枚举包含 `INIT` 和 `UPDATED`，`fromCode()` 方法正确解析
- 测试 `createTable()` 返回状态为 `INIT`
- 测试 `updateTable()` 对 `DEPLOYED` 表返回 `UPDATED`，对 `INIT` 表保持 `INIT`，对 `UPDATED` 表保持 `UPDATED`
- 测试 `getDeployedTables()` 返回 `DEPLOYED` 和 `UPDATED` 且 `enabled=true` 的表
- 测试 `getDeployedTableDefinition()` 允许 `DEPLOYED` 和 `UPDATED` 状态

### 基于属性的测试

- 生成随机表状态和操作序列，验证状态转换符合生命周期规则
- 生成随机 enabled/disabled 组合，验证 Table Data 查询结果的过滤正确性
- 生成随机非 bug 输入，验证修复前后行为一致

### 集成测试

- 测试完整生命周期流程：创建(INIT) → 部署(DEPLOYED) → 编辑(UPDATED) → 重新部署(DEPLOYED)
- 测试 disabled 表在 Table Data 和 Admin Center 中不可见
- 测试 Portal Visibility 开关在 disabled 状态下不可编辑
