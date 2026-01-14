# 修复功能单元列表不显示问题

## 问题描述

登录后功能单元列表没有任何数据，但数据库中确实有数据。

## 问题原因

1. **用户没有分配角色**：测试用户（tech.director, core.lead, dev.john）没有分配开发者角色
2. **权限检查失败**：后端 API 需要 `FUNCTION_UNIT_VIEW` 权限，但用户没有角色，导致权限检查失败，返回 403 错误

## 修复方案

### 1. 给用户分配开发者角色

已执行 SQL 脚本，给用户分配了相应的开发者角色：

- `tech.director` → `TECH_DIRECTOR_ROLE` (技术总监，拥有所有开发者权限)
- `core.lead` → `TEAM_LEADER_ROLE` (团队组长，拥有大部分开发者权限)
- `dev.john` → `DEVELOPER_ROLE` (开发人员，拥有基本开发者权限)

### 2. 权限格式说明

后端权限检查器会将权限代码从大写下划线格式转换为小写冒号格式：
- `FUNCTION_UNIT_VIEW` → `function_unit:view`
- `FUNCTION_UNIT_CREATE` → `function_unit:create`
- 等等

admin-center 返回的权限代码是 `FUNCTION_UNIT_VIEW` 格式，权限检查器会自动转换。

### 3. 角色权限配置

开发者角色权限存储在 `sys_developer_role_permissions` 表中：

- **TECH_DIRECTOR_ROLE**: 拥有所有开发者权限（FUNCTION_UNIT_VIEW, FUNCTION_UNIT_CREATE, FUNCTION_UNIT_UPDATE, FUNCTION_UNIT_DELETE, FUNCTION_UNIT_PUBLISH 等）
- **TEAM_LEADER_ROLE**: 拥有大部分开发者权限（除了审批权限）
- **DEVELOPER_ROLE**: 拥有基本开发者权限（查看和开发权限）

## 验证步骤

1. **清除浏览器缓存和 localStorage**
   ```javascript
   localStorage.clear()
   location.reload()
   ```

2. **重新登录**
   - 使用 `tech.director / admin123` 登录
   - 应该能看到功能单元列表

3. **检查浏览器控制台**
   - 不应该有 403 错误
   - API 请求应该返回 200 状态码

4. **检查功能单元列表**
   - 应该能看到 "采购申请" 功能单元
   - 应该显示功能单元的名称、状态等信息

## 相关文件

- `FIX_USER_ROLES.sql` - 用户角色分配 SQL 脚本
- `sys_developer_role_permissions` - 开发者角色权限表
- `sys_user_roles` - 用户角色关联表

## 如果仍然无法显示

1. 检查后端服务是否正常运行
2. 检查 admin-center 服务是否正常运行（权限查询需要调用 admin-center API）
3. 检查浏览器控制台的完整错误信息
4. 检查网络请求，确认 API 请求的 URL 和 headers 是否正确
