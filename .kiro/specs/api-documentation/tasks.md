# API 文档梳理 - 任务清单

## 1. 前期准备和分析

### 1.1 环境准备
- [ ] 确认所有后端服务正常运行（API Gateway, Workflow Engine Core, User Portal Backend, Developer Workstation Backend, Admin Center Backend）
- [ ] 确认数据库连接正常，可以查询表结构
- [ ] 准备 API 测试工具（使用 PowerShell Invoke-RestMethod）

### 1.2 代码结构分析
- [ ] 扫描所有后端 Controller 文件，提取 API 端点信息
  - Admin Center Backend: 20+ controllers
  - Developer Workstation Backend: 11+ controllers  
  - User Portal Backend: 15+ controllers
  - Workflow Engine Core: 4+ controllers
- [ ] 扫描所有前端 API 文件，提取前端调用的 API
  - Admin Center Frontend: 14 API 文件
  - Developer Workstation Frontend: 6 API 文件
  - User Portal Frontend: 9 API 文件

## 2. Admin Center API 文档编写

### 2.1 认证授权 API
- [ ] 分析 `AuthController.java` (admin-center)
- [ ] 文档化登录、登出、Token 刷新等 API
- [ ] 标注数据库操作（sys_users 表）
- [ ] 编写请求/响应示例

### 2.2 用户管理 API
- [ ] 分析 `UserController.java`
- [ ] 文档化用户 CRUD 操作
- [ ] 文档化用户状态管理、角色分配 API
- [ ] 分析 `UserBusinessUnitRoleController.java`
- [ ] 标注数据库操作（sys_users, sys_business_unit_members, sys_virtual_group_members）

### 2.3 角色管理 API
- [ ] 分析 `RoleController.java`
- [ ] 文档化角色 CRUD、权限管理 API
- [ ] 标注数据库操作（sys_roles, sys_permissions, sys_virtual_group_roles）

### 2.4 组织架构 API
- [ ] 分析 `BusinessUnitController.java`
- [ ] 文档化业务单元树查询、CRUD 操作
- [ ] 分析 `BusinessUnitRoleController.java`
- [ ] 分析 `ApproverController.java`
- [ ] 标注数据库操作（sys_business_units, sys_business_unit_members, sys_business_unit_roles）

### 2.5 虚拟组 API
- [ ] 分析 `VirtualGroupController.java`
- [ ] 文档化虚拟组 CRUD、成员管理 API
- [ ] 分析 `VirtualGroupRoleController.java`
- [ ] 标注数据库操作（sys_virtual_groups, sys_virtual_group_members, sys_virtual_group_roles）

### 2.6 功能单元管理 API (Admin Center)
- [ ] 分析 `FunctionUnitController.java` (admin-center)
- [ ] 分析 `FunctionUnitImportController.java`
- [ ] 文档化功能单元查询、导入、部署 API
- [ ] 标注数据库操作（sys_function_units, sys_function_unit_contents）

### 2.7 安全审计 API
- [ ] 分析 `SecurityAuditController.java`
- [ ] 分析 `LogController.java`
- [ ] 文档化审计日志查询、安全策略配置 API
- [ ] 标注数据库操作（admin_audit_logs, admin_security_policies）

### 2.8 系统监控 API
- [ ] 分析 `SystemMonitorController.java`
- [ ] 分析 `DashboardController.java` (admin-center)
- [ ] 文档化系统指标查询、告警管理 API

### 2.9 数据字典 API
- [ ] 分析 `DictionaryController.java`
- [ ] 文档化字典 CRUD、字典项查询 API
- [ ] 标注数据库操作（admin_dictionaries, admin_dictionary_items）

### 2.10 其他 Admin Center API
- [ ] 分析 `ConfigController.java`
- [ ] 分析 `PermissionController.java` (admin-center)
- [ ] 分析 `PermissionRequestAdminController.java`
- [ ] 分析 `DeveloperPermissionController.java`
- [ ] 分析 `TaskAssignmentController.java`

## 3. Developer Workstation API 文档编写

### 3.1 功能单元 API
- [ ] 分析 `FunctionUnitController.java` (developer-workstation)
- [ ] 文档化功能单元 CRUD、发布、克隆、验证 API
- [ ] 标注数据库操作（dw_function_units, sys_function_units, sys_function_unit_contents）

### 3.2 表设计 API
- [ ] 分析 `TableDesignController.java`
- [ ] 文档化表定义 CRUD、字段管理、外键关系 API
- [ ] 文档化 DDL 生成、表结构验证 API
- [ ] 标注数据库操作（dw_table_definitions, dw_field_definitions, dw_foreign_keys）

### 3.3 表单设计 API
- [ ] 分析 `FormDesignController.java`
- [ ] 文档化表单定义 CRUD、表绑定、配置管理 API
- [ ] 标注数据库操作（dw_form_definitions, dw_form_table_bindings）

### 3.4 动作设计 API
- [ ] 分析 `ActionDesignController.java`
- [ ] 文档化动作定义 CRUD、测试 API
- [ ] 标注数据库操作（dw_action_definitions）

### 3.5 流程设计 API
- [ ] 分析 `ProcessDesignController.java`
- [ ] 文档化流程定义保存/获取、验证、模拟 API
- [ ] 标注数据库操作（dw_process_definitions）

### 3.6 图标库 API
- [ ] 分析 `IconLibraryController.java`
- [ ] 文档化图标上传/查询/删除、分类/标签管理 API
- [ ] 标注数据库操作（dw_icons）

### 3.7 版本管理 API
- [ ] 分析 `VersionController.java`
- [ ] 文档化版本历史、比较、回滚 API
- [ ] 标注数据库操作（dw_versions）

### 3.8 部署和导入导出 API
- [ ] 分析 `DeploymentController.java`
- [ ] 分析 `ExportImportController.java`
- [ ] 文档化功能单元导出、部署、导入 API

### 3.9 认证 API (Developer Workstation)
- [ ] 分析 `AuthController.java` (developer-workstation)
- [ ] 文档化开发者工作站的认证 API

## 4. User Portal API 文档编写

### 4.1 任务管理 API
- [ ] 分析 `TaskController.java`
- [ ] 文档化任务查询（待办/已办）、认领、完成 API
- [ ] 文档化任务委托、转办、催办 API
- [ ] 标注数据库操作（act_ru_task, extended_task_info, delegation_rules）

### 4.2 流程管理 API
- [ ] 分析 `ProcessController.java`
- [ ] 文档化流程定义查询、流程启动 API
- [ ] 文档化流程撤回、催办、草稿管理 API
- [ ] 标注数据库操作（act_ru_execution, act_hi_procinst, sys_function_unit_contents）

### 4.3 委托管理 API
- [ ] 分析 `DelegationController.java`
- [ ] 文档化委托规则 CRUD、暂停/恢复 API
- [ ] 文档化代理任务查询、委托审计记录 API
- [ ] 标注数据库操作（delegation_rules, delegation_audit）

### 4.4 权限申请 API
- [ ] 分析 `PermissionController.java` (user-portal)
- [ ] 分析 `PermissionRequestController.java`
- [ ] 分析 `UserPermissionController.java`
- [ ] 分析 `ApprovalController.java`
- [ ] 文档化可申请角色/虚拟组查询、权限申请提交 API
- [ ] 文档化申请记录查询、申请审批 API
- [ ] 标注数据库操作（sys_roles, sys_virtual_groups, sys_virtual_group_members）

### 4.5 成员管理和退出 API
- [ ] 分析 `MemberController.java`
- [ ] 分析 `ExitController.java`
- [ ] 文档化成员管理、退出角色 API

### 4.6 工作台 API
- [ ] 分析 `DashboardController.java` (user-portal)
- [ ] 分析 `PreferenceController.java`
- [ ] 文档化工作台概览、统计数据、用户偏好 API

### 4.7 认证 API (User Portal)
- [ ] 分析 `AuthController.java` (user-portal)
- [ ] 文档化用户门户的认证 API

## 5. Workflow Engine Core API 文档编写

### 5.1 流程引擎 API
- [ ] 分析 `ProcessController.java` (workflow-engine-core)
- [ ] 文档化流程部署、实例管理 API
- [ ] 标注数据库操作（Flowable 内置表 act_*）

### 5.2 任务引擎 API
- [ ] 分析 `TaskController.java` (workflow-engine-core)
- [ ] 文档化任务管理（通过 Flowable）API
- [ ] 标注数据库操作（act_ru_task, extended_task_info）

### 5.3 历史数据 API
- [ ] 分析 `HistoryController.java`
- [ ] 文档化历史数据查询 API
- [ ] 标注数据库操作（act_hi_* 表）

### 5.4 监控 API
- [ ] 分析 `MonitoringController.java`
- [ ] 文档化流程监控 API

## 6. Platform Security API 文档编写

### 6.1 认证授权 API (Platform Security)
- [ ] 分析 `AuthController.java` (platform-security)
- [ ] 文档化平台级认证授权 API
- [ ] 标注数据库操作（sys_users, sys_roles, sys_permissions）

## 7. 前端 API 调用分析

### 7.1 Admin Center Frontend API 分析
- [ ] 分析 `frontend/admin-center/src/api/auth.ts`
- [ ] 分析 `frontend/admin-center/src/api/user.ts`
- [ ] 分析 `frontend/admin-center/src/api/role.ts`
- [ ] 分析 `frontend/admin-center/src/api/businessUnit.ts`
- [ ] 分析 `frontend/admin-center/src/api/virtualGroup.ts`
- [ ] 分析 `frontend/admin-center/src/api/functionUnit.ts`
- [ ] 分析 `frontend/admin-center/src/api/audit.ts`
- [ ] 分析 `frontend/admin-center/src/api/monitor.ts`
- [ ] 分析 `frontend/admin-center/src/api/dictionary.ts`
- [ ] 分析 `frontend/admin-center/src/api/dashboard.ts`
- [ ] 分析 `frontend/admin-center/src/api/config.ts`
- [ ] 分析 `frontend/admin-center/src/api/permissionRequest.ts`
- [ ] 匹配前端 API 调用与后端端点

### 7.2 Developer Workstation Frontend API 分析
- [ ] 分析 `frontend/developer-workstation/src/api/auth.ts`
- [ ] 分析 `frontend/developer-workstation/src/api/functionUnit.ts`
- [ ] 分析 `frontend/developer-workstation/src/api/icon.ts`
- [ ] 分析 `frontend/developer-workstation/src/api/user.ts`
- [ ] 分析 `frontend/developer-workstation/src/api/adminCenter.ts`
- [ ] 匹配前端 API 调用与后端端点

### 7.3 User Portal Frontend API 分析
- [ ] 分析 `frontend/user-portal/src/api/auth.ts`
- [ ] 分析 `frontend/user-portal/src/api/task.ts`
- [ ] 分析 `frontend/user-portal/src/api/process.ts`
- [ ] 分析 `frontend/user-portal/src/api/delegation.ts`
- [ ] 分析 `frontend/user-portal/src/api/permission.ts`
- [ ] 分析 `frontend/user-portal/src/api/dashboard.ts`
- [ ] 分析 `frontend/user-portal/src/api/preference.ts`
- [ ] 分析 `frontend/user-portal/src/api/user.ts`
- [ ] 匹配前端 API 调用与后端端点

## 8. 数据库操作分析

### 8.1 Repository 和 Service 层分析
- [ ] 分析 Admin Center 的 Repository 和 Service 层
- [ ] 分析 Developer Workstation 的 Repository 和 Service 层
- [ ] 分析 User Portal 的 Repository 和 Service 层
- [ ] 分析 Workflow Engine Core 的 Repository 和 Service 层
- [ ] 提取每个 API 的数据库操作类型（SELECT/INSERT/UPDATE/DELETE）

### 8.2 数据库表清单整理
- [ ] 整理 dw_* 表清单（Developer Workstation）
- [ ] 整理 sys_* 表清单（Platform Security）
- [ ] 整理 admin_* 表清单（Admin Center）
- [ ] 整理 act_* 表清单（Flowable）
- [ ] 整理扩展表清单（extended_task_info, delegation_*）

## 9. 文档生成和整合

### 9.1 主文档编写
- [ ] 创建文档目录结构
- [ ] 编写文档概述和使用说明
- [ ] 按模块分类整理 API 文档
- [ ] 添加 API 索引表（按字母顺序）

### 9.2 辅助文档编写
- [ ] 编写数据库表关系图
- [ ] 编写服务依赖图
- [ ] 编写枚举类型定义清单
- [ ] 编写错误码说明

### 9.3 示例和最佳实践
- [ ] 添加常用 API 调用示例
- [ ] 添加认证和授权示例
- [ ] 添加分页查询示例
- [ ] 添加错误处理示例

## 10. 文档审查和完善

### 10.1 完整性检查
- [ ] 检查所有前端 API 调用是否都已记录
- [ ] 检查所有后端 Controller 端点是否都已记录
- [ ] 检查每个 API 是否包含完整信息（端点、功能、数据库操作、请求/响应）

### 10.2 准确性验证
- [ ] 使用 PowerShell Invoke-RestMethod 测试关键 API
- [ ] 验证 API 端点路径是否正确
- [ ] 验证数据库表名是否正确
- [ ] 验证请求/响应格式是否与实际代码一致

### 10.3 格式和可用性检查
- [ ] 检查文档格式统一性
- [ ] 检查 Markdown 语法正确性
- [ ] 添加版本信息和更新日期
- [ ] 添加贡献者和维护者信息

### 10.4 最终审查
- [ ] 开发团队审查文档完整性
- [ ] 测试团队验证 API 可用性
- [ ] 产品团队审查功能描述准确性
- [ ] 修正审查中发现的问题

## 11. 文档发布和维护

### 11.1 文档发布
- [ ] 将文档提交到版本控制系统
- [ ] 生成 PDF 版本（可选）
- [ ] 发布到内部文档平台
- [ ] 通知相关团队文档已发布

### 11.2 维护流程建立
- [ ] 建立文档更新流程
- [ ] 设置代码审查时的文档检查点
- [ ] 建立定期审查机制
- [ ] 指定文档维护负责人

## 验收标准

根据需求文档第 5 节，完成以下验收标准：

- [ ] 列出所有前端 API 调用（按模块分类）
- [ ] 说明每个 API 的功能和用途
- [ ] 标注是否操作数据库
- [ ] 说明操作哪些数据库表
- [ ] 说明执行什么类型的操作（SELECT/INSERT/UPDATE/DELETE）
- [ ] 包含请求参数和响应格式
- [ ] 标注 API 所属的后端服务
- [ ] 使用中文编写文档
- [ ] 格式清晰，易于查阅

## 注意事项

1. **使用 PowerShell 进行 API 测试**：本项目使用 Windows PowerShell，使用 `Invoke-RestMethod` 而不是 `curl`
2. **数据库查询**：使用 `docker exec -i platform-postgres psql -U platform -d workflow_platform` 查询数据库
3. **服务端口**：
   - API Gateway: 8080
   - Workflow Engine Core: 8081
   - User Portal Backend: 8082
   - Developer Workstation Backend: 8083
   - Admin Center Backend: 8090
4. **文档格式**：使用 Markdown 格式，便于版本控制和在线查看
5. **增量更新**：文档应随代码变更同步更新
