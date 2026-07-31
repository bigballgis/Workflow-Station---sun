# 实施计划

- [x] 1. 编写 Bug Condition 探索测试
  - **Property 1: Bug Condition** - listMappings 返回所有活跃角色而非仅已映射角色
  - **重要**: 此属性测试必须在修复代码之前编写
  - **目标**: 通过反例证明 Bug 存在
  - **Scoped PBT 方法**: 使用 jqwik 属性测试，构造场景：存在多个活跃系统角色，但仅部分角色在 `bi_rbac_mapping` 表中有映射记录
  - Bug Condition `C(X)`: 存在活跃系统角色 R，使得 `bi_rbac_mapping` 表中不存在 `sys_role_id = R.id` 的记录
  - 测试断言（期望行为）: `listMappings()` 返回的结果集中不应包含未映射的角色 R
  - 在未修复代码上运行测试 — 预期测试 **失败**（这证实了 Bug 存在：当前 `listMappings` 基于 `roleRepository.findAllActive()` 返回所有角色）
  - 记录反例（例如："3 个活跃角色中仅 1 个有映射，但 listMappings 返回了全部 3 个"）
  - 测试文件: `backend/admin-center/src/test/java/com/admin/bi/service/BiRbacMappingListBugConditionPropertyTest.java`
  - 使用 jqwik `@Property` + Mockito mock，参考现有 `BiRbacMappingServicePropertyTest.java` 的模式
  - 测试完成标准：测试已编写、已运行、失败已记录
  - _Requirements: 1.1, 1.4, 2.1, 2.4_

- [x] 2. 编写 Preservation 属性测试（在修复之前）
  - **Property 2: Preservation** - 已有功能行为保持不变
  - **重要**: 遵循观察优先方法论
  - 观察未修复代码在非 Bug 条件输入下的行为：
    - 观察: `syncSupersetRoles()` 正常调用同步组件并返回同步摘要
    - 观察: `updateMapping(sysRoleId, request)` 全量替换映射，仅允许 ACTIVE 状态的 Superset 角色
    - 观察: `getEffectiveSupersetRoleIds(sysRoleIds)` 返回 ACTIVE 状态 Superset 角色 ID 的去重列表
    - 观察: `listMappings` 对已映射角色正确返回其 Superset 角色列表和最后更新时间
  - 编写属性测试验证以上行为在非 Bug 条件下保持不变：
    - P2a: 对于所有已映射的系统角色，`listMappings` 返回的 supersetRoles 与 `bi_rbac_mapping` 表中的记录一致
    - P2b: `updateMapping` 全量替换语义不变（已有 Property 15 覆盖，此处验证不受影响）
    - P2c: `getEffectiveSupersetRoleIds` 仅返回 ACTIVE 角色（已有 Property 16 覆盖，此处验证不受影响）
  - 在未修复代码上运行测试 — 预期测试 **通过**（确认基线行为）
  - 测试文件: `backend/admin-center/src/test/java/com/admin/bi/service/BiRbacMappingPreservationPropertyTest.java`
  - 测试完成标准：测试已编写、已运行、在未修复代码上通过
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. 修复 RBAC Mapping 手动创建模式

  - [x] 3.1 后端：修改 `listMappings` 查询逻辑，仅返回已映射角色
    - 修改 `BiRbacMappingServiceImpl.listMappings()`：先查询 `bi_rbac_mapping` 表获取所有已映射的 `sys_role_id`，再根据这些 ID 查询系统角色信息
    - 在 `BiRbacMappingRepository` 中添加查询方法：获取所有不重复的 `sys_role_id`
    - 在 `RoleRepository` 中添加按 ID 列表查询的方法（如 `findByIdIn`）
    - 筛选逻辑改为在已映射角色中按 roleName/roleType 过滤
    - _Bug_Condition: C(X) = 存在活跃角色 R 且 bi_rbac_mapping 中无 sys_role_id = R.id 的记录_
    - _Expected_Behavior: listMappings() 仅返回 bi_rbac_mapping 中存在记录的角色_
    - _Preservation: updateMapping 全量替换语义不变; getEffectiveSupersetRoleIds 行为不变_
    - _Requirements: 1.1, 1.4, 2.1, 2.4_

  - [x] 3.2 后端：新增创建映射 API (`POST /bi/rbac/mappings`)
    - 在 `BiRbacMappingService` 接口中添加 `createMapping(RbacMappingCreateRequest)` 方法
    - 创建 `RbacMappingCreateRequest` DTO（包含 `sysRoleId` 和 `supersetRoleIds`）
    - 实现逻辑：校验 sysRoleId 对应的角色存在且活跃、校验该角色尚未创建映射、校验 supersetRoleIds 均为 ACTIVE 状态、批量插入映射记录
    - 在 `BiRbacMappingController` 中添加 `POST /bi/rbac/mappings` 端点
    - _Requirements: 2.2_

  - [x] 3.3 后端：新增删除映射 API (`DELETE /bi/rbac/mappings/{sysRoleId}`)
    - 在 `BiRbacMappingService` 接口中添加 `deleteMapping(String sysRoleId)` 方法
    - 实现逻辑：删除该 sysRoleId 的所有映射记录（`mappingRepository.deleteBySysRoleId`）
    - 在 `BiRbacMappingController` 中添加 `DELETE /bi/rbac/mappings/{sysRoleId}` 端点
    - _Requirements: 2.3_

  - [x] 3.4 后端：新增查询未映射角色 API (`GET /bi/rbac/unmapped-roles`)
    - 在 `BiRbacMappingService` 中添加 `listUnmappedRoles()` 方法
    - 实现逻辑：查询所有活跃角色，排除已在 `bi_rbac_mapping` 中存在记录的角色
    - 在 `BiRbacMappingController` 中添加 `GET /bi/rbac/unmapped-roles` 端点
    - _Requirements: 2.2_

  - [x] 3.5 前端：API 层添加新接口定义
    - 在 `biManagement.ts` 中添加 `RbacMappingCreateRequest` 类型
    - 添加 `createMapping`、`deleteMapping`、`listUnmappedRoles` API 方法
    - _Requirements: 2.2, 2.3_

  - [x] 3.6 前端：RbacMapping.vue 添加"新增映射"功能
    - 在页面头部 header-actions 区域添加"新增映射"按钮
    - 创建新增映射对话框：系统角色下拉选择（数据来源 `listUnmappedRoles`）+ Superset 角色多选（仅 ACTIVE）
    - 提交时调用 `createMapping` API，成功后刷新列表
    - _Requirements: 2.2_

  - [x] 3.7 前端：RbacMapping.vue 添加"删除映射"功能
    - 在表格 Actions 列添加"删除"按钮
    - 点击后弹出确认对话框（ElMessageBox.confirm）
    - 确认后调用 `deleteMapping` API，成功后刷新列表
    - _Requirements: 2.3_

  - [x] 3.8 验证 Bug Condition 探索测试现在通过
    - **Property 1: Expected Behavior** - listMappings 仅返回已映射角色
    - **重要**: 重新运行任务 1 中的同一测试，不要编写新测试
    - 任务 1 的测试编码了期望行为：listMappings 不应返回未映射角色
    - 运行 `BiRbacMappingListBugConditionPropertyTest`
    - **预期结果**: 测试 **通过**（确认 Bug 已修复）
    - _Requirements: 2.1, 2.4_

  - [x] 3.9 验证 Preservation 属性测试仍然通过
    - **Property 2: Preservation** - 已有功能行为保持不变
    - **重要**: 重新运行任务 2 中的同一测试，不要编写新测试
    - 运行 `BiRbacMappingPreservationPropertyTest`
    - **预期结果**: 测试 **通过**（确认无回归）
    - 同时运行现有的 `BiRbacMappingServicePropertyTest`（Property 15、16）确认不受影响

- [x] 4. 检查点 - 确保所有测试通过
  - 运行所有相关测试，确保全部通过
  - 如有问题请向用户确认
