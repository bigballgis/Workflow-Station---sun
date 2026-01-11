# Admin Center 功能完善需求文档

## 介绍

本文档描述了 Admin Center 模块需要完善的功能，确保所有前端组件都与后端 API 完全对接，不使用任何 mock 数据。Admin Center 已有大部分后端实现，本次完善主要集中在前端 API 层和视图组件的完整对接。

## 术语表

- **Admin_Center**: 管理员中心，负责系统配置、权限管理、功能单元部署和系统监控
- **API_Layer**: 前端 API 层，负责与后端 RESTful 接口通信
- **View_Component**: 视图组件，负责用户界面展示和交互
- **Real_Data**: 真实数据，来自后端数据库的实际数据，非 mock 数据

## 需求

### 需求 1: 数据字典 API 对接

**用户故事**: 作为管理员，我希望数据字典管理页面能够与后端完全对接，以便管理真实的字典数据。

#### 验收标准

1. THE API_Layer SHALL 提供完整的数据字典 API 接口定义（dictionary.ts）
2. WHEN 查询字典列表时，THE View_Component SHALL 调用后端 /api/v1/admin/dictionaries 接口
3. WHEN 创建字典时，THE View_Component SHALL 调用后端 POST /api/v1/admin/dictionaries 接口
4. WHEN 更新字典时，THE View_Component SHALL 调用后端 PUT /api/v1/admin/dictionaries/{id} 接口
5. WHEN 删除字典时，THE View_Component SHALL 调用后端 DELETE /api/v1/admin/dictionaries/{id} 接口
6. WHEN 管理字典项时，THE View_Component SHALL 调用后端字典项相关接口
7. THE View_Component SHALL 正确处理后端返回的错误信息

### 需求 2: 系统配置 API 对接

**用户故事**: 作为管理员，我希望系统配置页面能够与后端完全对接，以便管理真实的系统配置。

#### 验收标准

1. THE API_Layer SHALL 提供完整的系统配置 API 接口定义（config.ts）
2. WHEN 查询配置列表时，THE View_Component SHALL 调用后端 /api/v1/admin/configs 接口
3. WHEN 更新配置时，THE View_Component SHALL 调用后端 PUT /api/v1/admin/configs/{key} 接口
4. WHEN 查看配置历史时，THE View_Component SHALL 调用后端配置历史接口
5. WHEN 回滚配置时，THE View_Component SHALL 调用后端配置回滚接口
6. THE View_Component SHALL 正确显示配置变更影响范围

### 需求 3: 虚拟组 API 对接

**用户故事**: 作为管理员，我希望虚拟组管理页面能够与后端完全对接，以便管理真实的虚拟组数据。

#### 验收标准

1. THE API_Layer SHALL 提供完整的虚拟组 API 接口定义（virtualGroup.ts）
2. WHEN 查询虚拟组列表时，THE View_Component SHALL 调用后端 /api/v1/admin/virtual-groups 接口
3. WHEN 创建虚拟组时，THE View_Component SHALL 调用后端 POST /api/v1/admin/virtual-groups 接口
4. WHEN 管理虚拟组成员时，THE View_Component SHALL 调用后端成员管理接口
5. WHEN 查询虚拟组任务时，THE View_Component SHALL 调用后端任务查询接口
6. THE View_Component SHALL 正确显示虚拟组生命周期状态

### 需求 4: 功能单元 API 对接

**用户故事**: 作为管理员，我希望功能单元管理页面能够与后端完全对接，以便导入和部署真实的功能包。

#### 验收标准

1. THE API_Layer SHALL 提供完整的功能单元 API 接口定义（functionUnit.ts）
2. WHEN 导入功能包时，THE View_Component SHALL 调用后端 POST /api/v1/admin/function-units/import 接口
3. WHEN 部署功能单元时，THE View_Component SHALL 调用后端部署接口
4. WHEN 查询部署状态时，THE View_Component SHALL 调用后端状态查询接口
5. WHEN 回滚部署时，THE View_Component SHALL 调用后端回滚接口
6. THE View_Component SHALL 正确显示部署进度和日志

### 需求 5: TypeScript 类型修复

**用户故事**: 作为开发者，我希望所有 TypeScript 类型错误都被修复，以便代码能够正确编译。

#### 验收标准

1. THE View_Component SHALL 修复 monitor/index.vue 中的 el-tag type 类型错误
2. THE View_Component SHALL 确保所有 API 响应类型与后端返回数据匹配
3. THE View_Component SHALL 确保所有组件 props 类型正确定义
4. THE API_Layer SHALL 确保所有接口参数和返回类型正确定义

### 需求 6: 用户管理部门选择器完善

**用户故事**: 作为管理员，我希望用户管理页面的部门选择器能够加载真实的部门数据。

#### 验收标准

1. WHEN 用户管理页面加载时，THE View_Component SHALL 调用组织架构 API 获取部门列表
2. THE View_Component SHALL 在部门选择器中显示完整的部门树
3. WHEN 筛选用户时，THE View_Component SHALL 使用选中的部门 ID 进行过滤

### 需求 7: 日志管理导出功能

**用户故事**: 作为管理员，我希望能够导出审计日志，以便进行离线分析。

#### 验收标准

1. WHEN 点击导出按钮时，THE View_Component SHALL 调用后端日志导出接口
2. THE View_Component SHALL 支持导出当前查询条件下的日志
3. THE View_Component SHALL 正确处理大文件下载

### 需求 8: Dashboard 真实数据对接

**用户故事**: 作为管理员，我希望 Dashboard 页面显示真实的系统统计数据，以便了解系统运行状态。

#### 验收标准

1. WHEN Dashboard 页面加载时，THE View_Component SHALL 调用后端统计接口获取真实数据
2. THE View_Component SHALL 显示真实的用户总数、部门数量、角色数量和在线用户数
3. THE View_Component SHALL 显示真实的最近活动记录（来自审计日志）
4. THE View_Component SHALL 显示真实的用户活跃趋势图表数据
5. THE View_Component SHALL 支持数据自动刷新

