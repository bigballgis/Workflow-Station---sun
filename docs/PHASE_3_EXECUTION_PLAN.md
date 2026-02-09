# Phase 3-9 Execution Plan

## 编译错误分析 (100个错误)

### 错误分类

#### 1. 类型转换错误 (约30个)
- String vs RoleType enum 比较
- String vs VirtualGroupType enum 比较
- String vs BusinessUnitStatus enum 比较
- LocalDateTime vs Instant 转换

**修复方案**: 使用 EntityTypeConverter

#### 2. 缺失的Helper方法调用 (约25个)
- `.isBusinessRole()` 在 String 上调用
- `.getMemberCount()` 在实体上调用
- `.isValid()` 在 VirtualGroup 上调用
- `.getResource()` / `.getAction()` 在 Permission 上调用

**修复方案**: 使用 RoleHelper, VirtualGroupHelper, BusinessUnitHelper, PermissionHelper

#### 3. Builder模式错误 (约20个)
- `.user()`, `.role()`, `.virtualGroup()`, `.permission()` 方法不存在
- 需要使用 `.userId()`, `.roleId()`, `.virtualGroupId()`, `.permissionId()`

**修复方案**: 更新所有builder调用使用ID字段

#### 4. 实体关系访问错误 (约20个)
- `.getUser()`, `.getRole()`, `.getBusinessUnit()` 方法不存在
- `.getApplicant()`, `.getApprover()`, `.getPermission()` 方法不存在

**修复方案**: 在服务层显式获取相关实体，更新DTO的fromEntity方法

#### 5. 导入错误 (约5个)
- UserBusinessUnit 从 com.admin.entity 导入
- UserStatus.DISABLED 不存在

**修复方案**: 更新导入，使用 UserStatus.INACTIVE

## 执行顺序

### 优先级1: 基础修复 (立即执行)
1. 修复所有导入错误 (UserManagerComponent, VirtualGroupManagerComponent)
2. 修复 UserStatus.DISABLED → INACTIVE (UserManagerComponent, AuthServiceImpl)
3. 修复 EntityTypeConverter 使用 (所有类型转换)

### 优先级2: Helper服务集成
4. 修复 RoleHelper 使用 (所有 isBusinessRole 调用)
5. 修复 VirtualGroupHelper 使用 (getMemberCount, isValid)
6. 修复 BusinessUnitHelper 使用 (getMemberCount)
7. 修复 PermissionHelper 使用 (getResource, getAction)

### 优先级3: Builder模式修复
8. 修复所有 builder 调用使用ID字段

### 优先级4: DTO修复
9. 更新所有 DTO 的 fromEntity 方法

### 优先级5: 服务层修复
10. 更新所有服务方法显式获取相关实体

## 文件修复清单

### 需要修复的文件 (按优先级)

#### 高优先级 (阻塞编译)
1. `UserManagerComponent.java` - 导入, UserStatus, getUserRoles
2. `AuthServiceImpl.java` - UserStatus.DISABLED
3. `VirtualGroupManagerComponent.java` - 类型转换, builder
4. `OrganizationManagerComponent.java` - BusinessUnitStatus转换

#### 中优先级 (类型转换)
5. `VirtualGroupResult.java` - 类型转换
6. `VirtualGroupInfo.java` - 类型转换, helper
7. `BusinessUnitTree.java` - 类型转换, helper
8. `DepartmentTree.java` - 类型转换

#### 中优先级 (Helper服务)
9. `UserPermissionService.java` - RoleHelper
10. `FunctionUnitAccessService.java` - RoleHelper
11. `BusinessUnitRoleService.java` - RoleHelper
12. `VirtualGroupRoleService.java` - RoleHelper
13. `TaskAssignmentQueryService.java` - RoleHelper
14. `DeveloperPermissionService.java` - RoleHelper
15. `PermissionRequestService.java` - RoleHelper

#### 中优先级 (Builder)
16. `RoleMemberManagerComponent.java` - builder
17. `MemberManagementService.java` - builder
18. `PermissionDelegationComponent.java` - builder
19. `PermissionConflictComponent.java` - builder, PermissionHelper

#### 中优先级 (DTO)
20. `PermissionRequestInfo.java` - fromEntity
21. `PermissionDelegationResult.java` - fromEntity
22. `ConflictDetectionResult.java` - fromEntity
23. `UserBusinessUnitRoleInfo.java` - fromEntity
24. `VirtualGroupMemberInfo.java` - fromEntity

#### 低优先级 (其他)
25. `UserBusinessUnitService.java` - 方法引用
26. `ApproverService.java` - 方法引用
27. `UserController.java` - 类型转换, helper
28. `DepartmentRoleTaskServiceImpl.java` - getRole
29. `VirtualGroupTaskServiceImpl.java` - isValid
30. `RolePermissionManagerComponent.java` - LocalDateTime转换

## 预计工作量
- 总文件数: 30个
- 预计修复时间: 每个文件5-10分钟
- 总时间: 2.5-5小时

## 成功标准
- ✅ 零编译错误
- ✅ 所有文件使用helper服务
- ✅ 所有builder使用ID字段
- ✅ 所有DTO正确映射
- ✅ 所有类型转换使用EntityTypeConverter
