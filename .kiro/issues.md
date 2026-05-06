# Issue 跟踪仪表盘

> **详细信息已迁移到 `.kiro/issues/index.yaml`**
> 本文件仅保留统计摘要和当前待处理清单。

## 统计 (截至 2026-05-06)

| 状态 | 数量 |
|------|------|
| ✅ Fixed | **151** |
| 🔓 Open | **7** |
| ⏸️ Wontfix | **1** |
| **总计** | **159** |

按严重度的分布见 `index.yaml` 各条目的 `severity` 字段。

---

## 当前待处理 (Open)

| ID | 分类 | 描述 |
|----|------|------|
| 137 | architecture | developer-workstation 功能单元版本/部署双轨并存 |
| 141 | quality | init-scripts README 与 fu 种子 code 不一致 |
| 152 | bug | user-portal tasks 页 submitAction 捕获异常后仍弹 success |
| 155 | quality | user-portal subTablePositionControl.property.test.ts 未注册 Element Plus 导致 Vitest unhandled errors |
| 156 | quality | user-portal vue-tsc 在当前工具链下启动失败，无法执行类型检查 |
| 157 | security | BpmnActionParser DOM 解析 BPMN 时未禁用 DTD/外部实体，存在 XXE 风险 |
| 158 | bug | user-portal PortalRelationTableServiceImpl.queryTableData 接收 search 参数但未用于过滤 |

### Wontfix

| ID | 分类 | 描述 |
|----|------|------|
| 095 | quality | auth.ts 三端重复 — 无共享包，维持各应用内模块 |

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
