# 工作流平台待办任务清单

## 优先级说明
- 🔴 高优先级 - 核心功能，需要立即完成
- 🟡 中优先级 - 重要功能，计划中完成
- 🟢 低优先级 - 增强功能，有时间再做

---

## 认证与授权模块

### ✅ TODO-001: 完善三个前端的登录功能使用真实API
- **优先级**: 🔴 高
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 当前三个前端（管理员中心、开发者工作站、用户门户）使用 mock 登录，需要实现真实的后端认证 API
- **涉及文件**:
  - `frontend/admin-center/src/views/login/index.vue`
  - `frontend/developer-workstation/src/views/Login.vue`
  - `frontend/user-portal/src/views/login/index.vue`
  - `backend/platform-security/` (认证模块)
- **已完成子任务**:
  - [x] 1.1 创建用户实体和数据库表
  - [x] 1.2 实现 JWT 认证服务
  - [x] 1.3 创建登录 API 端点 (`POST /api/v1/auth/login`)
  - [x] 1.4 创建登出 API 端点 (`POST /api/v1/auth/logout`)
  - [x] 1.5 创建刷新 Token API (`POST /api/v1/auth/refresh`)
  - [x] 1.6 更新前端登录组件调用真实 API
  - [x] 1.7 实现前端 Token 刷新机制
  - [x] 1.8 添加测试用户数据初始化脚本

---

## 开发者工作站模块

### TODO-009: 完善开发者工作站全部功能（使用真实API）
- **优先级**: 🔴 高
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 开发者工作站是流程开发人员使用的核心工具，需要完整实现所有功能并对接真实后端 API
- **前置依赖**: TODO-001 (登录功能)
- **涉及文件**:
  - 前端: `frontend/developer-workstation/`
  - 后端: `backend/developer-workstation/`
- **子任务**:

#### 9.1 功能单元管理
  - [x] 9.1.1 后端 - 功能单元实体和数据库表设计
  - [x] 9.1.2 后端 - 功能单元 CRUD API (`/api/v1/function-units`)
  - [x] 9.1.3 后端 - 功能单元版本管理 API
  - [x] 9.1.4 后端 - 功能单元导入/导出 API
  - [x] 9.1.5 后端 - 功能单元部署/回滚 API
  - [x] 9.1.6 前端 - 功能单元列表页面对接真实 API
  - [x] 9.1.7 前端 - 功能单元编辑页面对接真实 API
  - [x] 9.1.8 前端 - 功能单元版本历史展示
  - [x] 9.1.9 前端 - 功能单元导入/导出功能

#### 9.2 流程设计器
  - [x] 9.2.1 后端 - 流程定义实体和数据库表
  - [x] 9.2.2 后端 - 流程定义 CRUD API (`/api/v1/process-definitions`)
  - [x] 9.2.3 后端 - BPMN XML 解析和验证服务
  - [x] 9.2.4 后端 - 流程部署到 Flowable 引擎
  - [x] 9.2.5 前端 - BPMN.js 流程设计器集成
  - [x] 9.2.6 前端 - 流程属性面板
  - [x] 9.2.7 前端 - 流程保存/发布功能
  - [x] 9.2.8 前端 - 流程版本对比功能

#### 9.3 表单设计器
  - [x] 9.3.1 后端 - 表单定义实体和数据库表
  - [x] 9.3.2 后端 - 表单定义 CRUD API (`/api/v1/form-definitions`)
  - [x] 9.3.3 后端 - 表单字段验证规则
  - [x] 9.3.4 前端 - 可视化表单设计器组件
  - [x] 9.3.5 前端 - 表单字段拖拽配置
  - [x] 9.3.6 前端 - 表单预览功能
  - [x] 9.3.7 前端 - 表单与流程节点绑定

#### 9.4 数据模型设计
  - [x] 9.4.1 后端 - 数据模型实体和数据库表
  - [x] 9.4.2 后端 - 数据模型 CRUD API (`/api/v1/data-models`)
  - [x] 9.4.3 后端 - 动态表创建服务
  - [x] 9.4.4 前端 - 数据模型设计界面
  - [x] 9.4.5 前端 - 字段类型配置
  - [x] 9.4.6 前端 - 数据模型关联配置

#### 9.5 图标库管理
  - [x] 9.5.1 后端 - 图标资源存储服务
  - [x] 9.5.2 后端 - 图标上传/删除 API (`/api/v1/icons`)
  - [x] 9.5.3 前端 - 图标库展示页面对接真实 API
  - [x] 9.5.4 前端 - 图标上传功能
  - [x] 9.5.5 前端 - 图标分类管理

#### 9.6 API 配置
  - [x] 9.6.1 后端 - API 配置实体和数据库表
  - [x] 9.6.2 后端 - API 配置 CRUD API (`/api/v1/api-configs`)
  - [x] 9.6.3 后端 - API 测试调用服务
  - [x] 9.6.4 前端 - API 配置管理界面
  - [x] 9.6.5 前端 - API 测试功能

#### 9.7 调试与测试
  - [x] 9.7.1 后端 - 流程实例调试 API
  - [x] 9.7.2 后端 - 流程执行日志 API
  - [x] 9.7.3 前端 - 流程调试面板
  - [x] 9.7.4 前端 - 变量监控功能
  - [x] 9.7.5 前端 - 执行日志查看

---

## 用户管理模块

### ✅ TODO-002: 实现用户 CRUD 功能
- **优先级**: 🔴 高
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 管理员中心需要完整的用户管理功能
- **涉及文件**:
  - 后端: `backend/admin-center/src/main/java/com/admin/controller/UserController.java`
  - 后端: `backend/admin-center/src/main/java/com/admin/component/UserManagerComponent.java`
  - 前端: `frontend/admin-center/src/views/user/UserList.vue`
  - 前端: `frontend/admin-center/src/views/user/components/`
- **已完成子任务**:
  - [x] 2.1 用户列表查询 API (分页、筛选、排序)
  - [x] 2.2 用户创建 API (含唯一性验证、密码哈希)
  - [x] 2.3 用户更新 API (基本信息、状态变更)
  - [x] 2.4 用户删除/禁用 API (软删除、最后管理员保护)
  - [x] 2.5 用户批量导入功能 (Excel/CSV解析、模板下载)
  - [x] 2.6 用户详情查看 (含角色、登录历史)
  - [x] 2.7 密码重置功能
  - [x] 2.8 前端用户列表页面
  - [x] 2.9 前端用户表单对话框
  - [x] 2.10 前端用户详情对话框
  - [x] 2.11 前端批量导入对话框

### ✅ TODO-003: 实现角色权限管理
- **优先级**: 🔴 高
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 实现 RBAC 权限控制
- **涉及文件**:
  - 后端: `backend/admin-center/src/main/java/com/admin/controller/RoleController.java`
  - 后端: `backend/admin-center/src/main/java/com/admin/controller/PermissionController.java`
  - 前端: `frontend/admin-center/src/views/role/`
  - 前端: `frontend/admin-center/src/api/role.ts`
- **已完成子任务**:
  - [x] 3.1 角色 CRUD API (创建、查询、更新、删除)
  - [x] 3.2 权限分配 API (配置角色权限、获取角色权限)
  - [x] 3.3 用户角色关联 (添加/移除成员、批量操作)
  - [x] 3.4 前端角色列表页面
  - [x] 3.5 前端权限配置页面
  - [x] 3.6 前端角色表单对话框
  - [x] 3.7 前端成员管理对话框
  - [x] 3.8 前端权限配置对话框
  - [x] 3.9 API Gateway 路由配置

---

## 工作流引擎模块

### ✅ TODO-004: 完善流程定义管理
- **优先级**: 🟡 中
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 开发者工作站的流程设计功能
- **备注**: 已在 TODO-009 开发者工作站中完成
- **子任务**:
  - [x] 4.1 BPMN 流程设计器集成
  - [x] 4.2 流程定义部署 API
  - [x] 4.3 流程版本管理

### TODO-005: 实现任务处理功能
- **优先级**: 🟡 中
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 用户门户的待办任务处理
- **涉及文件**:
  - 后端: `backend/user-portal/src/main/java/com/portal/controller/TaskController.java`
  - 后端: `backend/user-portal/src/main/java/com/portal/component/TaskQueryComponent.java`
  - 后端: `backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java`
  - 前端: `frontend/user-portal/src/views/tasks/index.vue`
  - 前端: `frontend/user-portal/src/views/tasks/detail.vue`
  - 前端: `frontend/user-portal/src/api/task.ts`
- **已完成子任务**:
  - [x] 5.1 待办任务列表 API (多维度查询、分页、筛选、排序)
  - [x] 5.2 任务认领/完成 API (认领、取消认领、审批、拒绝)
  - [x] 5.3 任务委托功能 (委托、转办、权限验证)
  - [x] 5.4 任务催办功能 (单个催办、批量催办、通知)

---

## 表单设计模块

### ✅ TODO-006: 实现动态表单设计器
- **优先级**: 🟡 中
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 可视化表单设计功能
- **备注**: 已在 TODO-009 开发者工作站中完成
- **子任务**:
  - [x] 6.1 表单设计器组件
  - [x] 6.2 表单渲染引擎
  - [x] 6.3 表单数据存储

---

## 系统管理模块

### TODO-007: 实现系统监控功能
- **优先级**: 🟢 低
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 系统运行状态监控
- **涉及文件**:
  - 后端: `backend/admin-center/src/main/java/com/admin/controller/SystemMonitorController.java`
  - 后端: `backend/admin-center/src/main/java/com/admin/component/SystemMonitorComponent.java`
  - 前端: `frontend/admin-center/src/views/monitor/index.vue`
  - 前端: `frontend/admin-center/src/api/monitor.ts`
  - 网关: `backend/api-gateway/src/main/resources/application.yml`
- **已完成子任务**:
  - [x] 7.1 服务健康检查面板 (系统/业务/应用指标展示)
  - [x] 7.2 性能指标展示 (CPU/内存/磁盘/网络实时监控)
  - [x] 7.3 告警管理功能 (告警规则、活跃告警、确认/处理)

### TODO-008: 实现审计日志功能
- **优先级**: 🟢 低
- **状态**: ✅ 已完成
- **完成日期**: 2026-01-06
- **描述**: 操作审计记录
- **涉及文件**:
  - 后端: `backend/admin-center/src/main/java/com/admin/controller/SecurityAuditController.java`
  - 后端: `backend/admin-center/src/main/java/com/admin/component/SecurityAuditComponent.java`
  - 前端: `frontend/admin-center/src/views/audit/index.vue`
  - 前端: `frontend/admin-center/src/api/audit.ts`
  - 网关: `backend/api-gateway/src/main/resources/application.yml`
- **已完成子任务**:
  - [x] 8.1 审计日志记录 (操作类型、资源、用户、IP等)
  - [x] 8.2 审计日志查询 (多条件筛选、分页、详情查看)
  - [x] 8.3 安全策略管理 (密码/登录/会话策略配置)
  - [x] 8.4 异常检测与合规报告

---

## 已完成任务

### ✅ TODO-007: 实现系统监控功能
- **完成日期**: 2026-01-06
- **描述**: 实现系统监控功能，包括系统/业务/应用指标展示、告警规则管理、告警确认处理

### ✅ TODO-008: 实现审计日志功能
- **完成日期**: 2026-01-06
- **描述**: 实现审计日志功能，包括日志查询、安全策略管理、异常检测、合规报告

### ✅ TODO-005: 实现任务处理功能
- **完成日期**: 2026-01-06
- **描述**: 实现完整的任务处理功能，包括待办任务列表、任务认领/完成、委托/转办、催办等

### ✅ TODO-003: 实现角色权限管理
- **完成日期**: 2026-01-06
- **描述**: 实现完整的 RBAC 权限控制，包括角色 CRUD、权限配置、成员管理等

### ✅ TODO-002: 实现用户 CRUD 功能
- **完成日期**: 2026-01-06
- **描述**: 实现完整的用户管理功能，包括用户列表、创建、编辑、删除、状态管理、批量导入、密码重置等

### ✅ TODO-001: 完善三个前端的登录功能使用真实API
- **完成日期**: 2026-01-06
- **描述**: 实现完整的 JWT 认证系统，包括后端认证服务、前端登录/登出功能、Token 刷新机制

### ✅ DONE-001: 搭建基础项目框架
- **完成日期**: 2026-01-06
- **描述**: 完成前后端项目基础架构搭建

### ✅ DONE-002: Docker 服务配置
- **完成日期**: 2026-01-06
- **描述**: 配置 Docker Compose 启动所有服务

### ✅ DONE-003: 添加测试用户下拉菜单
- **完成日期**: 2026-01-06
- **描述**: 在三个前端登录页面添加测试用户快速选择功能

---

## 更新记录

| 日期 | 更新内容 |
|------|----------|
| 2026-01-06 | 创建待办任务清单 |
| 2026-01-06 | 完成 TODO-001: 认证功能实现 |
| 2026-01-06 | 完成 TODO-002: 用户 CRUD 功能实现 |
| 2026-01-06 | 完成 TODO-003: 角色权限管理实现 |
| 2026-01-06 | 开始 TODO-009: 开发者工作站功能实现 |
| 2026-01-06 | 完成 TODO-009 大部分子任务: 功能单元管理、表单设计器、数据模型设计、图标库管理、API配置 |
| 2026-01-06 | 完成 TODO-009 剩余子任务: 版本对比UI、表单流程绑定、数据模型关联、调试面板、变量监控、执行日志 |
| 2026-01-06 | 完成 TODO-009: BPMN.js 流程设计器集成，开发者工作站全部功能完成 |
| 2026-01-06 | 完成 TODO-005: 任务处理功能实现，包括待办列表、认领/完成、委托/转办、催办 |
| 2026-01-06 | 完成 TODO-007: 系统监控功能实现，包括指标展示、告警管理 |
| 2026-01-06 | 完成 TODO-008: 审计日志功能实现，包括日志查询、安全策略、异常检测 |
| 2026-01-07 | 完成功能单元导出、导入与一键部署功能实现 |
