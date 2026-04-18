# 16 - 会议参与人信息收集 (Meeting Participant Info Collection)

## 概述

演示多实例子流程（Multi-Instance Sub-Process）动态任务分发功能的 Function Unit。

## 业务场景

会议组织者创建会议并添加参与人 → 为每位参与人分配处理人 → 系统自动为每位参与人创建独立的"填写参会信息"子任务 → 全部完成后流程结束。

## 流程

```
开始 → 创建会议 → 分配参与人 → [多实例子流程: 填写参会信息] → 收集完成
```

## 表单说明

- **创建会议**与**分配参与人**两个用户任务共用同一张 `Create Meeting Form`（`dw_form_definitions`，`form_type=PROCESS`）；分配节点通过 BPMN 扩展属性 `subTableName` / `assigneeField` 等驱动参与人子表与处理人分配，不再使用单独的 Assign 表单。

## 数据模型

| 表名 | 类型 | 说明 |
|------|------|------|
| meeting | MAIN | 会议基本信息（主题、时间、地点、组织者） |
| participants | SUB | 参与人列表（姓名、部门、邮箱、处理人、参会状态、饮食偏好） |

## 多实例配置

- **数据源子表**: participants
- **处理人字段**: assignee_user_id
- **集合变量**: multiInstance_participants_collection
- **执行模式**: 并行（PARALLEL）

## 执行顺序

```bash
psql -f 00-create-function-unit.sql
psql -f 01-create-tables.sql
psql -f 02-create-bpmn-process.sql
psql -f 03-form-table-bindings.sql
psql -f 04-update-bpmn-diagram.sql
psql -f 05-form-stage-bindings.sql
```

> **注意**: `04-update-bpmn-diagram.sql` 会覆盖 `02` 中的 BPMN XML 并补充可视化布局信息。
> 如果只执行到 `03`，流程设计器中会显示 "no diagram to display"。
> `05-form-stage-bindings.sql` 与 `03` 中的阶段绑定段落等价；若已执行完整 `03`，可跳过 `05`。

## 版本与初始化

- 功能单元 `current_version` 种子为 **1.0.4**（与 developer-workstation 已发布版本一致）；`version` 仍为 **1.0.0**。
- **全新 PostgreSQL 数据目录** 首次启动时，`00-init-all.sh` 会在 Digital Lending 种子之后自动执行本目录 `00`–`05`。
- 本地用 `psql` 手工初始化时，可运行仓库根目录 `deploy/init-scripts/init-database.ps1`（含本功能单元）。
- **已有数据卷** 的容器不会重跑 entrypoint；需自行对库执行上述 SQL 或使用 `99-maintenance/00-wipe-all-function-units.sql` 后再种子。
