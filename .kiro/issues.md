# Issue 跟踪仪表盘

> **详细信息已迁移到 `.kiro/issues/index.yaml`**
> 本文件仅保留统计摘要和当前待处理清单。

## 统计 (截至 2026-05-20)

| 状态 | 数量 |
|------|------|
| ✅ Fixed | **169** |
| 🔓 Open | **13** |
| ⏸️ Wontfix | **1** |
| **总计** | **183** |

按严重度的分布见 `index.yaml` 各条目的 `severity` 字段。

---

## 当前待处理 (Open)

| ID | 分类 | 描述 |
|----|------|------|
| 137 | architecture | developer-workstation 功能单元版本/部署双轨并存 |
| 141 | quality | init-scripts README 与 fu 种子 code 不一致 |
| 155 | quality | user-portal subTablePositionControl.property.test.ts 未注册 Element Plus 导致 Vitest unhandled errors |
| 156 | quality | user-portal vue-tsc 在当前工具链下启动失败，无法执行类型检查 |
| 157 | security | BpmnActionParser DOM 解析 BPMN 时未禁用 DTD/外部实体，存在 XXE 风险 |
| 158 | bug | user-portal PortalRelationTableServiceImpl.queryTableData 接收 search 参数但未用于过滤 |
| 161 | bug | ExportImportComponentImpl.importPackage 未设置 ProcessDefinition.functionUnitVersionId |
| 162 | bug | DeploymentService.deployFunctionUnit 未显式设置 ProcessDefinition.functionUnitVersionId |
| 168 | deploy | dev docker-compose n8n healthcheck 用 curl 但镜像无 curl，容器长期 unhealthy |

### Wontfix

| ID | 分类 | 描述 |
|----|------|------|
| 095 | quality | auth.ts 三端重复 — 无共享包，维持各应用内模块 |

---

## 复发问题模式（排查索引）

| 主记录 | 场景 | 相关 |
|--------|------|------|
| **#179** | MI 待办/申请：Link Form、表格下内联 **subtable2** 跨节点不带数、空白、id 串人、Current node 串行 | #177、#178 |

详情与根因清单见 `index.yaml` 中 `id: "179"`（`recurrence: pattern`）。改 `shared.ts` / `tasks/detail.vue` / `FormRenderer.vue` / `SubTableField.vue` 时优先跑上述单测并做三参与者手测。

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
