# Batch Assignment 空选择器修复

## 结论

已修复并部署。用户任务 `48877187-cc4d-11f1-89f2-0a31b3ca61b3` 的按人、按角色界面已实际打开核对；取消后确认没有 submit/complete 请求，数据快照未变化。真实人员、角色保存使用独立合成实例完成，不改动用户的演示数据。

## 根因与上轮验证遗漏

上轮把 Assignment 这个复合控件的必需选择器，误当成了必须由开发人员另外放入 children 的普通字段。已有实例只包含 miAssignment 标记时，能渲染模式卡片，却没有任何可填写的目标。

上轮成功的写入测试使用了显式配置三个子字段的合成 FU；旧实例只检查打开和取消，没有点击模式验证内部选择器。此前“runtime 不补选择器”的设计说明错误，已更正现有帮助文章及历史报告提示。

## 实现范围

- 新增 `frontend/shared/src/batch/assignmentControlRules.ts`：从冻结 assignmentConfig 的 assigneeField/buField/roleField 定义复合控件内部选择器，遵守 allowUser/allowRole。
- `BatchAssignmentEditor.vue:35` 在原生渲染前应用该控件规则；DW Batch Preview 与 Portal 共享生效。
- 自定义子字段保留其标签、样式、布局、只读和隐藏规则；只构造没有对应自定义规则的内部选择器，不重复添加。用户字段不按列名惯例猜测。
- 默认不修改和外层选择保持不变；Role 在需要 BU 而未选择 BU 时不可选。
- Portal/DW 各三语补齐标签，并更正缺少目标时的报错；帮助三语和 llms-full 同步。
- 不修改 FU、冻结快照、数据库结构、后端权限或工作流推进链。

## 红 → 绿证据

1. `SubTableBatchAssignmentEditor.test.ts`：点击模式后检查真实渲染的 BatchAssignmentInput。修复前两条失败（人员 0 个；BU/Role 数组为空），修复后通过。
2. 既有 marker-only 任务 `42f170a0-cc38-11f1-89f2-0a31b3ca61b3`，同一个 `verify-batch-assignment-controls.mjs`：
   - before-legacy：截图后断言失败，人员选择器 `0 !== 1`。
   - after-legacy：按人和按角色均 PASS，`hasAssignmentChildren=false`，只读检查无写入。
3. 用户实际任务 `48877187-cc4d-11f1-89f2-0a31b3ca61b3`：相同 marker-only 结构，两种模式均 PASS，无 pageerror/console.error，未保存修改，数据快照不变。

用户最初记录中的任务 791f36bd… 可读到相同空标记配置，但该页面没有可操作的子表行；没有将它的超时当作修复证据。用户提供当前 URL 后，已切换到当前任务验证。

## 实际保存与数据库核对

在旧合成 FU=50087 新建测试实例，保持其原始 marker-only Batch Form，不为测试补 children：

- 任务：`47f43d50-cc4e-11f1-89f2-0a31b3ca61b3`。
- 实例：`47e45e89-cc4e-11f1-89f2-0a31b3ca61b3`。
- 先选中第一行，选择人员，Apply 后正常保存，重新读取服务端确认第一行有人员，第二行未变。
- 再从已保存页面打开，选择 hase-hmdc 和 HMDC_Operator_Role，Apply 后重新读取服务端确认角色目标完整、旧人员字段已清空，第二行及普通业务字段未变。
- PostgreSQL 只读查询 `up_process_instance.variables.__subTables__`：
  - 第一行：First synthetic line，Amount=1，人员为空，BU=hase-hmdc，Role=HMDC_Operator_Role。
  - 第二行：Second synthetic line，Amount=2，人员/BU/Role 均未分派。
- 保存验证只写合成测试数据；未完成任务或创建 MI 子任务，不将“保存配置”声称为“子任务已创建”。

## 截图

- 修复前空框：`frontend/user-portal/verification-screenshots/2026-10-20_assignment-before-legacy-person.png`
- 用户实际任务按人：`frontend/user-portal/verification-screenshots/2026-10-20_assignment-after-user-person.png`
- 用户实际任务按角色：`frontend/user-portal/verification-screenshots/2026-10-20_assignment-after-user-role.png`
- 合成实例选人并保存：`frontend/user-portal/verification-screenshots/2026-10-20_assignment-after-live-person-chosen.png`
- 合成实例选 BU/Role 并保存：`frontend/user-portal/verification-screenshots/2026-10-20_assignment-after-live-role-chosen.png`

## 命令与结果

- Portal：`pnpm exec vitest run Batch batch assignmentControlRules --reporter=dot` → 20 文件，306/306。
- DW：`pnpm exec vitest run Batch batch miAssignmentWidget --reporter=dot` → 10 文件，79/79。
- 帮助：`node frontend/scripts/verify-help-portal.mjs` → 153/153。
- Portal、DW、help build 成功，重建三个 frontend Docker 服务并启动；最后一次部署后用户实际任务再测 PASS。
- `git diff --check` 无 whitespace 错误。新增共享控件规则和验证脚本未发现凭证/私钥/Bearer 字面量。

## Code Review

### 首轮 Findings / 逐条处置

- Major：`BatchAssignmentEditor.vue:35` 将没有 children 解释为没有选择器，导致可选模式无法填写。最小修复是按 assignmentConfig 渲染复合控件必需的选择器；已修，单测及同一旧任务红→绿。
- Major：旧 UI 测试没有真正渲染表单项子内容，也没有在旧实例点击模式。已修：表单项 stub 保留 slot；新增模式点击、选择器、BU 前置约束、不同列名映射和自定义规则保真用例；另加用户实际 URL 与 marker-only 实例保存验证。
- Minor：缺少目标时的错误仍提示弹窗行选择、帮助说明要求手工补子字段。已修：两应用三语及现有帮助同步。
- Blocker/Question：无新增未处置项。

### 重审

本轮增量与调用链重审：原生 Assignment 标记 → BatchAssignmentEditor → 配置映射的内部选择器 → 原目录接口 → 同一外层选择 → 原保存链。没有按 FU/表/业务列名分支，没有修改授权和行归属，没有向缺失配置猜测字段。

本轮已知 Findings 均已关闭。完整工程放行结论仍为 CONDITIONAL，不以这次修复声称全工程回归通过：

- 本轮相关构建、306/79 单测、真实用户 UI 和隔离保存/数据库核对均通过。
- 沿用上一轮已记录的范围外限制：完整 MI 回归缺少有效附件样本，全量 vue-tsc 存在存量错误。此次类型检查对新增 assignmentControlRules/分派组件路径无新诊断；未降低检查规则。
- Save/Load、Portal runtime 读取真实冻结配置已验证；Export/Import、Clone、Rollback、Admin 导入 N/A（未改配置格式、包、快照或映射链），没有声称进行了这些全流程测试。
- 不涉及性能优化；沿用目录防抖和已有请求链，无逐行业务请求。

## 未验证项

未在用户的演示实例上保存或推进流程，这是数据保护边界；实际写入在独立合成实例完成。未验证此任务完成后的 MI 子任务创建，未重新执行全套导入导出/回滚或所有权限主体组合；完整 MI 附件与全量类型门禁仍有上述存量缺口。
