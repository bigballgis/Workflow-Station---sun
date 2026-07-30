---
name: function-unit-portability
description: >-
  Function Unit export/import/clone/version snapshot must carry ALL designer configuration
  so re-import, rollback, and clone do not fail or silently drop settings. Use when editing
  FunctionUnitExporter, FunctionUnitImporter, FunctionUnitCloner, VersionComponentImpl,
  *Portability classes, ExportManifest, or when the user mentions FU 导入导出 / clone / 打包缺失.
---

# Function Unit 可移植性 — 导入 / 导出 / Clone / 版本快照

**原则：** ZIP 导出、同名覆盖导入、Clone、版本快照/回滚 MUST 在目标环境还原 **与源 FU 等价的全部设计配置**；禁止「能导入但丢字段/丢规则/静默跳过」。

与 View 访问管控规则配套：`.cursor/skills/view-access-control/SKILL.md`。

---

## 三条路径（必须行为一致）

| 路径 | 入口 | View Design 实现 |
|------|------|------------------|
| **Export → Import** | `FunctionUnitExporter` / `FunctionUnitImporter` | `views/main_table_views.json` ← `MainTableViewPortability` |
| **Clone** | `FunctionUnitCloner` | `MainTableViewService.cloneViewsForFunctionUnit` |
| **Version snapshot / rollback** | `VersionComponentImpl` | snapshot `mainTableViews` ← `MainTableViewService.snapshotViewsForFunctionUnit` + `MainTableViewPortability.importAll` |

**Rollback 完整还原契约（含缺口矩阵、测试）：** `.cursor/skills/function-unit-version-rollback/SKILL.md`

新增 FU 级配置时 **三条路径都要补**，并在下方清单打勾。

---

## ZIP 包结构（ExportManifest.components）

| 文件 / 目录 | 内容 |
|-------------|------|
| `manifest.json` | 元数据 + `components`（含 `mainTableViews` 路径） |
| `process/process.bpmn` | 流程 BPMN |
| `tables/table_*.json` | 表结构 + FK 元数据 |
| `relations/table_relations.json` | 表关系 |
| `forms/form_*.json` | 表单 + binding + subTableViewConfig |
| `relation-tables/relation_tables.json` | 引用的 rt_ 结构 |
| **`views/main_table_views.json`** | **View Design：字段、排序/筛选、restrictToInvolvedUsers、accessRules** |
| `actions/action_*.json` | 动作 |
| `decisions/decision_*.dmn` | 决策 |
| `connections/`、`email-monitors/`、**`email-templates/`** | 邮件连接、监听规则、**Send Task HTML 模板** |

---

## Email Templates 可移植（MUST）

| 路径 | 行为 |
|------|------|
| **Export / Snapshot** | 始终写入 `emailTemplates`（无模板时写 `[]`）；ZIP 为 `email-templates/template_*.json` |
| **Import (DW)** | 解析并 `importEmailTemplate`；BPMN Send Task `emailTemplateId` 经 `BpmnIdRewriter` remap |
| **Clone** | 深拷贝模板并建立 id mapping；monitor 的 `connectionUid` / `targetFormId` / 数值 `targetBindingId` **映射失败必须抛业务异常**（禁止 `getOrDefault` 回退源 ID） |
| **Version rollback** | `clearChildCollectionsAndFlush` 删除模板后按快照重建；**旧快照缺 `emailTemplates` key** → 清空且不恢复（接受：回到无模板功能时代） |
| **Admin import** | 解析 `email-templates/`，以 `ContentType.EMAIL_TEMPLATE` 写入 **catalog content**（禁止静默丢弃；坏 JSON → 整次导入失败） |

**Admin catalog ≠ Send Email 运行时：** workflow-engine 仍从 **DW** `dw_email_templates`（Internal API）取模板。Admin 导入只保证包内模板进入 catalog、不丢文件；**不会**自动写入 DW 运行时表。跨环境要能发信，须另有 DW 导入/同步（本 skill 不把「Admin→DW 回写」算作当前 MUST）。

---

## View Design 可移植字段（MUST）

每个 view 条目：

| 字段 | 说明 |
|------|------|
| `mainTableName` | 按表名 remap（非 id） |
| `viewName` / `isDefault` / `status` | 基本元数据 |
| `sortConfig` / `filterConfig` | 含 toolbar |
| **`restrictToInvolvedUsers`** | 仅参与用户可见数据 |
| **`accessRules`** | BU + Role；见下节 |
| `fields` | 列定义 |

### accessRules 序列化

- **Export：** 从 `dw_main_table_view_access` **JDBC 读取**（禁止依赖 JPA lazy `view.getAccessRules()` — `findByFunctionUnitIdWithFields` 不 fetch access）。
- 每条规则：`targetType` + `targetId` + **`targetCode`**（BU/Role 的 `sys_*`.code，跨环境 remap）。
- **Import：** 优先 `targetCode` → 目标环境 id；fallback `targetId`（同库版本回滚）。
- **校验：** `MainTableViewAccessRulesValidator` — 空或 BU+Role 成对；禁止半配；code 无法解析 → `BIZ_VIEW_ACCESS_IMPORT_UNRESOLVED`。
- **Publish / Save / Clone：** 同样成对校验。

---

## Clone 专用

- `cloneViewsForFunctionUnit`：复制 fields、`restrictToInvolvedUsers`、access rules（`cloneAccessRules` via JDBC DTO）。
- 保存前 `MainTableViewAccessRulesValidator.validatePairedOrEmptyEntities`。
- 新 FU 上 view 状态为 **DRAFT**（与表 clone 一致）。

---

## 新增 FU 配置时的检查清单

改 Export/Import/Clone/Version 时逐项确认：

- [ ] **Exporter** 是否写入 ZIP / snapshot？
- [ ] **Importer** 是否在 tables 之后、forms 之前（或文档约定顺序）还原？
- [ ] **Importer** 是否 remap 名称/id（表名、rt 名、BU/Role code）？
- [ ] **Clone** 是否深拷贝并重写 BPMN/表单 id 引用？
- [ ] **Version rollback** 是否走同一 `*Portability.importAll`？
- [ ] 失败时是 **显式业务异常** 而非 `log.warn` 静默跳过？
- [ ] View access：是否 JDBC 导出 + 成对校验 + `restrictToInvolvedUsers`？
- [ ] `ExportManifest.components` 是否登记新组件路径？

---

## 代码 touchpoints

| 类 | 职责 |
|----|------|
| `FunctionUnitExporter` | 组装 ZIP + manifest |
| `FunctionUnitImporter` | 解析包 + 编排导入 |
| `FunctionUnitCloner` | 同库 clone + BPMN rewrite |
| `VersionComponentImpl` | snapshot/restore |
| `MainTableViewPortability` | View Design export/import |
| `RelationTableStructurePortability` | rt_ 结构 |
| `MainTableViewAccessRulesValidator` | BU/Role 成对校验（Save/publish/import/clone） |
| `MainTableViewServiceImpl` | Save、publish、clone、snapshot DTO |

---

## 测试

| 测试 | 覆盖 |
|------|------|
| `MainTableViewPortabilityTest` | JDBC 导出 access；import 未解析 code；半配拒绝 |
| `MainTableViewAccessRulesValidatorTest` | 成对/空规则 |
| `MainTableViewServiceImplTest` | updateView 成对 Save |
| `EmailPortabilityTest` | 模板 import；BPMN templateId remap；monitor 未映射 form/connection 抛错 |
| `ExportImportPropertyTest` / 手测 | 整包 round-trip |

手测：Export FU → 删/改 View access → Import 同名覆盖 → Portal 验证 View 菜单与 access 与导出前一致。

---

## 禁止

- 仅 UI Save 校验、import/clone 不校验 access 成对。
- Export 用 JPA 读 access rules（未 fetch → **空数组**）。
- Import 对无法 remap 的 access code **warn 跳过**导致半配或 admin-only 静默降级。
- 新配置只加 Exporter 不加 Importer/Clone/Version。
- 同名 re-import / rollback 时 **`clearChildCollectionsAndFlush` 未删** `dw_email_connections` / `dw_email_monitor_rules`（`connection_uid` / `rule_uid` 全局唯一 → 重复 INSERT 失败）。

---

## Re-import 清理（email）

`VersionComponentImpl.clearChildCollectionsAndFlush` MUST 在重建内容前：

1. `emailMonitorRuleRepository.deleteByFunctionUnitId`
2. `emailConnectionRepository.deleteByFunctionUnitId`
3. `emailTemplateRepository.deleteByFunctionUnitId`
4. `tableRelationRepository.deleteByFunctionUnitId`（与 tables 一并重建）

不能依赖 `functionUnit.getEmailConnections().clear()` alone — 集合常为 lazy 未加载，旧行会留在库中。

---

## 参考

- View 业务规则：`.cursor/skills/view-access-control/SKILL.md`
- 手测：`docs/view-access-control-test-guide.md`
