# 功能单元管理完整需求规格

## 概述

本规格定义了功能单元（Function Unit）的完整管理功能，涵盖：
1. **Admin Center**: 功能单元安全删除、启用/禁用开关
2. **User Portal**: 功能单元列表展示、流程启动、表单填写

功能单元是在开发者工作站中设计的业务流程模板，部署后可供用户在用户门户中发起。

## 术语表

- **Function_Unit**: 功能单元，从开发者工作站部署的业务流程模板
- **Admin_Center**: 管理中心，系统管理员操作界面
- **User_Portal**: 用户门户，终端用户操作界面
- **Enabled_State**: 启用状态，功能单元对终端用户可见可用
- **Disabled_State**: 禁用状态，功能单元对终端用户不可见不可用

## 需求

### 需求 1: 功能单元安全删除

**用户故事:** 作为系统管理员，我希望能够安全地删除不再需要的功能单元及其所有关联组件，并通过多重确认机制防止误操作。

#### 验收标准

1. THE Admin_Center SHALL 在功能单元列表的操作列中显示删除按钮
2. WHEN 管理员点击删除按钮 THEN Admin_Center SHALL 显示红色警告样式的危险操作确认对话框
3. WHEN 显示删除确认对话框 THEN Admin_Center SHALL 显示警告图标、"危险操作"标题和红色边框
4. WHEN 显示删除确认对话框 THEN Admin_Center SHALL 列出将被删除的关联数据数量
5. THE Admin_Center SHALL 要求用户手动输入功能单元名称才能启用删除按钮
6. IF 输入的名称与功能单元名称不匹配 THEN Admin_Center SHALL 禁用删除按钮
7. WHEN 名称匹配后 THEN Admin_Center SHALL 显示3秒倒计时，倒计时结束后才能点击删除按钮
8. IF 功能单元有正在运行的流程实例 THEN Admin_Center SHALL 阻止删除并提示用户
9. WHEN 删除确认通过 THEN Admin_Center SHALL 级联删除功能单元及其所有关联内容
10. WHEN 删除成功 THEN Admin_Center SHALL 显示成功提示并刷新列表

### 需求 2: 功能单元启用/禁用开关

**用户故事:** 作为系统管理员，我希望能够临时禁用功能单元而不删除它，以便控制用户访问。

#### 验收标准

1. THE Admin_Center SHALL 在功能单元列表中显示启用/禁用开关
2. WHEN 管理员切换开关为禁用状态 THEN Admin_Center SHALL 显示确认提示
3. WHEN 确认禁用 THEN Admin_Center SHALL 将功能单元标记为禁用状态
4. WHEN 功能单元处于禁用状态 THEN User_Portal SHALL 不显示该功能单元
5. WHEN 功能单元处于禁用状态 THEN User_Portal SHALL 阻止直接URL访问
6. WHEN 管理员切换开关为启用状态 THEN Admin_Center SHALL 恢复启用状态
7. WHEN 功能单元状态变更 THEN Admin_Center SHALL 立即生效

### 需求 3: 用户门户功能单元列表

**用户故事:** 作为用户门户的用户，我希望能够查看我有权限访问的所有已部署且启用的功能单元。

#### 验收标准

1. THE User_Portal SHALL 显示所有已部署、已启用且用户有权限访问的功能单元
2. THE User_Portal SHALL 为每个功能单元显示名称、描述、图标
3. WHEN 用户输入搜索关键字 THEN User_Portal SHALL 按名称和描述筛选
4. WHEN 用户选择分类 THEN User_Portal SHALL 按分类筛选
5. WHEN 用户点击收藏按钮 THEN User_Portal SHALL 切换收藏状态

### 需求 4: 启动功能单元流程

**用户故事:** 作为用户门户的用户，我希望能够点击功能单元卡片进入流程启动页面。

#### 验收标准

1. WHEN 用户点击功能单元卡片 THEN User_Portal SHALL 使用功能单元ID导航到启动页面
2. WHEN 启动页面加载 THEN User_Portal SHALL 根据ID获取功能单元详情
3. THE User_Portal SHALL 根据功能单元配置动态渲染表单
4. THE User_Portal SHALL 显示功能单元的名称和描述信息
5. WHEN 用户提交表单 THEN User_Portal SHALL 执行表单验证规则
6. IF 表单验证通过 THEN User_Portal SHALL 显示提交确认对话框
7. WHEN 用户确认提交 THEN User_Portal SHALL 发起流程并跳转到申请列表
8. IF 提交失败 THEN User_Portal SHALL 显示错误信息
9. IF 功能单元已禁用 THEN User_Portal SHALL 显示"功能单元已禁用"提示


## 技术需求

### 数据库变更

```sql
ALTER TABLE admin_function_units ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
```

### API 接口

#### Admin Center API

- `GET /function-units/{id}/delete-preview` - 获取删除预览
- `DELETE /function-units/{id}` - 删除功能单元
- `PUT /function-units/{id}/enabled` - 切换启用状态

#### User Portal API

- `GET /api/portal/processes/definitions` - 获取可用功能单元列表
- `GET /api/portal/processes/function-unit/:id/content` - 获取功能单元详情
- `POST /api/portal/processes/start` - 启动流程

## 相关文件

### Admin Center
- `frontend/admin-center/src/views/function-unit/index.vue`
- `frontend/admin-center/src/api/functionUnit.ts`
- `backend/admin-center/src/main/java/com/admin/controller/FunctionUnitController.java`
- `backend/admin-center/src/main/java/com/admin/component/FunctionUnitManagerComponent.java`
- `backend/admin-center/src/main/java/com/admin/entity/FunctionUnit.java`

### User Portal
- `frontend/user-portal/src/views/processes/index.vue`
- `frontend/user-portal/src/views/processes/start.vue`
- `frontend/user-portal/src/api/process.ts`
- `backend/user-portal/src/main/java/com/portal/component/FunctionUnitAccessComponent.java`

## 已知问题与修复

### 导航参数错误
前端 `index.vue` 使用 `process.key`（code）导航，但 `start.vue` 需要使用 ID。
**修复**: 使用 `process.id` 而不是 `process.key` - ✅ 已修复
