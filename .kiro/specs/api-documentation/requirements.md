# API 文档梳理需求

## 1. 项目概述

本项目是一个基于微服务架构的工作流平台，包含以下前端应用和后端服务：

### 前端应用
- **Admin Center** (端口 3000) - 管理员中心
- **User Portal** (端口 3001) - 用户门户
- **Developer Workstation** (端口 3002) - 开发者工作站

### 后端服务
- **API Gateway** (端口 8080) - API 网关
- **Workflow Engine Core** (端口 8081) - 工作流引擎核心
- **User Portal Backend** (端口 8082) - 用户门户后端
- **Developer Workstation Backend** (端口 8083) - 开发者工作站后端
- **Admin Center Backend** (端口 8090) - 管理中心后端

## 2. 需求目标

创建一份完整的API文档，包含：

1. **API 端点清单** - 列出所有前端调用的后端API
2. **功能说明** - 每个API的具体功能和用途
3. **数据库操作** - 是否操作数据库，操作哪些表，执行什么操作
4. **请求/响应格式** - 参数和返回值说明
5. **模块归属** - API属于哪个后端服务

## 3. API 分类

### 3.1 认证授权 API
- 用户登录/登出
- Token 刷新
- 获取当前用户信息
- 密码修改

### 3.2 用户管理 API (Admin Center)
- 用户 CRUD 操作
- 用户状态管理
- 用户角色分配
- 用户业务单元管理
- 用户虚拟组管理
- 批量导入用户

### 3.3 角色管理 API (Admin Center)
- 角色 CRUD 操作
- 角色权限管理
- 角色成员管理
- 获取业务角色/开发角色

### 3.4 组织架构 API (Admin Center)
- 业务单元树查询
- 业务单元 CRUD 操作
- 业务单元成员管理
- 业务单元角色绑定
- 业务单元审批人管理

### 3.5 虚拟组 API (Admin Center)
- 虚拟组 CRUD 操作
- 虚拟组成员管理
- 虚拟组角色绑定
- 虚拟组任务管理
- 虚拟组审批人管理

### 3.6 功能单元 API (Developer Workstation)
- 功能单元 CRUD 操作
- 功能单元发布/克隆
- 功能单元验证
- 功能单元导出/部署
- 版本管理

### 3.7 表设计 API (Developer Workstation)
- 表定义 CRUD 操作
- 字段定义管理
- 外键关系管理
- DDL 生成
- 表结构验证

### 3.8 表单设计 API (Developer Workstation)
- 表单定义 CRUD 操作
- 表单-表绑定管理
- 表单配置管理

### 3.9 动作设计 API (Developer Workstation)
- 动作定义 CRUD 操作
- 动作测试

### 3.10 流程设计 API (Developer Workstation)
- 流程定义保存/获取
- 流程验证
- 流程模拟

### 3.11 图标库 API (Developer Workstation)
- 图标上传/查询/删除
- 图标分类/标签管理

### 3.12 任务管理 API (User Portal)
- 任务查询（待办/已办）
- 任务详情获取
- 任务认领/取消认领
- 任务完成
- 任务委托/转办
- 任务催办
- 任务统计

### 3.13 流程管理 API (User Portal)
- 流程定义查询
- 流程启动
- 流程撤回/催办
- 我的申请查询
- 草稿管理
- 功能单元内容获取

### 3.14 委托管理 API (User Portal)
- 委托规则 CRUD 操作
- 委托规则暂停/恢复
- 代理任务查询
- 委托审计记录

### 3.15 权限申请 API (User Portal)
- 可申请角色/虚拟组查询
- 业务单元查询
- 权限申请提交
- 申请记录查询
- 申请审批
- 成员管理
- 退出角色

### 3.16 工作流引擎 API (Workflow Engine Core)
- 流程定义部署
- 流程实例管理
- 任务管理（通过 Flowable）
- 历史数据查询
- 流程监控

### 3.17 安全审计 API (Admin Center)
- 安全策略配置
- 审计日志查询
- 异常检测
- 合规报告

### 3.18 系统监控 API (Admin Center)
- 系统指标查询
- 告警规则管理
- 告警管理

### 3.19 数据字典 API (Admin Center)
- 字典 CRUD 操作
- 字典项查询

## 4. 数据库表归属

### 4.1 Developer Workstation 表 (dw_*)
- `dw_function_units` - 功能单元
- `dw_table_definitions` - 表定义
- `dw_field_definitions` - 字段定义
- `dw_form_definitions` - 表单定义
- `dw_form_table_bindings` - 表单-表绑定
- `dw_action_definitions` - 动作定义
- `dw_process_definitions` - 流程定义
- `dw_foreign_keys` - 外键关系
- `dw_icons` - 图标库
- `dw_versions` - 版本历史

### 4.2 Platform Security 表 (sys_*)
- `sys_users` - 用户表
- `sys_roles` - 角色表
- `sys_permissions` - 权限表
- `sys_virtual_groups` - 虚拟组
- `sys_virtual_group_members` - 虚拟组成员
- `sys_virtual_group_roles` - 虚拟组角色绑定
- `sys_business_units` - 业务单元
- `sys_business_unit_members` - 业务单元成员
- `sys_business_unit_roles` - 业务单元角色绑定
- `sys_function_units` - 功能单元（已发布）
- `sys_function_unit_contents` - 功能单元内容

### 4.3 Admin Center 表 (admin_*)
- `admin_audit_logs` - 审计日志
- `admin_security_policies` - 安全策略
- `admin_dictionaries` - 数据字典
- `admin_dictionary_items` - 字典项

### 4.4 Workflow Engine 表
- Flowable 内置表（act_*）
- `extended_task_info` - 扩展任务信息
- `delegation_rules` - 委托规则
- `delegation_audit` - 委托审计

## 5. 验收标准

- [ ] 列出所有前端 API 调用（按模块分类）
- [ ] 说明每个 API 的功能和用途
- [ ] 标注是否操作数据库
- [ ] 说明操作哪些数据库表
- [ ] 说明执行什么类型的操作（SELECT/INSERT/UPDATE/DELETE）
- [ ] 包含请求参数和响应格式
- [ ] 标注 API 所属的后端服务
- [ ] 使用中文编写文档
- [ ] 格式清晰，易于查阅

## 6. 输出格式

文档应包含以下结构：

```markdown
# API 文档

## 1. 认证授权 API

### 1.1 用户登录
- **端点**: POST /api/v1/auth/login
- **服务**: platform-s