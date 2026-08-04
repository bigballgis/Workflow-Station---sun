# Bugfix 需求文档

## 简介

在 Admin Center 的 Table Structure 模块中，表的状态生命周期管理存在多个缺陷。当前系统在创建表时将状态设为 `DRAFT`，编辑已部署的表后也将状态重置为 `DRAFT`，导致该表在 Table Data 页面中消失，用户无法查看和操作已有数据。此外，`disabled` 状态的表缺少对 Portal Visibility 按钮的禁用控制，Table Data 和 Admin Center 中也未过滤掉 `disabled` 的表。前端 Actions 按钮顺序也不符合用户期望。

本次修复需要引入 `INIT` 和 `UPDATED` 两个新状态，完善表的完整生命周期：`INIT → DEPLOYED → UPDATED → DEPLOYED`，并修复相关的可见性控制和 UI 问题。

## Bug 分析

### 当前行为（缺陷）

1.1 WHEN 用户在 Table Structure 中首次创建一个新表 THEN 系统将表状态设为 `DRAFT`，而非 `INIT`，无法区分"从未部署过的新表"和"已部署后被编辑的表"

1.2 WHEN 用户编辑一个状态为 `DEPLOYED` 的表结构 THEN 系统将表状态重置为 `DRAFT`，导致该表从 Table Data 页面的已部署表列表中消失，用户无法查看和操作已有数据

1.3 WHEN Table Data 页面加载已部署表列表 THEN 系统仅查询状态为 `DEPLOYED` 的表，不包含状态为 `UPDATED` 的表（即已部署后被编辑但尚未重新部署的表）

1.4 WHEN 一个表的 `enabled` 字段为 `false`（即被禁用） THEN 系统仍然在 Table Data 页面和 Admin Center 的数据视图中显示该表的数据

1.5 WHEN 一个表的 `enabled` 字段为 `false` THEN 前端的 Portal Visibility 开关仍然可以被编辑，用户可以将一个已禁用的表设为门户可见

1.6 WHEN 用户查看 Table Structure 列表的 Actions 列 THEN 按钮顺序为 Access、Deploy、Versions、Edit、Rollback、Delete，不符合用户期望的操作优先级顺序

### 期望行为（正确）

2.1 WHEN 用户在 Table Structure 中首次创建一个新表 THEN 系统 SHALL 将表状态设为 `INIT`，表示该表尚未被部署过

2.2 WHEN 用户编辑一个状态为 `DEPLOYED` 的表结构 THEN 系统 SHALL 将表状态设为 `UPDATED`（而非 `DRAFT`），该表仍然在 Table Data 页面中可见，且 Table Data 中展示的字段列应为上一次部署版本的字段

2.3 WHEN Table Data 页面加载已部署表列表 THEN 系统 SHALL 同时查询状态为 `DEPLOYED` 和 `UPDATED` 的表，确保编辑中的已部署表仍然可见

2.4 WHEN 一个表的 `enabled` 字段为 `false` THEN 系统 SHALL 在 Table Data 页面和 Admin Center 的数据视图中过滤掉该表，不显示其数据

2.5 WHEN 一个表的 `enabled` 字段为 `false` THEN 前端 SHALL 禁用该表的 Portal Visibility 开关，使其不可编辑

2.6 WHEN 用户查看 Table Structure 列表的 Actions 列 THEN 按钮顺序 SHALL 依次为：Edit、Delete、Deploy、Rollback、Version、Access

2.7 WHEN 用户对一个状态为 `UPDATED` 的表点击 Deploy THEN 系统 SHALL 将表状态更新为 `DEPLOYED`，并且 Table Data 中的字段列 SHALL 更新为最新部署版本的字段

### 不变行为（回归防护）

3.1 WHEN 用户对一个状态为 `INIT` 的表点击 Deploy THEN 系统 SHALL CONTINUE TO 正常执行首次部署流程，创建物理表并将状态更新为 `DEPLOYED`

3.2 WHEN 表状态为 `DEPLOYED` 且未被编辑 THEN 系统 SHALL CONTINUE TO 在 Table Data 页面中正常显示该表及其数据

3.3 WHEN 用户执行 Rollback 操作 THEN 系统 SHALL CONTINUE TO 正常回滚到指定版本，状态设为 `ROLLBACK`

3.4 WHEN 一个表的 `enabled` 字段为 `true` THEN 系统 SHALL CONTINUE TO 正常显示该表的数据，Portal Visibility 开关保持可编辑状态

3.5 WHEN 用户在 Table Data 页面中对已部署表执行增删改查操作 THEN 系统 SHALL CONTINUE TO 正常执行数据操作，不受状态生命周期修改的影响

3.6 WHEN 用户创建、删除表或管理版本历史 THEN 系统 SHALL CONTINUE TO 正常执行这些操作，不受本次修复的影响
