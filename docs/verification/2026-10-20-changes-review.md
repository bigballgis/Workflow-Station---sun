# Code Review 结果

> 提交决定（2026-10-20）：用户阅读本报告后明确要求“提交吧，全仓类型检查不属于这次改动”。据此提交当前已审查的暂存改动，不扩大全仓类型治理范围。上述历史审查结论与 MI 附件样本缺口保留，不改写为完整门禁 PASS；仅创建本地提交，不推送远端。下文“未提交”为该轮审查完成时的状态。

## 结论

CONDITIONAL。已审查 Changes 中的 Batch 外层选行、原生 Batch Form、真实 Assignment、Flow History 展示及其测试、脚本、帮助、依赖改动（暂存前 63 文件，包括此前未跟踪文件）。本报告也暂存。

用户要求“没问题就 commit”。尚未满足完整门禁，因此没有 commit，也没有 push；相关改动保留在暂存区，未丢弃任何原有工作。

## Findings

### Blocker

- 本轮增量未发现新增的业务正确性、安全或数据损坏 Blocker。
- 全仓类型检查不是绿灯，见下方门禁限制；不能把该声明理解为整个仓库类型正确。

### Major / 验证缺口

- 完整 MI 截图回归未通过。最近一次 `pnpm run regression:mi` 的单测 234/234、前两个截图场景通过，第三个 `verify-mi-attachment-rows.mjs` 返回 `no To Do or My Request with attachment rows`，后续场景未运行。涉及 Portal SubTableField/任务详情等消费者，必要运行证据仍不完整。需要准备可用的独立附件样本后重跑，不以专项单测替代该门禁。
- `vue-tsc --noEmit` 全仓失败。复查本轮共享 Batch/历史展示组件无对应诊断，Portal SubTableField 仍报既有 nullable 参数和未使用 FieldRenderer 导入；HEAD 中可以查到相同旧代码。尚未取得整个候选改动相对干净基线的完整类型差异证据，不宣称全部报错已证明与本轮无关。未降低检查规则或扩大为全仓类型修复。

### Minor

- `frontend/scripts/verify-batch-outer-selection.mjs` 仍断言弹窗包含 Selected rows。新交互不应有该提示；已改为检查弹窗和外层工具栏均不含此文案，并保留后续无修改 Apply 的持久化断言。
- `frontend/shared/src/batch/batchFormRules.ts`、`useBatchSelection.ts` 新文件末尾多余空行，在暂存后的 `git diff --cached --check` 首次发现；已删除，复查通过。

### Question

- 无需求方向上的未决问题。是否接受上述现存门禁缺口仍需用户明确决定，不能自行视为已接受。

## 逐条处置与重审

- Minor 均已修复；`verify-batch-assignment-controls.mjs` 同步增加“不显示 Selected rows”断言。
- `SubTableField.vue` 只删除工具栏选中数量的 span，保留复选框、选择身份、按钮禁用条件与 selectedRowIds 传递。
- 增量重审以及暂存完整范围复查后，未发现需要另外修改的产品缺陷；两个验证缺口未关闭，结论仍 CONDITIONAL，未提交。

## 范围 / 验收映射

- 外层稳定行身份选择 → BatchWorkspace 冻结选择 → 填一次应用到选中行；新增/删除分开操作。
- BatchDesignedForm 使用保存的 rule/options，保留开发人员定义的布局和样式；默认值只展示，不自动修改已有数据。
- Assignment 复合控件按冻结配置提供人员/BU/Role 选择器，兼容旧 marker-only 表单；保留自定义子规则。真实保存验证见同目录 batch-assignment-controls 报告。
- Flow History 按 BPMN 层级过滤子流程边界，仅影响时间线，不移除流程图或快照依赖的原始历史。
- Portal/DW 包装、解析配置、三语帮助、依赖和 lockfile 一同纳入，没有遗漏新源码。

## 触发矩阵（命中项）

- code-review、最小 diff、前端构建/测试、Portal MI、设计 parity、冻结配置/行身份、弹窗标签、安全/增量敏感信息。
- 本次没有修改后端接口、数据库、FU 包/快照 schema、鉴权规则或服务部署配置。

## 编译与静态检查

- Portal `pnpm run build`：PASS，built in 32.78s；已重建并启动 user-portal-frontend。
- DW `pnpm run build`：PASS，built in 20.04s。
- Help 构建/部署和 153/153 UI 检查：引用本会话前序已经执行并记录的证据，本次删除 Portal 文案未修改 Help 产物，不重复宣称重跑。
- `git diff --cached --check`：修正两处新增空行后 PASS。
- 类型门禁：FAIL，详见 Major/验证缺口。

## 测试与运行证据

- Portal 全量：`pnpm exec vitest run --reporter=dot` → 317 文件，2389/2389 PASS。
- Portal 专项：Batch/Assignment/历史等 → 23 文件，320/320 PASS。
- DW 专项：`pnpm exec vitest run Batch batch miAssignmentWidget --reporter=dot` → 10 文件，79/79 PASS。
- 本轮未执行 DW 全量单测；没有用专项数字冒充全量。
- 删除文案部署后，`verify-batch-assignment-controls.mjs` 在独立合成任务执行只读检查：人员/角色选择器正常，工具栏和弹窗均无 Selected rows，取消无保存请求，服务端行快照不变。
- 额外只读检查：外层选中行后 Batch Edit 仍可点击。已人工查看截图：
  `F:/hermes1.0/Workflow-Station---sun/frontend/user-portal/verification-screenshots/2026-10-20_batch-no-selected-rows-label.png`。
- 两种模式截图：同目录 `2026-10-20_assignment-after-no-selection-label-person.png`、`2026-10-20_assignment-after-no-selection-label-role.png`。
- 先前同路径真实任务历史红→绿、隔离 CRUD/Assignment 保存和 DW 预览证据见同目录四份专题报告；此次审查不把它们说成本轮重新执行。

## Function Unit 通用性与生命周期

- 通用性：配置主键/平台身份、冻结 Batch Form/assignmentConfig 及部署 BPMN 驱动，没有业务 FU/表/字段名分支。
- 双样本：前序报告已记录合成新 FU 和旧实例的实际运行证据，且分派测试使用不同于用户实例的字段映射。
- Save/Load：当前保存配置和现有运行时契约不变；Portal/DW 消费者同步。
- Export/Import、Clone、Version/Rollback、Admin 两条 Import：未修改配置格式、包或快照映射，无新增字段需迁移；本轮未执行生命周期 round-trip，不声称其全链路已验证。
- Portal runtime/parity：原生表单共享；本轮专项/全量单测与已部署只读检查通过，完整 MI 截图门禁仍有缺口。

## 增量敏感信息

- 范围：全部暂存新增/修改行以及新增源码、脚本、文档；未创建 commit，无新 commit message。
- 高置信凭证/私钥模式扫描无匹配，并检查验证脚本沿用登录 helper、外部任务 ID 校验及合成实例写入限制。
- 浏览器 XML 只读解析，不插入活动 DOM、不执行脚本，不新增服务器 XML/SQL/LDAP 接口。
- 没有新增硬编码凭证；截图为本地验证产物，没有强制添加被忽略的截图。

## 性能

- 不是性能优化任务，不宣称性能提升。批量上限 200；人员搜索沿用目录防抖；无逐行新增目录请求。
- 历史节点集合按 BPMN computed 缓存，不新增网络请求；完整原始历史仍由原 loader 提供。

## 残余风险 / 豁免

- 未豁免或掩盖类型检查与 MI 截图缺口，故不 commit；待修复门禁或用户明确接受风险后再决定提交。
- 工作区及暂存区保留全部改动，不 stash/reset 用户工作。

## 范围外问题

- 现存全仓类型检查错误和 MI 回归样本准备没有在本次 UI 文案删除任务中扩大治理。
