# Issue 跟踪仪表盘

> **详细信息已迁移到 `.kiro/issues/index.yaml`**
> 本文件仅保留统计摘要和当前待处理清单。

## 统计 (截至 2026-05-27)

| 状态 | 数量 |
|------|------|
| ✅ Fixed | **0** |
| 🔓 Open | **15** |
| ⏸️ Wontfix | **1** |
| **总计** | **16** |

按严重度的分布见 `index.yaml` 各条目的 `severity` 字段。

---

## 当前待处理 (Open)

| ID | 分类 | 描述 |
|----|------|------|
| 137 | architecture | developer-workstation 功能单元版本/部署双轨并存 |
| 148 | quality | 三端 profile 页重复实现 languageLabelFor |
| 155 | quality | user-portal subTablePositionControl.property.test.ts 未注册 Element Plus 导致 Vitest unhandled errors |
| 156 | quality | user-portal vue-tsc 在当前工具链下启动失败，无法执行类型检查 |
| 157 | security | BpmnActionParser DOM 解析 BPMN 时未禁用 DTD/外部实体，存在 XXE 风险 |
| 158 | bug | user-portal PortalRelationTableServiceImpl.queryTableData 接收 search 参数但未用于过滤 |
| 162 | bug | DeploymentService.deployFunctionUnit 未显式设置 ProcessDefinition.functionUnitVersionId |
| 166 | quality | frontend/admin-center lint/typecheck 配置与 catch 变量引用存在问题 |
| 167 | quality | frontend/developer-workstation 三语 locale key 不一致 |
| 168 | deploy | dev docker-compose n8n healthcheck 用 curl 但镜像无 curl，容器长期 unhealthy |
| 1373 | security | user-portal 流程详情接口在未登录时仍可直接读取流程实例详情 |
| 1383 | bug | user-portal MI 子任务 To Do 的 subtable2 inline 表单串用上一参与者数据 |
| 1398 | performance | user-portal 前端 useTaskActions 把同一份 sub-table data 写 4–5 个别名 key 进 __subTables__，JSONB 体积 ×N |
| 1399 | performance | user-portal 后端 __subTables__ 合并器只"补缺失 alias"不去重，alias 集合单调增长（与 #1398 同根） |

### Wontfix

| ID | 分类 | 描述 |
|----|------|------|
| 095 | quality | auth.ts 三端重复 — 无共享包，维持各应用内模块 |

---

## 复发问题模式（排查索引）

| 主记录 | 场景 | 相关 |
|--------|------|------|
| **#179** | MI 待办/申请：Link Form、表格下内联 **subtable2** 跨节点不带数、空白、id 串人、Current node 串行 | #177、#178、#1383 |
| **#1392** | MI 子任务主表字段透传：BPMN previous-form BFS 误从 MI 子流程 startEvent 起跑 / 缺父子流程感知 / 同名约定无诊断；同时存在 SubTaskForm.vue 死代码副管线违反 portal-design-parity | #1396 |
| **#1396** | MI 字段透传 Path 2 readonly 快照剩余两条 BFS 缺口：`MI 子任务 → 后续主流程`（subProcess 内部 user task 不可达）+ `intra-MI`（同 MI 内 sub form1 → sub form2 兄弟不可达）；引入 subProcess 内部递归 + 内部 BFS 一并补齐 | #1392 |

详情与根因清单见 `index.yaml` 中 `id: "179"`（`recurrence: pattern`）。改 `shared.ts` / `tasks/detail.vue` / `FormRenderer.vue` / `SubTableField.vue` 时优先跑上述单测并做三参与者手测；最新一例（#1383）是当前参与者打开 sub form1 时 inline subtable2 被预填上一参与者的输入，根因在 `isolateMiSubTaskData` 重建 myRow.__subTables__ 时遗漏 MI 过滤、且 `syncMiLinkChildRowsIntoParentNested` 在空数据时不回写残留。#1392 修了 `useBpmnParser.ts:parseBpmnXmlAndGetPreviousFormIds` 让其与 `applications/detail.vue:parseBpmnXmlAndGetAllFormIds` 同构（主流程感知 + 父 subProcess break），并清除了 `SubTaskForm.vue + /sub-task-form-data` 死代码链。#1396 在 #1392 基础上把 BFS 进一步扩到「子流程内部 transitive descendants 自动当 previous」+「intra-MI 内部 BFS」，覆盖 MI 字段透传剩余的「MI 子任务 → 后续主流程」与「同 MI 内 sub form1 → sub form2」两条 readonly 快照缺口。改 `useBpmnParser.ts` 时优先跑 `useBpmnParserPreviousForms.test.ts`（8 case 含场景 1/2/2-regression/3/4 + boundary + dedup + missing-current）。

---

## 跟踪系统说明

issue 详情存储在 `.kiro/issues/index.yaml`，格式为机器可读的 YAML。

查看所有未修复问题：
```bash
grep "status: open" .kiro/issues/index.yaml
```

查看特定分类：
```bash
grep -A2 "category: security" .kiro/issues/index.yaml | grep "status: open"
```
