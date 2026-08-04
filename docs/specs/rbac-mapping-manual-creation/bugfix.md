# Bugfix 需求文档

## 简介

RBAC Mapping 页面（`/bi-management/rbac-mapping`）当前会自动列出所有系统角色（Sys_Role），并期望每个角色都配置 Superset 角色映射。但实际业务场景中，并非所有系统角色都需要与 Superset 权限做映射。用户希望 RBAC 映射改为手动创建模式：管理员主动选择需要映射的系统角色并配置对应的 Superset 角色，未创建映射的系统角色不应出现在列表中。同时需要支持删除已有的映射记录。

## Bug 分析

### 当前行为（缺陷）

1.1 WHEN 管理员访问 RBAC Mapping 列表页面 THEN 系统自动查询并展示所有活跃的系统角色（`roleRepository.findAllActive()`），无论该角色是否已配置 Superset 角色映射

1.2 WHEN 管理员想要为某个系统角色创建 RBAC 映射 THEN 系统没有提供"新增映射"的入口，只能对已展示在列表中的角色点击"Edit Mapping"

1.3 WHEN 管理员想要移除某个系统角色的全部 RBAC 映射 THEN 系统没有提供"删除映射"操作，只能通过编辑将 Superset 角色清空，但该角色仍然显示在列表中

1.4 WHEN 管理员使用 roleName 或 roleType 筛选 THEN 系统在所有活跃系统角色中筛选，而非仅在已创建映射的角色中筛选

### 期望行为（正确）

2.1 WHEN 管理员访问 RBAC Mapping 列表页面 THEN 系统 SHALL 仅展示已手动创建了映射记录的系统角色（即 `bi_rbac_mapping` 表中存在对应 `sys_role_id` 记录的角色），未创建映射的系统角色不应出现在列表中

2.2 WHEN 管理员点击"新增映射"按钮 THEN 系统 SHALL 展示创建表单，允许管理员从系统角色下拉列表中选择一个尚未创建映射的角色，并选择一个或多个 ACTIVE 状态的 Superset 角色，提交后创建映射记录

2.3 WHEN 管理员点击某条映射记录的"删除"按钮并确认 THEN 系统 SHALL 删除该系统角色的所有 RBAC 映射记录，该角色从列表中消失

2.4 WHEN 管理员使用 roleName 或 roleType 筛选 THEN 系统 SHALL 仅在已创建映射的系统角色中进行筛选

### 不变行为（回归防护）

3.1 WHEN 管理员点击"Sync Superset Roles"按钮 THEN 系统 SHALL CONTINUE TO 从 Superset 数据库同步角色列表并返回同步摘要

3.2 WHEN 管理员编辑已有映射记录的 Superset 角色选择 THEN 系统 SHALL CONTINUE TO 以全量替换方式保存新的映射列表

3.3 WHEN 映射编辑表单中展示 Superset 角色列表 THEN 系统 SHALL CONTINUE TO 仅允许选择 ACTIVE 状态的 Superset 角色

3.4 WHEN 请求 Guest Token 时根据用户的 Sys_Role 查询有效映射 THEN 系统 SHALL CONTINUE TO 返回所有 ACTIVE 状态的 Superset 角色 ID 的去重并集

3.5 WHEN 已映射的 Superset 角色被标记为 INACTIVE THEN 系统 SHALL CONTINUE TO 保留映射记录但在查询有效映射时排除该角色
