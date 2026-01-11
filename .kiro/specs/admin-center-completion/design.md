# Admin Center 功能完善设计文档

## 概述

本设计文档描述了 Admin Center 前端 API 层和视图组件的完善方案，确保所有功能都与后端 RESTful API 完全对接，不使用任何 mock 数据。

### 设计目标

- **完整对接**: 所有前端组件与后端 API 完全对接
- **类型安全**: 所有 TypeScript 类型正确定义
- **错误处理**: 统一的错误处理和用户提示
- **代码质量**: 修复所有 TypeScript 编译错误

## 架构设计

### API 层架构

```
frontend/admin-center/src/api/
├── request.ts          # 已存在 - HTTP 请求封装
├── auth.ts             # 已存在 - 认证相关
├── user.ts             # 已存在 - 用户管理
├── organization.ts     # 已存在 - 组织架构
├── role.ts             # 已存在 - 角色权限
├── monitor.ts          # 已存在 - 系统监控
├── audit.ts            # 已存在 - 审计日志
├── dictionary.ts       # 新增 - 数据字典
├── config.ts           # 新增 - 系统配置
├── virtualGroup.ts     # 新增 - 虚拟组管理
└── functionUnit.ts     # 新增 - 功能单元
```

## 组件和接口

### 1. 数据字典 API (dictionary.ts)

```typescript
// 类型定义
export interface Dictionary {
  id: string
  name: string
  code: string
  type: 'SYSTEM' | 'BUSINESS' | 'CUSTOM'
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  createdAt: string
  updatedAt: string
}

export interface DictionaryItem {
  id: string
  dictionaryId: string
  code: string
  nameEn: string
  nameZhCn?: string
  nameZhTw?: string
  parentItemId?: string
  sortOrder: number
  status: 'ACTIVE' | 'INACTIVE'
  validFrom?: string
  validTo?: string
  extraData?: Record<string, any>
}

export interface DictionaryCreateRequest {
  name: string
  code: string
  type: string
  description?: string
}

export interface DictionaryItemRequest {
  code: string
  nameEn: string
  nameZhCn?: string
  nameZhTw?: string
  parentItemId?: string
  sortOrder?: number
}

// API 接口
export const dictionaryApi = {
  list: (type?: string, status?: string) => 
    get<Dictionary[]>('/dictionaries', { params: { type, status } }),
  
  getById: (id: string) => 
    get<Dictionary>(`/dictionaries/${id}`),
  
  getByCode: (code: string) => 
    get<Dictionary>(`/dictionaries/code/${code}`),
  
  create: (data: DictionaryCreateRequest) => 
    post<Dictionary>('/dictionaries', data),
  
  update: (id: string, data: Partial<DictionaryCreateRequest>) => 
    put<Dictionary>(`/dictionaries/${id}`, data),
  
  delete: (id: string) => 
    del<void>(`/dictionaries/${id}`),
  
  activate: (id: string) => 
    post<Dictionary>(`/dictionaries/${id}/activate`),
  
  deactivate: (id: string) => 
    post<Dictionary>(`/dictionaries/${id}/deactivate`),
  
  // 字典项
  getItems: (dictionaryId: string) => 
    get<DictionaryItem[]>(`/dictionaries/${dictionaryId}/items`),
  
  getValidItems: (dictionaryId: string) => 
    get<DictionaryItem[]>(`/dictionaries/${dictionaryId}/items/valid`),
  
  createItem: (dictionaryId: string, data: DictionaryItemRequest) => 
    post<DictionaryItem>(`/dictionaries/${dictionaryId}/items`, data),
  
  updateItem: (itemId: string, data: DictionaryItemRequest) => 
    put<DictionaryItem>(`/dictionaries/items/${itemId}`, data),
  
  deleteItem: (itemId: string) => 
    del<void>(`/dictionaries/items/${itemId}`),
  
  // 版本管理
  getVersionHistory: (dictionaryId: string) => 
    get<any[]>(`/dictionaries/${dictionaryId}/versions`),
  
  rollback: (dictionaryId: string, version: number) => 
    post<Dictionary>(`/dictionaries/${dictionaryId}/rollback/${version}`)
}
```

### 2. 系统配置 API (config.ts)

```typescript
export interface SystemConfig {
  id: string
  configKey: string
  configValue: string
  configType: string
  category: string
  environment: string
  description?: string
  isEncrypted: boolean
  version: number
  updatedAt: string
  updatedBy: string
}

export interface ConfigHistory {
  id: string
  configKey: string
  oldValue: string
  newValue: string
  changedBy: string
  changedAt: string
  changeReason?: string
}

export interface ConfigCreateRequest {
  configKey: string
  configValue: string
  configType: string
  category: string
  environment?: string
  description?: string
  isEncrypted?: boolean
}

export interface ConfigUpdateRequest {
  configValue: string
  changeReason?: string
}

export interface ImpactAssessment {
  affectedServices: string[]
  requiresRestart: boolean
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  recommendations: string[]
}

export const configApi = {
  getAll: () => 
    get<SystemConfig[]>('/configs'),
  
  getByKey: (key: string) => 
    get<SystemConfig>(`/configs/${key}`),
  
  getByCategory: (category: string) => 
    get<SystemConfig[]>(`/configs/category/${category}`),
  
  getByEnvironment: (env: string) => 
    get<SystemConfig[]>(`/configs/environment/${env}`),
  
  create: (data: ConfigCreateRequest) => 
    post<SystemConfig>('/configs', data),
  
  update: (key: string, data: ConfigUpdateRequest) => 
    put<SystemConfig>(`/configs/${key}`, data),
  
  delete: (key: string) => 
    del<void>(`/configs/${key}`),
  
  getHistory: (key: string, page = 0, size = 20) => 
    get<any>(`/configs/${key}/history`, { params: { page, size } }),
  
  rollback: (key: string, version: number) => 
    post<SystemConfig>(`/configs/${key}/rollback/${version}`),
  
  assessImpact: (key: string, newValue: string) => 
    post<ImpactAssessment>(`/configs/${key}/assess-impact`, newValue),
  
  compareEnvironments: (sourceEnv: string, targetEnv: string) => 
    get<any>(`/configs/compare/${sourceEnv}/${targetEnv}`),
  
  syncConfigs: (sourceEnv: string, targetEnv: string, keys: string[]) => 
    post<any>(`/configs/sync/${sourceEnv}/${targetEnv}`, keys)
}
```

### 3. 虚拟组 API (virtualGroup.ts)

```typescript
export interface VirtualGroup {
  id: string
  name: string
  type: 'PROJECT' | 'WORK' | 'TEMPORARY' | 'TASK_HANDLER'
  description?: string
  validFrom?: string
  validTo?: string
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
  memberCount: number
  createdAt: string
  updatedAt: string
}

export interface VirtualGroupMember {
  id: string
  groupId: string
  userId: string
  username: string
  fullName: string
  role: 'LEADER' | 'MEMBER'
  joinedAt: string
}

export interface GroupTask {
  id: string
  taskId: string
  taskName: string
  processName: string
  assignedAt: string
  status: 'PENDING' | 'CLAIMED' | 'COMPLETED'
  claimedBy?: string
  claimedAt?: string
}

export interface VirtualGroupCreateRequest {
  name: string
  type: string
  description?: string
  validFrom?: string
  validTo?: string
}

export interface MemberRequest {
  userId: string
  role?: string
}

export const virtualGroupApi = {
  list: (type?: string, status?: string) => 
    get<VirtualGroup[]>('/virtual-groups', { params: { type, status } }),
  
  getById: (id: string) => 
    get<VirtualGroup>(`/virtual-groups/${id}`),
  
  create: (data: VirtualGroupCreateRequest) => 
    post<any>('/virtual-groups', data),
  
  update: (id: string, data: VirtualGroupCreateRequest) => 
    put<any>(`/virtual-groups/${id}`, data),
  
  delete: (id: string) => 
    del<void>(`/virtual-groups/${id}`),
  
  activate: (id: string) => 
    post<any>(`/virtual-groups/${id}/activate`),
  
  deactivate: (id: string) => 
    post<any>(`/virtual-groups/${id}/deactivate`),
  
  // 成员管理
  getMembers: (groupId: string) => 
    get<VirtualGroupMember[]>(`/virtual-groups/${groupId}/members`),
  
  addMember: (groupId: string, data: MemberRequest) => 
    post<any>(`/virtual-groups/${groupId}/members`, data),
  
  removeMember: (groupId: string, userId: string) => 
    del<any>(`/virtual-groups/${groupId}/members/${userId}`),
  
  updateMemberRole: (groupId: string, userId: string, data: MemberRequest) => 
    put<any>(`/virtual-groups/${groupId}/members/${userId}/role`, data),
  
  // 任务管理
  getTasks: (groupId: string) => 
    get<GroupTask[]>(`/virtual-groups/${groupId}/tasks`),
  
  getMyGroupTasks: () => 
    get<GroupTask[]>('/virtual-groups/my-tasks'),
  
  claimTask: (groupId: string, taskId: string) => 
    post<void>(`/virtual-groups/${groupId}/tasks/${taskId}/claim`),
  
  delegateTask: (taskId: string, toUserId: string, reason?: string) => 
    post<void>(`/virtual-groups/tasks/${taskId}/delegate`, { toUserId, reason }),
  
  getTaskHistory: (groupId: string, taskId: string) => 
    get<any[]>(`/virtual-groups/${groupId}/tasks/${taskId}/history`)
}
```

### 4. 功能单元 API (functionUnit.ts)

```typescript
export interface FunctionUnit {
  id: string
  name: string
  code: string
  version: string
  description?: string
  status: 'DRAFT' | 'VALIDATED' | 'DEPLOYED' | 'DEPRECATED'
  importedAt?: string
  importedBy?: string
  deployedAt?: string
  deployedBy?: string
  environment?: string
}

export interface Deployment {
  id: string
  functionUnitId: string
  environment: 'DEVELOPMENT' | 'TESTING' | 'STAGING' | 'PRODUCTION'
  strategy: 'FULL' | 'INCREMENTAL' | 'CANARY' | 'BLUE_GREEN'
  status: 'PENDING' | 'APPROVED' | 'EXECUTING' | 'COMPLETED' | 'FAILED' | 'ROLLED_BACK'
  deployedBy: string
  deployedAt?: string
  completedAt?: string
  rollbackReason?: string
}

export interface DeploymentProgress {
  deploymentId: string
  status: string
  progress: number
  currentStep: string
  logs: string[]
  startedAt: string
  estimatedCompletion?: string
}

export interface ImportRequest {
  fileName: string
  fileContent: string
  overwriteExisting?: boolean
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export const functionUnitApi = {
  list: (status?: string, page = 0, size = 20) => 
    get<any>('/function-units', { params: { status, page, size } }),
  
  getById: (id: string) => 
    get<FunctionUnit>(`/function-units/${id}`),
  
  import: (data: ImportRequest) => 
    post<any>('/function-units/import', data),
  
  validate: (data: ImportRequest) => 
    post<ValidationResult>('/function-units/validate', data),
  
  delete: (id: string) => 
    del<void>(`/function-units/${id}`),
  
  validateUnit: (id: string) => 
    post<FunctionUnit>(`/function-units/${id}/validate`),
  
  deprecate: (id: string) => 
    post<FunctionUnit>(`/function-units/${id}/deprecate`),
  
  // 部署管理
  createDeployment: (id: string, environment: string, strategy = 'FULL') => 
    post<Deployment>(`/function-units/${id}/deployments`, null, { 
      params: { environment, strategy } 
    }),
  
  getDeploymentHistory: (id: string) => 
    get<Deployment[]>(`/function-units/${id}/deployments`),
  
  getDeployment: (deploymentId: string) => 
    get<Deployment>(`/function-units/deployments/${deploymentId}`),
  
  executeDeployment: (deploymentId: string) => 
    post<Deployment>(`/function-units/deployments/${deploymentId}/execute`),
  
  rollbackDeployment: (deploymentId: string, reason: string) => 
    post<Deployment>(`/function-units/deployments/${deploymentId}/rollback`, null, { 
      params: { reason } 
    }),
  
  cancelDeployment: (deploymentId: string, reason: string) => 
    post<Deployment>(`/function-units/deployments/${deploymentId}/cancel`, null, { 
      params: { reason } 
    }),
  
  getDeploymentProgress: (deploymentId: string) => 
    get<DeploymentProgress>(`/function-units/deployments/${deploymentId}/progress`),
  
  // 审批管理
  getDeploymentApprovals: (deploymentId: string) => 
    get<any[]>(`/function-units/deployments/${deploymentId}/approvals`),
  
  approveDeployment: (approvalId: string, comment?: string) => 
    post<any>(`/function-units/approvals/${approvalId}/approve`, null, { 
      params: { comment } 
    }),
  
  rejectDeployment: (approvalId: string, comment: string) => 
    post<any>(`/function-units/approvals/${approvalId}/reject`, null, { 
      params: { comment } 
    }),
  
  getPendingApprovals: () => 
    get<any[]>('/function-units/approvals/pending'),
  
  // 版本管理
  getAllVersions: (code: string) => 
    get<FunctionUnit[]>(`/function-units/code/${code}/versions`),
  
  getLatestVersion: (code: string) => 
    get<FunctionUnit>(`/function-units/code/${code}/latest`),
  
  createNewVersion: (id: string, newVersion: string) => 
    post<FunctionUnit>(`/function-units/${id}/new-version`, null, { 
      params: { newVersion } 
    }),
  
  getVersionHistory: (code: string) => 
    get<any[]>(`/function-units/code/${code}/history`)
}
```

## 数据模型

数据模型已在后端定义，前端 TypeScript 接口与后端实体保持一致。

## 正确性属性

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Dictionary API Integration Correctness

*For any* dictionary CRUD operation (create, read, update, delete), the frontend API layer SHALL correctly call the corresponding backend endpoint with proper parameters and handle the response appropriately.

**Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6**

### Property 2: Config API Integration Correctness

*For any* system configuration operation (query, update, rollback), the frontend API layer SHALL correctly call the corresponding backend endpoint and properly display the configuration data and history.

**Validates: Requirements 2.2, 2.3, 2.4, 2.5**

### Property 3: Virtual Group API Integration Correctness

*For any* virtual group operation (CRUD, member management, task operations), the frontend API layer SHALL correctly call the corresponding backend endpoint and maintain data consistency.

**Validates: Requirements 3.2, 3.3, 3.4, 3.5**

### Property 4: Function Unit API Integration Correctness

*For any* function unit operation (import, deploy, rollback), the frontend API layer SHALL correctly call the corresponding backend endpoint and properly display deployment status and progress.

**Validates: Requirements 4.2, 4.3, 4.4, 4.5**

### Property 5: User Department Filter Integration

*For any* user query with department filter, the frontend SHALL correctly load department data and use the selected department ID to filter users.

**Validates: Requirements 6.1, 6.3**

### Property 6: Audit Log Export Integration

*For any* audit log export request, the frontend SHALL correctly call the backend export endpoint with current query conditions and handle file download properly.

**Validates: Requirements 7.1, 7.2**

### Property 7: Dashboard Real Data Integration

*For any* dashboard page load, the frontend SHALL correctly call backend statistics APIs and display real-time data including user counts, department counts, role counts, online users, recent activities, and user trends.

**Validates: Requirements 8.1, 8.2, 8.3, 8.4**

### 5. Dashboard API (dashboard.ts)

```typescript
export interface DashboardStats {
  totalUsers: number
  totalDepartments: number
  totalRoles: number
  onlineUsers: number
  activeProcesses: number
  pendingTasks: number
}

export interface RecentActivity {
  id: string
  action: string
  resourceType: string
  resourceName: string
  username: string
  createdAt: string
}

export interface UserTrend {
  date: string
  activeUsers: number
  newUsers: number
}

export const dashboardApi = {
  getStats: () => 
    get<DashboardStats>('/dashboard/stats'),
  
  getRecentActivities: (limit = 10) => 
    get<RecentActivity[]>('/dashboard/activities', { params: { limit } }),
  
  getUserTrends: (days = 7) => 
    get<UserTrend[]>('/dashboard/user-trends', { params: { days } }),
  
  getSystemHealth: () => 
    get<any>('/dashboard/health')
}
```

### 6. 后端 Dashboard Controller 设计

由于 admin-center 后端目前没有 dashboard 统计接口，需要新增：

```java
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "管理员中心仪表盘统计接口")
public class DashboardController {
    
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    
    @GetMapping("/stats")
    @Operation(summary = "获取统计数据")
    public ResponseEntity<DashboardStats> getStats() {
        DashboardStats stats = DashboardStats.builder()
            .totalUsers(userRepository.count())
            .totalDepartments(departmentRepository.count())
            .totalRoles(roleRepository.count())
            .onlineUsers(getOnlineUserCount())
            .build();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/activities")
    @Operation(summary = "获取最近活动")
    public ResponseEntity<List<RecentActivity>> getRecentActivities(
            @RequestParam(defaultValue = "10") int limit) {
        List<AuditLog> logs = auditLogRepository.findTop10ByOrderByTimestampDesc();
        return ResponseEntity.ok(logs.stream()
            .map(this::toRecentActivity)
            .toList());
    }
    
    @GetMapping("/user-trends")
    @Operation(summary = "获取用户趋势")
    public ResponseEntity<List<UserTrend>> getUserTrends(
            @RequestParam(defaultValue = "7") int days) {
        // 返回最近N天的用户活跃趋势
        return ResponseEntity.ok(calculateUserTrends(days));
    }
}
```

## 错误处理

### 统一错误处理

所有 API 调用使用统一的错误处理机制：

```typescript
// request.ts 中已实现
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)
```

### 组件级错误处理

```typescript
// 组件中的错误处理模式
const handleOperation = async () => {
  try {
    await api.operation()
    ElMessage.success('操作成功')
  } catch (error: any) {
    // 错误已在 interceptor 中处理
    console.error('Operation failed:', error)
  }
}
```

## 测试策略

### 单元测试

- 测试 API 函数的参数传递
- 测试类型定义的正确性
- 测试错误处理逻辑

### 集成测试

- 测试前端组件与后端 API 的完整交互
- 测试数据流的正确性
- 测试错误场景的处理

### TypeScript 类型检查

- 确保所有 API 响应类型与后端返回数据匹配
- 修复所有 TypeScript 编译错误
- 使用严格模式进行类型检查

