# Issue 跟踪仪表盘

> **详细信息已迁移到 `.kiro/issues/index.yaml`**
> 本文件仅保留统计摘要和当前待处理清单。

## 统计 (截至 2026-04-03)

| 状态 | 数量 |
|------|------|
| ✅ Fixed | **135** |
| 🔓 Open | **0** |
| ⏸️ Wontfix | **1** |
| **总计** | **136** |

按严重度的分布见 `index.yaml` 各条目的 `severity` 字段。

---

## 当前待处理 (Open)

无。已全部关闭或标记为不修复。

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
