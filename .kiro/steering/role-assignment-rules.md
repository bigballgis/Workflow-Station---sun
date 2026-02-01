# 角色分配规则

## 核心原则

**角色只能分配给虚拟组，不能直接分配给用户。**

用户通过加入虚拟组来获得角色权限。

## 权限获取路径

```
用户 → 加入虚拟组 → 虚拟组绑定角色 → 用户获得角色权限
```

## 虚拟组类型 (type 字段)

| 类型 | 说明 | 可删除 |
|------|------|--------|
| `SYSTEM` | 系统内置虚拟组，不可删除 | ❌ |
| `CUSTOM` | 用户自定义虚拟组，可删除 | ✅ |

所有虚拟组都可以绑定 AD 组（通过 `ad_group` 字段）。

## 系统角色与系统虚拟组（不可删除）

| 系统角色 | 角色代码 | 对应虚拟组 | 虚拟组代码 | 特殊规则 |
|----------|----------|------------|------------|----------|
| System Administrator | SYS_ADMIN | System Administrators | SYS_ADMINS | 必须至少有1个成员 |
| Auditor | AUDITOR | Auditors | AUDITORS | - |
| Developer | DEVELOPER | Developers | DEVELOPERS | - |
| Team Leader | TEAM_LEADER | Team Leaders | TEAM_LEADERS | - |
| Technical Director | TECH_DIRECTOR | Technical Directors | TECH_DIRECTORS | - |

## 业务角色（可自定义）

| 角色 | 角色代码 | 类型 | 说明 |
|------|----------|------|------|
| Manager | MANAGER | BU_UNBOUNDED | 部门经理 |
| User | USER | BU_UNBOUNDED | 普通用户 |

## 数据库表

### sys_roles
- `is_system = true` 的角色不可删除

### sys_virtual_groups
- `type = 'SYSTEM'` 的虚拟组不可删除
- `ad_group` 字段用于绑定 AD 组

### sys_virtual_group_members
- System Administrators 组必须至少有1个成员
- 管理员不能将自己从 System Administrators 组移除（如果是最后一个成员）

### sys_virtual_group_roles
- 虚拟组角色绑定表（一个虚拟组可以绑定多个角色）

## 禁止的操作

- ❌ 删除系统角色（is_system = true）
- ❌ 删除系统虚拟组（type = 'SYSTEM'）
- ❌ 将 System Administrators 组的成员数减少到 0
- ❌ 直接将角色分配给用户

## 正确的操作

- ✅ 创建 CUSTOM 类型的虚拟组
- ✅ 将角色绑定到虚拟组
- ✅ 将用户加入虚拟组
- ✅ 为虚拟组绑定 AD 组（ad_group 字段）
