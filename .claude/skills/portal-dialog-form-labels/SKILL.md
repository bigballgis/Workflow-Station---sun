---
name: portal-dialog-form-labels
description: >-
  Keep dialog/drawer form field labels fully visible on one line without wrapping,
  never let inputs/selects after a label cover the label text, and keep the left
  edges of all inputs in the same form aligned (uniform label width). Applies to all
  three frontends: user-portal, admin-center, developer-workstation. Use when
  editing any el-dialog / el-drawer form, setting label-width, or when the user
  reports 弹窗字段折行 / 标签被输入框挡住 / 输入框不对齐 / label wrap / overlap /
  misaligned inputs in dialogs.
---

# 弹窗表单字段名（label）显示规范 — 三前端统一

**user-portal**、**admin-center**、**developer-workstation** 内弹窗（`el-dialog`）/ 抽屉（`el-drawer`）的表单必须同时满足：

1. 字段名**一行完整显示、不折行**；
2. 输入框 / 下拉框等**不得遮挡** label；
3. **同一表单内各行输入框 / 下拉框左边缘对齐**（label 宽度统一）。

## 根因（不要再犯）

- 写死过小的 `label-width`（如 `120px`）→ 长文字折行或被 content 区压住。
- 用 `width: auto !important` 一刀切覆盖 → 每个 label 变成各自文字宽度，**每行输入框起点不一、对齐被破坏**（曾在 Portal Add Record 弹窗复现）。

正确做法：**保留表单统一的 `label-width` 作为对齐基准**，仅用 `min-width: max-content` 兜底——短 label 保持统一宽度（输入框对齐），超长 label 撑开自身（不折行、不遮挡）。

## 已生效的全局修复（三应用同一规则）

各应用 `src/styles/index.scss`（`main.ts` 全局引入）：

- `frontend/user-portal/src/styles/index.scss`
- `frontend/admin-center/src/styles/index.scss`
- `frontend/developer-workstation/src/styles/index.scss`

```scss
.el-dialog,
.el-drawer {
  .el-form:not(.el-form--label-top) .el-form-item__label {
    min-width: max-content;   // 不折行、不遮挡；不覆盖 label-width，保住对齐
    max-width: none !important;
    white-space: nowrap;
    flex-shrink: 0;
  }
  .el-form--label-top .el-form-item__label {
    white-space: nowrap;
  }
}
```

覆盖**所有**弹窗表单，无需逐组件改。组件内**禁止**再写 `width: auto !important` / `max-width: <px>` 之类盖过该规则（developer-workstation 的 `FormPreviewItems.vue`、`FormDesigner.vue` 预览区已按此修正）。

## 编辑/新增弹窗表单

- 弹窗/抽屉内 el-form **一律 `label-width="auto"`**（EP 2.13 测量最长 label、用 margin 补齐 → 各行输入框严格左对齐，任何超长 label 也不破坏对齐）。三应用已全量清扫，新增弹窗照此写，**不要**再写固定像素。
  - 例外：设计器/后端下发的 labelWidth 配置可保留（本身统一），fallback 用 `'auto'`。
- **不要**为个别 label 单独调宽度，**不要**给 `.el-form-item__content` 加 margin 兜底。
- 长 label 靠 `min-width: max-content` 自动撑开，不要截断文字（禁 `text-overflow: ellipsis` 配小 `max-width`）。

## 自检与验证

- [ ] 无写死折行风险的过小 `label-width`？
- [ ] 最长文案下一行完整、输入框不遮挡？
- [ ] 同一弹窗内所有输入框左边缘对齐（短 label 行与长 label 行起点一致）？
- [ ] 改可见 UI → `verify-ui-fix-with-screenshot`，截图存 `frontend/<app>/verification-screenshots/`

Portal mock 对比脚本：`frontend/scripts/verify-dialog-form-labels.mjs`（`--live` 仅 portal）。

## 典型弹窗（按应用）

| 应用 | 示例 |
|---|---|
| user-portal | `FormPopupDialog`、`SubTableAddDialog`（Add Record）、`permissions` 申请弹窗 |
| admin-center | `UserFormDialog`、`DictionaryFormDialog`、`FunctionUnitDeployDialog` |
| developer-workstation | `FormCreateDialog`、`ActionCreateDialog`、`FormDesigner` 预览弹窗 |
