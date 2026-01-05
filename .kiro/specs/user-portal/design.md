# 用户门户技术设计

## 1. 系统架构

### 1.1 整体架构
```
┌─────────────────────────────────────────────────────────────┐
│                    用户门户前端 (Vue 3)                       │
├─────────────────────────────────────────────────────────────┤
│  Views层    │  Components层  │  Stores层   │  API层          │
│  - Dashboard │  - TaskList    │  - user     │  - request.ts   │
│  - Process   │  - ProcessDiag │  - task     │  - task.ts      │
│  - Task      │  - FormRender  │  - process  │  - process.ts   │
│  - Delegate  │  - Notification│  - delegate │  - delegate.ts  │
│  - Permission│  - DragGrid    │  - notify   │  - permission.ts│
│  - Notify    │  - ActionBtns  │  - settings │  - notify.ts    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    用户门户后端 (Spring Boot)                 │
├─────────────────────────────────────────────────────────────┤
│  Controller层           │  Service层              │  集成层   │
│  - TaskController       │  - TaskService          │  ↓       │
│  - ProcessController    │  - ProcessService       │          │
│  - DelegateController   │  - DelegateService      │          │
│  - PermissionController │  - PermissionService    │          │
│  - NotifyController     │  - NotifyService        │          │
│  - DashboardController  │  - DashboardService     │          │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ workflow-engine │ │  admin-center   │ │   PostgreSQL    │
│     -core       │ │                 │ │                 │
│ (流程引擎服务)   │ │ (用户/组织服务)  │ │  (用户门户数据)  │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 1.2 模块依赖关系
- 用户门户后端依赖 workflow-engine-core（流程引擎、任务管理）
- 用户门户后端依赖 admin-center（用户认证、组织结构、角色权限）
- 用户门户前端独立部署，通过API网关访问后端服务

## 2. 数据模型设计

### 2.1 用户偏好设置表 (up_user_preference)
```sql
CREATE TABLE up_user_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    theme VARCHAR(20) DEFAULT 'light',           -- 主题：light/dark
    theme_color VARCHAR(20) DEFAULT '#DB0011',   -- 主题色
    font_size VARCHAR(10) DEFAULT 'medium',      -- 字体大小
    layout_density VARCHAR(10) DEFAULT 'normal', -- 布局密度
    language VARCHAR(10) DEFAULT 'zh-CN',        -- 语言
    timezone VARCHAR(50) DEFAULT 'Asia/Shanghai',-- 时区
    date_format VARCHAR(20) DEFAULT 'YYYY-MM-DD',-- 日期格式
    page_size INTEGER DEFAULT 20,                -- 分页大小
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.2 工作台布局表 (up_dashboard_layout)
```sql
CREATE TABLE up_dashboard_layout (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    component_id VARCHAR(50) NOT NULL,           -- 组件ID
    component_type VARCHAR(50) NOT NULL,         -- 组件类型
    grid_x INTEGER NOT NULL,                     -- 网格X位置
    grid_y INTEGER NOT NULL,                     -- 网格Y位置
    grid_w INTEGER NOT NULL,                     -- 网格宽度
    grid_h INTEGER NOT NULL,                     -- 网格高度
    is_visible BOOLEAN DEFAULT TRUE,             -- 是否可见
    config JSONB,                                -- 组件配置
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, component_id)
);
```

### 2.3 通知偏好表 (up_notification_preference)
```sql
CREATE TABLE up_notification_preference (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,      -- 通知类型
    email_enabled BOOLEAN DEFAULT TRUE,          -- 邮件通知
    browser_enabled BOOLEAN DEFAULT TRUE,        -- 浏览器通知
    in_app_enabled BOOLEAN DEFAULT TRUE,         -- 站内通知
    quiet_start_time TIME,                       -- 免打扰开始时间
    quiet_end_time TIME,                         -- 免打扰结束时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, notification_type)
);
```

### 2.4 委托规则表 (up_delegation_rule)
```sql
CREATE TABLE up_delegation_rule (
    id BIGSERIAL PRIMARY KEY,
    delegator_id VARCHAR(64) NOT NULL,           -- 委托人ID
    delegate_id VARCHAR(64) NOT NULL,            -- 被委托人ID
    delegation_type VARCHAR(20) NOT NULL,        -- 委托类型：ALL/PARTIAL/TEMPORARY/URGENT
    process_types JSONB,                         -- 流程类型筛选
    priority_filter JSONB,                       -- 优先级筛选
    start_time TIMESTAMP,                        -- 生效开始时间
    end_time TIMESTAMP,                          -- 生效结束时间
    status VARCHAR(20) DEFAULT 'ACTIVE',         -- 状态：ACTIVE/INACTIVE/EXPIRED
    reason TEXT,                                 -- 委托原因
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.5 权限申请表 (up_permission_request)
```sql
CREATE TABLE up_permission_request (
    id BIGSERIAL PRIMARY KEY,
    applicant_id VARCHAR(64) NOT NULL,           -- 申请人ID
    request_type VARCHAR(20) NOT NULL,           -- 申请类型：FUNCTION/DATA/TEMPORARY
    permissions JSONB NOT NULL,                  -- 申请的权限列表
    reason TEXT NOT NULL,                        -- 申请理由
    valid_from TIMESTAMP,                        -- 有效期开始
    valid_to TIMESTAMP,                          -- 有效期结束
    status VARCHAR(20) DEFAULT 'PENDING',        -- 状态：PENDING/APPROVED/REJECTED
    approver_id VARCHAR(64),                     -- 审批人ID
    approve_time TIMESTAMP,                      -- 审批时间
    approve_comment TEXT,                        -- 审批意见
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2.6 收藏流程表 (up_favorite_process)
```sql
CREATE TABLE up_favorite_process (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,-- 流程定义Key
    display_order INTEGER DEFAULT 0,             -- 显示顺序
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, process_definition_key)
);
```

### 2.7 流程草稿表 (up_process_draft)
```sql
CREATE TABLE up_process_draft (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    process_definition_key VARCHAR(255) NOT NULL,
    form_data JSONB NOT NULL,                    -- 表单数据
    attachments JSONB,                           -- 附件信息
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 3. API设计

### 3.1 任务管理API
```
GET    /api/portal/tasks                    # 获取待办任务列表（支持多维度查询）
GET    /api/portal/tasks/{taskId}           # 获取任务详情
POST   /api/portal/tasks/{taskId}/complete  # 完成任务
POST   /api/portal/tasks/{taskId}/claim     # 认领任务
POST   /api/portal/tasks/{taskId}/unclaim   # 取消认领
POST   /api/portal/tasks/{taskId}/delegate  # 委托任务
POST   /api/portal/tasks/{taskId}/transfer  # 转办任务
GET    /api/portal/tasks/history            # 获取历史任务
GET    /api/portal/tasks/statistics         # 获取任务统计
```

### 3.2 流程管理API
```
GET    /api/portal/processes/definitions    # 获取可发起的流程列表
GET    /api/portal/processes/definitions/{key} # 获取流程定义详情
POST   /api/portal/processes/start          # 发起流程
GET    /api/portal/processes/my-applications # 获取我的申请
GET    /api/portal/processes/{instanceId}   # 获取流程实例详情
POST   /api/portal/processes/{instanceId}/withdraw # 撤回流程
POST   /api/portal/processes/{instanceId}/urge     # 催办流程
GET    /api/portal/processes/{instanceId}/diagram  # 获取流程图数据
GET    /api/portal/processes/{instanceId}/history  # 获取流转历史
```

### 3.3 委托管理API
```
GET    /api/portal/delegations              # 获取委托规则列表
POST   /api/portal/delegations              # 创建委托规则
PUT    /api/portal/delegations/{id}         # 更新委托规则
DELETE /api/portal/delegations/{id}         # 删除委托规则
GET    /api/portal/delegations/proxy-tasks  # 获取代理任务列表
GET    /api/portal/delegations/audit        # 获取委托审计记录
```

### 3.4 权限申请API
```
GET    /api/portal/permissions/my           # 获取我的权限
POST   /api/portal/permissions/request      # 提交权限申请
GET    /api/portal/permissions/requests     # 获取我的申请记录
POST   /api/portal/permissions/renew/{id}   # 续期申请
GET    /api/portal/permissions/usage        # 获取权限使用统计
```

### 3.5 工作台API
```
GET    /api/portal/dashboard/overview       # 获取Dashboard概览数据
GET    /api/portal/dashboard/layout         # 获取工作台布局
PUT    /api/portal/dashboard/layout         # 保存工作台布局
GET    /api/portal/dashboard/statistics     # 获取统计图表数据
```

### 3.6 用户偏好API
```
GET    /api/portal/preferences              # 获取用户偏好设置
PUT    /api/portal/preferences              # 更新用户偏好设置
GET    /api/portal/preferences/notifications # 获取通知偏好
PUT    /api/portal/preferences/notifications # 更新通知偏好
```

### 3.7 收藏和草稿API
```
GET    /api/portal/favorites                # 获取收藏的流程
POST   /api/portal/favorites                # 添加收藏
DELETE /api/portal/favorites/{processKey}   # 取消收藏
GET    /api/portal/drafts                   # 获取草稿列表
POST   /api/portal/drafts                   # 保存草稿
DELETE /api/portal/drafts/{id}              # 删除草稿
```

## 4. 前端组件设计

### 4.1 布局组件
- PortalLayout：门户主布局（顶部导航+左侧菜单+主内容区）
- DragGridLayout：可拖拽网格布局组件

### 4.2 业务组件
- TaskList：任务列表组件（支持多维度筛选）
- TaskDetail：任务详情组件（四区域布局）
- ProcessList：流程列表组件
- ProcessDetail：流程详情组件（四区域布局）
- ProcessDiagram：BPMN流程图组件
- ProcessHistory：流程流转历史组件
- FormRenderer：动态表单渲染组件
- ActionButtons：操作按钮组件

### 4.3 Dashboard组件
- TaskOverviewWidget：任务概览组件
- ProcessStatisticsWidget：流程统计组件
- QuickActionsWidget：快捷操作组件
- NotificationWidget：通知中心组件

### 4.4 通用组件
- NotificationCenter：通知中心
- DelegationManager：委托管理
- PermissionTree：权限树形选择器
- FileUploader：文件上传组件

## 5. 状态管理设计

### 5.1 Pinia Stores
```typescript
// stores/user.ts - 用户状态
// stores/task.ts - 任务状态
// stores/process.ts - 流程状态
// stores/delegation.ts - 委托状态
// stores/notification.ts - 通知状态
// stores/settings.ts - 设置状态
// stores/dashboard.ts - 工作台状态
```

## 6. 路由设计

```typescript
const routes = [
  { path: '/login', component: Login },
  { path: '/', component: PortalLayout, children: [
    { path: '', redirect: '/dashboard' },
    { path: 'dashboard', component: Dashboard },
    { path: 'tasks', component: TaskList },
    { path: 'tasks/:id', component: TaskDetail },
    { path: 'processes', component: ProcessList },
    { path: 'processes/start/:key', component: ProcessStart },
    { path: 'processes/:id', component: ProcessDetail },
    { path: 'my-applications', component: MyApplications },
    { path: 'delegations', component: DelegationManagement },
    { path: 'permissions', component: PermissionManagement },
    { path: 'notifications', component: NotificationCenter },
    { path: 'settings', component: UserSettings },
  ]}
]
```

## 7. 与现有模块集成

### 7.1 与workflow-engine-core集成
- 调用ProcessEngineComponent获取流程定义和实例
- 调用TaskManagerComponent进行任务操作
- 调用VariableManagerComponent管理流程变量
- 调用HistoryManagerComponent查询历史数据
- 订阅NotificationManagerComponent的事件通知

### 7.2 与admin-center集成
- 调用用户认证服务进行登录验证
- 调用组织服务获取部门和虚拟组信息
- 调用角色服务进行权限验证
- 调用数据字典服务获取配置数据
