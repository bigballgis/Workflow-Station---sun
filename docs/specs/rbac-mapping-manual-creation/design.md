# RBAC Mapping 手动创建模式 Bugfix 设计

## 概述

当前 RBAC Mapping 列表页面会自动展示所有活跃系统角色（通过 `roleRepository.findAllActive()`），无论该角色是否已配置 Superset 角色映射。本次修复将列表查询逻辑改为"仅展示已创建映射的角色"，同时新增"创建映射"和"删除映射"功能，使管理员能够手动管理 RBAC 映射的生命周期。

修复范围涉及三层：
- **后端 Service 层**：修改 `listMappings` 查询逻辑，新增 `createMapping` 和 `deleteMapping` 方法
- **后端 Controller 层**：新增 `POST /bi/rbac/mappings` 和 `DELETE /bi/rbac/mappings/{sysRoleId}` 端点，新增 `GET /bi/rbac/unmapped-roles` 端点
- **前端 Vue 组件**：新增"创建映射"按钮与对话框，新增"删除"按钮，修改 API 调用

## 术语表

- **Bug_Condition (C)**：管理员访问 RBAC Mapping 列表时，系统展示了所有活跃系统角色（包括未创建映射的角色），且缺少创建/删除映射的操作入口
- **Property (P)**：列表仅展示已在 `bi_rbac_mapping` 表中存在记录的系统角色；管理员可手动创建和删除映射
- **Preservation**：Superset 角色同步、映射编辑（全量替换）、Guest Token 有效映射查询、ACTIVE 状态过滤等现有行为不受影响
- **`listMappings()`**：`BiRbacMappingServiceImpl` 中的方法，当前从 `roleRepository.findAllActive()` 查询所有活跃角色并构建响应
- **`bi_rbac_mapping`**：存储 Sys_Role 与 Superset_Role 多对多映射关系的数据库表
- **`sys_roles`**：系统角色表，通过 `RoleRepository` 访问

## Bug 详情

### Bug Condition

当管理员访问 RBAC Mapping 列表页面时，`listMappings()` 方法以 `roleRepository.findAllActive()` 为数据源，遍历所有活跃系统角色并构建响应。这导致：
1. 未创建映射的角色也出现在列表中（Superset Roles 列显示 "-"）
2. 没有入口让管理员为新角色创建映射
3. 没有入口让管理员删除已有映射
4. 筛选范围是所有活跃角色而非已映射角色

**形式化规约：**
```
FUNCTION isBugCondition(request)
  INPUT: request of type HttpRequest (GET /bi/rbac/mappings)
  OUTPUT: boolean

  currentBehavior := listMappings() 基于 roleRepository.findAllActive() 构建结果
  RETURN currentBehavior 包含 bi_rbac_mapping 表中不存在对应 sys_role_id 的角色
         OR 系统不提供 createMapping 操作入口
         OR 系统不提供 deleteMapping 操作入口
END FUNCTION
```


### 示例

- 管理员访问列表 → 系统展示 20 个活跃角色，但仅 3 个有映射记录 → **期望**：仅展示 3 个已映射角色
- 管理员想为"数据分析师"角色创建映射 → 无"新增映射"按钮 → **期望**：点击"新增映射"按钮，选择角色和 Superset 角色后提交
- 管理员想删除"测试角色"的映射 → 无"删除"按钮 → **期望**：点击"删除"按钮并确认后，该角色从列表消失
- 管理员按 roleName 搜索"admin" → 在所有 20 个角色中搜索 → **期望**：仅在已映射的角色中搜索

## 期望行为

### Preservation Requirements

**不变行为：**
- 点击"Sync Superset Roles"按钮触发同步并返回摘要（需求 3.1）
- 编辑已有映射时以全量替换方式保存（需求 3.2）
- 编辑表单中仅允许选择 ACTIVE 状态的 Superset 角色（需求 3.3）
- Guest Token 查询有效映射返回 ACTIVE 状态的 Superset 角色 ID 去重并集（需求 3.4）
- INACTIVE 的 Superset 角色保留映射记录但查询时排除（需求 3.5）


**范围：**
本次修复不涉及以下功能，它们应完全不受影响：
- `syncSupersetRoles()` 同步逻辑
- `updateMapping()` 全量替换逻辑
- `getEffectiveSupersetRoleIds()` 有效映射查询逻辑
- `listSupersetRoles()` Superset 角色列表查询

## 假设根因分析

基于代码分析，问题根因明确：

1. **`listMappings()` 数据源错误**：当前以 `roleRepository.findAllActive()` 为起点遍历所有活跃角色，应改为以 `bi_rbac_mapping` 表中存在的 `sys_role_id` 为起点，仅查询已映射的角色
   - 位置：`BiRbacMappingServiceImpl.listMappings()` 第 1 步
   - 当前代码先查所有活跃角色，再左连接映射数据；应反转为先查映射表中的 distinct sys_role_id，再关联角色信息

2. **缺少创建映射 API**：Controller 仅有 `PUT /bi/rbac/mappings/{sysRoleId}`（更新），没有 `POST /bi/rbac/mappings`（创建）

3. **缺少删除映射 API**：Controller 没有 `DELETE /bi/rbac/mappings/{sysRoleId}` 端点

4. **缺少未映射角色查询 API**：创建映射时需要下拉列表展示"尚未创建映射的活跃角色"，当前没有此端点


## 正确性属性

Property 1: Bug Condition - 列表仅展示已映射角色

_For any_ 对 `GET /bi/rbac/mappings` 的请求，修复后的 `listMappings()` 方法 SHALL 仅返回在 `bi_rbac_mapping` 表中存在至少一条映射记录的系统角色。返回结果中的每个 `sysRoleId` 都必须在 `bi_rbac_mapping` 表中有对应记录。

**Validates: Requirements 2.1, 2.4**

Property 2: Preservation - 现有功能不受影响

_For any_ 不涉及 `listMappings` 查询逻辑变更的操作（Superset 角色同步、映射编辑全量替换、Guest Token 有效映射查询、Superset 角色列表查询），修复后的代码 SHALL 产生与修复前完全相同的结果，保持所有现有功能的正确性。

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**


## 修复实现

### 所需变更

假设根因分析正确，需要以下变更：

**文件**：`backend/admin-center/src/main/java/com/admin/bi/service/impl/BiRbacMappingServiceImpl.java`

**方法**：`listMappings(String roleName, String roleType)`

**具体变更**：
1. **修改查询逻辑**：将数据源从 `roleRepository.findAllActive()` 改为先查询 `bi_rbac_mapping` 表中的 distinct `sys_role_id`，再通过 `roleRepository.findAllById()` 获取角色详情，在已映射角色范围内应用 roleName/roleType 筛选
2. **新增 `createMapping` 方法**：校验角色存在且 ACTIVE、校验无重复映射、校验 Superset 角色 ACTIVE，然后批量插入
3. **新增 `deleteMapping` 方法**：调用 `mappingRepository.deleteBySysRoleId(sysRoleId)`
4. **新增 `listUnmappedRoles` 方法**：查询所有活跃角色与已映射角色 ID 的差集


---

**文件**：`backend/admin-center/src/main/java/com/admin/bi/service/BiRbacMappingService.java`

新增接口方法：
1. `void createMapping(String sysRoleId, RbacMappingUpdateRequest request)`
2. `void deleteMapping(String sysRoleId)`
3. `List<RoleOptionResponse> listUnmappedRoles()`

---

**文件**：`backend/admin-center/src/main/java/com/admin/bi/controller/BiRbacMappingController.java`

新增端点：
1. `POST /bi/rbac/mappings` → 接收 `RbacMappingCreateRequest`，调用 `createMapping`
2. `DELETE /bi/rbac/mappings/{sysRoleId}` → 调用 `deleteMapping`
3. `GET /bi/rbac/unmapped-roles` → 调用 `listUnmappedRoles`

---

**文件**：`backend/admin-center/src/main/java/com/admin/bi/dto/request/RbacMappingCreateRequest.java`（新建）

新建 DTO：`sysRoleId`（String, @NotBlank）、`supersetRoleIds`（List<Integer>, @NotEmpty）


---

**文件**：`backend/admin-center/src/main/java/com/admin/bi/dto/response/RoleOptionResponse.java`（新建）

新建 DTO：`id`、`name`、`code`、`type` 字段，用于未映射角色下拉列表

---

**文件**：`backend/admin-center/src/main/java/com/admin/bi/repository/BiRbacMappingRepository.java`

新增方法：`@Query("SELECT DISTINCT m.sysRoleId FROM BiRbacMapping m") List<String> findDistinctSysRoleIds()`

---

**文件**：`frontend/admin-center/src/api/biManagement.ts`

1. 新增 `RbacMappingCreateRequest` 和 `RoleOptionResponse` 类型
2. 在 `biManagementApi.rbac` 中新增 `createMapping`、`deleteMapping`、`listUnmappedRoles` 方法

---

**文件**：`frontend/admin-center/src/views/bi-management/RbacMapping.vue`

1. Header 区域新增"新增映射"按钮
2. 新增映射对话框（系统角色下拉 + Superset 角色多选）
3. 表格 Actions 列新增"Delete"按钮
4. 删除确认使用 `ElMessageBox.confirm`


## 测试策略

### 验证方法

测试策略分两阶段：首先在未修复代码上验证 bug 存在，然后验证修复后的正确性和行为保持。

### 探索性 Bug Condition 检查

**目标**：在实施修复前，通过测试用例证明 bug 存在，确认或否定根因分析。

**测试计划**：编写单元测试调用 `listMappings()`，验证返回结果是否包含未映射的角色。

**测试用例**：
1. **列表包含未映射角色**：5 个活跃角色仅 2 个有映射，`listMappings(null, null)` 返回 5 条 vs 期望 2 条
2. **筛选范围错误**：按 roleName 筛选时在所有角色中搜索 vs 期望仅在已映射角色中搜索
3. **无创建入口**：Controller 没有 POST 端点
4. **无删除入口**：Controller 没有 DELETE 端点

**预期反例**：`listMappings()` 返回数量等于所有活跃角色数量


### Fix Checking

**目标**：验证修复后 `listMappings()` 仅返回已映射角色。

**伪代码：**
```
FOR ALL request WHERE isBugCondition(request) DO
  result := listMappings_fixed(request.roleName, request.roleType)
  ASSERT result 中每个 sysRoleId 都在 bi_rbac_mapping 表中存在记录
  ASSERT result 不包含 bi_rbac_mapping 表中无记录的角色
END FOR
```

### Preservation Checking

**目标**：验证未变更的功能行为一致。

**伪代码：**
```
FOR ALL operation WHERE NOT isBugCondition(operation) DO
  ASSERT syncSupersetRoles_original() = syncSupersetRoles_fixed()
  ASSERT updateMapping_original(sysRoleId, req) = updateMapping_fixed(sysRoleId, req)
  ASSERT getEffectiveSupersetRoleIds_original(ids) = getEffectiveSupersetRoleIds_fixed(ids)
  ASSERT listSupersetRoles_original() = listSupersetRoles_fixed()
END FOR
```


**测试方法**：推荐属性基测试（PBT），可自动生成大量用例覆盖输入域，捕获边界情况。

**Preservation 测试用例**：
1. **同步功能保持**：`syncSupersetRoles()` 修复前后返回相同结果
2. **编辑功能保持**：`updateMapping()` 修复前后以相同方式全量替换
3. **有效映射查询保持**：`getEffectiveSupersetRoleIds()` 修复前后返回相同 ACTIVE 角色 ID
4. **Superset 角色列表保持**：`listSupersetRoles()` 修复前后返回相同结果

### 单元测试

- `listMappings()` 仅返回已映射角色（无映射时返回空列表）
- `listMappings()` 筛选仅在已映射角色范围内生效
- `createMapping()` 正常创建、重复创建抛异常、角色不存在抛异常、Superset 角色非 ACTIVE 抛异常
- `deleteMapping()` 正常删除、删除后角色从列表消失
- `listUnmappedRoles()` 返回正确的未映射角色列表

### 属性基测试

- 随机角色和映射组合，验证 `listMappings()` 结果始终是已映射角色的子集
- 随机 Superset 角色配置，验证 `createMapping()` 后列表包含新映射
- 随机操作序列，验证 `syncSupersetRoles()` 和 `getEffectiveSupersetRoleIds()` 不受影响

### 集成测试

- 完整流程：创建映射 → 列表出现 → 编辑映射 → 删除映射 → 列表消失
- 创建映射后 roleName/roleType 筛选结果正确
- 创建映射后 `getEffectiveSupersetRoleIds()` 返回正确的 ACTIVE 角色 ID
