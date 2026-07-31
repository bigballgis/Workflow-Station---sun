# 实施计划

- [x] 1. 编写 Bug 条件探索性测试
  - **Property 1: Bug Condition** - 表状态生命周期缺陷验证
  - **CRITICAL**: 此测试必须在未修复代码上 FAIL — 失败即确认 bug 存在
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: 此测试编码了期望行为 — 修复后测试通过即验证修复正确
  - **GOAL**: 产生反例以证明 bug 存在
  - **Scoped PBT Approach**: 针对确定性 bug，将属性范围限定到具体失败场景以确保可复现
  - 在 `backend/admin-center/src/test/java/com/admin/service/impl/` 下创建 `RelationTableStatusBugConditionTest.java`
  - 测试 1: 调用 `createTable()`，断言返回状态为 `INIT`（未修复代码返回 `DRAFT`，测试将失败）
  - 测试 2: 对 `DEPLOYED` 状态的表调用 `updateTable()`，断言状态为 `UPDATED`（未修复代码返回 `DRAFT`，测试将失败）
  - 测试 3: 调用 `getDeployedTables()`，断言包含 `UPDATED` 状态且 `enabled=true` 的表（未修复代码仅查询 `DEPLOYED`，测试将失败）
  - 测试 4: 调用 `getDeployedTables()`，断言不包含 `enabled=false` 的表（未修复代码未过滤 disabled，测试将失败）
  - 测试 5: 对 `UPDATED` 状态的表调用 `getDeployedTableDefinition()`，断言不抛异常（未修复代码仅允许 `DEPLOYED`，测试将失败）
  - _Bug_Condition: isBugCondition(input) where input.operation ∈ {create, edit(DEPLOYED), queryDeployedTables, getDeployedTableDefinition(UPDATED)}_
  - 在未修复代码上运行测试
  - **EXPECTED OUTCOME**: 测试 FAIL（这是正确的 — 证明 bug 存在）
  - 记录发现的反例以理解根因
  - 当测试编写完成、运行完毕且失败已记录后，标记任务完成
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. 编写 Preservation 属性测试（在实施修复之前）
  - **Property 2: Preservation** - 非 Bug 条件行为保持不变
  - **IMPORTANT**: 遵循观察优先方法论
  - 在 `backend/admin-center/src/test/java/com/admin/service/impl/` 下创建 `RelationTableStatusPreservationTest.java`
  - 观察: 在未修复代码上，`DEPLOYED` 且 `enabled=true` 的表在 `getDeployedTables()` 中正常返回
  - 观察: 在未修复代码上，部署操作将 `DRAFT` 状态表正常部署为 `DEPLOYED`
  - 观察: 在未修复代码上，回滚操作正常将状态设为 `ROLLBACK`
  - 观察: 在未修复代码上，`DEPLOYED` 状态表的数据 CRUD 操作正常执行
  - 观察: 在未修复代码上，`enabled=true` 的表 Portal Visibility 开关可编辑
  - 编写属性测试: 对于所有不满足 bug 条件的输入，验证行为与未修复代码一致
  - 属性测试 1: 对于任意 `DEPLOYED` 且 `enabled=true` 的表，`getDeployedTables()` 始终包含该表
  - 属性测试 2: 对于任意 `DEPLOYED` 状态的表，数据查询操作 `getDeployedTableDefinition()` 正常返回
  - 属性测试 3: 对于任意 `INIT` 状态的表调用 `updateTable()`，状态保持不变（不应变为 `DEPLOYED` 或其他）
  - 在未修复代码上运行测试
  - **EXPECTED OUTCOME**: 测试 PASS（确认基线行为已捕获）
  - 当测试编写完成、运行完毕且在未修复代码上通过后，标记任务完成
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 3. 修复表状态生命周期缺陷

  - [x] 3.1 后端枚举与实体变更
    - 在 `RelationTableStatus.java` 中添加 `INIT("INIT", "Init")` 和 `UPDATED("UPDATED", "Updated")` 枚举值
    - 在 `RelationTableDefinition.java` 中将 `@Builder.Default` 默认状态从 `DRAFT` 改为 `INIT`
    - _Bug_Condition: isBugCondition(input) where RelationTableStatus 枚举缺少 INIT 和 UPDATED_
    - _Expected_Behavior: 枚举包含 INIT、UPDATED，fromCode() 正确解析_
    - _Preservation: 现有 DRAFT、DEPLOYED、ROLLBACK 枚举值不受影响_
    - _Requirements: 2.1, 2.2_

  - [x] 3.2 后端服务层逻辑修复
    - 在 `RelationTableStructureServiceImpl.createTable()` 中将 `.status(RelationTableStatus.DRAFT)` 改为 `.status(RelationTableStatus.INIT)`
    - 在 `RelationTableStructureServiceImpl.updateTable()` 中，将无条件 `setStatus(DRAFT)` 改为条件判断：当 `currentStatus == DEPLOYED` 时设为 `UPDATED`，其他状态保持不变
    - 在 `RelationTableDataServiceImpl.getDeployedTables()` 中，将 `findByStatus(DEPLOYED)` 改为 `findByStatusInAndEnabledTrue(List.of(DEPLOYED, UPDATED))`
    - 在 `RelationTableDataServiceImpl.getDeployedTableDefinition()` 中，将 `status != DEPLOYED` 改为 `status != DEPLOYED && status != UPDATED`
    - _Bug_Condition: createTable 硬编码 DRAFT；updateTable 无条件重置 DRAFT；getDeployedTables 仅查询 DEPLOYED；getDeployedTableDefinition 仅允许 DEPLOYED_
    - _Expected_Behavior: createTable 返回 INIT；updateTable(DEPLOYED) 返回 UPDATED；getDeployedTables 返回 DEPLOYED+UPDATED 且 enabled=true；getDeployedTableDefinition 允许 UPDATED_
    - _Preservation: 部署流程、回滚流程、数据 CRUD 不受影响_
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 3.3 后端 Repository 查询方法
    - 在 `RelationTableDefinitionRepository.java` 中添加 `findByStatusInAndEnabledTrue(List<RelationTableStatus> statuses)` 方法
    - 更新 `findPortalVisibleAndDeployed()` 查询以包含 `UPDATED` 状态：`t.status IN ('DEPLOYED', 'UPDATED')`
    - _Bug_Condition: 缺少按多状态 + enabled 条件查询的方法_
    - _Expected_Behavior: 新方法返回指定状态且 enabled=true 的表_
    - _Preservation: 现有 findByStatus 等方法不受影响_
    - _Requirements: 2.3, 2.4_

  - [x] 3.4 前端类型与 UI 修复
    - 在 `frontend/admin-center/src/api/relationTable.ts` 中，`RelationTableStatus` 类型添加 `'INIT' | 'UPDATED'`
    - 在 `frontend/admin-center/src/views/relation-table/structure/index.vue` 中：
      - Portal Visibility 的 `el-switch` 添加 `:disabled="!row.enabled"` 属性
      - Actions 按钮重排序为：Edit → Delete → Deploy → Rollback → Version → Access
      - `statusTagType` 函数添加 `INIT: 'info'` 和 `UPDATED: 'warning'` 映射
    - _Bug_Condition: 前端类型缺少 INIT/UPDATED；Portal Visibility 未禁用；按钮顺序不正确_
    - _Expected_Behavior: 类型完整；disabled 表的 Portal Visibility 不可编辑；按钮顺序正确_
    - _Preservation: 现有 DRAFT/DEPLOYED/ROLLBACK 标签颜色不变；enabled 表的 Portal Visibility 保持可编辑_
    - _Requirements: 2.5, 2.6_

  - [x] 3.5 验证 Bug 条件探索性测试现在通过
    - **Property 1: Expected Behavior** - 表状态生命周期正确性
    - **IMPORTANT**: 重新运行任务 1 中的同一测试 — 不要编写新测试
    - 任务 1 中的测试编码了期望行为
    - 当此测试通过时，确认期望行为已满足
    - 运行任务 1 中的 bug 条件探索性测试
    - **EXPECTED OUTCOME**: 测试 PASS（确认 bug 已修复）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 3.6 验证 Preservation 测试仍然通过
    - **Property 2: Preservation** - 非 Bug 条件行为保持不变
    - **IMPORTANT**: 重新运行任务 2 中的同一测试 — 不要编写新测试
    - 运行任务 2 中的 preservation 属性测试
    - **EXPECTED OUTCOME**: 测试 PASS（确认无回归）
    - 确认修复后所有测试仍然通过（无回归）

- [x] 4. Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。
