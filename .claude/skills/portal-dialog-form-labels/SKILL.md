---
name: portal-dialog-form-labels
description: >-
  Keep dialog/drawer form field labels fully visible on one line without wrapping,
  and never let inputs/selects after a label cover the label text. Applies to all
  three frontends: user-portal, admin-center, developer-workstation. Use when
  editing any el-dialog / el-drawer form, setting label-width, or when the user
  reports 弹窗字段折行 / 标签被输入框挡住 / label wrap / overlap in dialogs.
---

# 弹窗表单字段名（label）显示规范 — 三前端统一

**user-portal**、**admin-center**、**developer-workstation** 内弹窗（`el-dialog`）/ 抽屉（`el-drawer`）的字段名必须**一行完整显示、不折行**，且输入框 / 下拉框等**不得遮挡** label。

## 根因（不要再犯）

写死 `label-width="120px"` 等固定值 → 文字折行或被 content 区压住。仅加 `nowrap` 不够，须 **label 宽度按文字自适应**（Element Plus 2.4：有 label 时为 flex，改 `width:auto` + `nowrap` 即可）。

## 已生效的全局修复（三应用同一规则）

各应用 `src/styles/index.scss`（`main.ts` 全局引入）：

- `frontend/user-portal/src/styles/index.scss`
- `frontend/admin-center/src/styles/index.scss`
- `frontend/developer-workstation/src/styles/index.scss`

```scss
.el-dialog,
.el-drawer {
  .el-form:not(.el-form--label-top) .el-form-item__label {
    width: auto !important;
    max-width: none !important;
    white-space: nowrap;
    flex-shrink: 0;
  }
  .el-form--label-top .el-form-item__label {
    white-space: nowrap;
  }
}
```

覆盖**所有**弹窗表单，无需逐组件改。

## 编辑/新增弹窗表单

- **不要**为防截断把 `label-width` 往大调；对齐用 `label-width="auto"`。
- 长 label 靠自适应宽度，不要截断文字。

## 自检与验证

- [ ] 无写死折行风险的 `label-width` 像素值？
- [ ] 最长文案下一行完整、输入框不遮挡？
- [ ] 改可见 UI → `verify-ui-fix-with-screenshot`，截图存 `frontend/<app>/verification-screenshots/`

Portal mock 对比脚本：`frontend/scripts/verify-dialog-form-labels.mjs`（`--live` 仅 portal）。

## 典型弹窗（按应用）

| 应用 | 示例 |
|---|---|
| user-portal | `FormPopupDialog`、`SubTableAddDialog`、`permissions` 申请弹窗 |
| admin-center | `UserFormDialog`、`DictionaryFormDialog`、`FunctionUnitDeployDialog` |
| developer-workstation | `FormCreateDialog`、`ActionCreateDialog`、`FormDesigner` 预览弹窗 |
