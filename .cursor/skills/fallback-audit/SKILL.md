---
name: fallback-audit
description: >-
  Re-scan the repo for silent-fallback anti-patterns using the fixed 2026-07 audit methodology and
  compare against the recorded baseline to measure debt reduction. Use when the user asks to re-audit
  fallbacks, measure governance progress, check 兜底密度 / 吞错基线 / 治理降幅, or after completing a
  remediation batch (task-assignment chain, audit-field unification, DW save path, R1/R2 extraction).
---

# 兜底密度复扫 — 固定口径，对照基线

目的：用与 2026-07-10 首次审计**完全相同的口径**重扫，输出各指标降幅。口径不许改——改了就没有可比性；
若确需修订口径，旧口径跑一次、新口径跑一次，两组数字都报告，并更新本文件。

## 基线（2026-07-10）

| 指标 | 口径 | user-portal | admin-center | developer-workstation | 后端合计 |
|---|---|---|---|---|---|
| F1 吞空 | `\|\| []` / `\|\| {}`（.ts/.vue, src/ 下, 排除 node_modules/dist） | 310 | 59 | 345 | — |
| F2 log-only catch | catch 块内仅 console.* 无 rethrow；含空 catch | 72 | 13 | 52 | — |
| F3 catch→默认 | `catch { return []/null/{}/''/false }` 单行形态 | 14 | 12 | 14 | — |
| B1 catch 总数 | `catch (Exception|Throwable|RuntimeException|Error` 仅 src/main | — | — | — | 922 |
| B2 log-only catch | catch 后仅 `log.*` 单语句无 rethrow | — | — | — | ~294 |
| B3 catch→默认 | catch 后 return null/emptyList/emptyMap/Optional.empty/false/"" | — | — | — | ~60 |
| B4 orElse 兜底 | `.orElse(null)` / `.orElse("system")` / `.orElseGet` | — | — | — | 150 |

后端 B1 按模块：workflow-engine-core 314 / user-portal 286 / admin-center 138 / developer-workstation 107 /
platform-common 46 / platform-security 25。

## 扫描步骤

1. **前端三项（F1-F3）**：对 `frontend/{user-portal,admin-center,developer-workstation}/src` 分别跑
   Grep（用 Grep 工具，不用 shell grep）：
   - F1：pattern `\|\|\s*(\[\]|\{\})`，type ts + glob `**/*.vue`，output_mode count
   - F2：pattern `catch[^}]*\{\s*(console\.\w+\([^)]*\);?)?\s*\}`，multiline true
   - F3：pattern `catch\s*(\([^)]*\))?\s*\{\s*return\s+(\[\]|null|\{\}|''|""|false)`，multiline true
2. **后端四项（B1-B4）**：对 `backend/*/src/main` 跑：
   - B1：pattern `catch\s*\((Exception|Throwable|RuntimeException|Error)\b`，type java，count 按模块汇总
   - B2：pattern `catch\s*\([^)]+\)\s*\{\s*log\.\w+\([^;]*\);\s*\}`，multiline true
   - B3：pattern `catch\s*\([^)]+\)\s*\{[^}]*return\s+(null|Collections\.empty\w+\(\)|Optional\.empty\(\)|false|"")`，multiline true
   - B4：pattern `\.orElse\((null|"system")\)|\.orElseGet\(`
3. **排除合理项**（与首扫一致，不计入反模式）：i18n 目录（`src/i18n/**`）、`utils/sso.ts`、
   `utils/httpErrorMessage.ts`、`api/request.ts`、测试文件（`__tests__`、`*.test.ts`、`src/test`）、
   portal 各 `WorkflowEngine*Client.java` 与跨服务 HTTP 客户端的 `Optional.empty()` 降级、
   `platform-cache` Redis 回源、审计/通知旁路（AdminAuditAspect 等）。
   注意：**AdminCenterClient（workflow-engine-core）不在豁免内**——它是分派权威数据源。
4. **重灾区跟踪**（逐文件报数，观察是否清零）：DW `useFormSave.ts`、`savedFormPreviewBuilder.ts`、
   `useFormPreviewBuild.ts`；portal `miLinkChildRows.ts`、`bpmnDiagramParser.ts:514`、
   `relationFields.ts`(mergeMissingTableFieldColumns)；后端 `AdminCenterClient.java`、
   `TaskAssignmentListener.java`、`WorkspaceTaskFilterComponent.java`、`TaskOrphanRepairService.java`、
   `RelationTableDataServiceImpl.java`、`SubTableEnrichmentComponent.java`。
5. **parity 副本抽查**：grep `mirrors|aligns with|与.*一致` 注释计数（首扫为普遍现象未计数，本次起记录）；
   确认 R1（tableFkRuntime 5 份）、R2（子表 PK 行键 3 份）、R5（审计字段 4 份）份数是否减少。

## 输出格式

一张对照表：指标 | 基线 | 本次 | 降幅%，按 app/模块分组；重灾区文件逐个列"基线 N → 本次 M"；
新增豁免注释 `FALLBACK(...)` 的数量与依据抽查（无依据的标签 = 违规，单独列出）。
结论一句话：整体降幅 + 是否有指标反弹（反弹 = 有 agent 违反 error-handling-governance 规则，列出提交）。

## 数据口径备注

- F2/B2 的 multiline 正则只捕获单语句形态，多语句 log-only catch 会漏计——首扫同样漏计，可比性不受影响。
- 正则计数含少量误报（如合理的可选 props 默认值），趋势比绝对值重要。
- 若要深挖某文件，派 Explore agent 人工判读，不要放宽正则。
