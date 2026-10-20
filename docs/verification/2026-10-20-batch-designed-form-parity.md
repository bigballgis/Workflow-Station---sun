# Batch Form 按设计渲染：验证与审查

> 后续纠正：本报告对空 Assignment 标记“不生成选择器”的设计判断有误，且只读旧实例检查未验证模式切换后的选择器。该遗漏已由用户实际任务复现。请以同目录 `2026-10-20-batch-assignment-controls.md` 的修复证据为准；下文保留本轮历史记录，不代表该空控件曾验证通过。

日期：2026-10-20。范围：本轮 Portal/DW Batch Form 渲染对齐增量；工作区还包含上一轮外层选择改动，未提交或暂存任何文件。

## 验收与实现

- Portal 和 DW Batch Preview 使用同一份 `BatchDesignedForm.vue`，复用 DW 已使用的 form-create 3.2.37；Portal 增加相同运行时依赖，lockfile 已同步。
- 按保存的 rule/options 渲染控件、嵌套布局、样式、标签、尺寸、必填标记及条件规则。保留自定义 labelWidth，不套用弹窗固定宽度。
- 移除独立的草稿说明、选中数量段落、逐字段“不修改/清空/恢复”操作行；不再用手工输入控件覆盖设计属性。
- Assignment Mode 控件的视图从 DW 提取为共享组件，DW 画布、DW 预览和 Portal 使用同一实现。人员、BU、Role 只渲染设计中实际放置的子规则，不自动添加缺失字段。
- 初始化默认值只展示，不产生批量修改意图。用户填写一次，只更新外层选中行。取消后重开重新初始化。
- 保留权限、PK/FK/计算字段保护、草稿新鲜度检查、原有正常保存链及实例冻结设计。不修改后端、数据库结构或用户已有 FU 设计。

主要文件：

- `frontend/shared/src/batch/BatchDesignedForm.vue`：原生配置渲染与运行时适配。
- `frontend/shared/src/batch/BatchWorkspace.vue`：选择快照、显式修改意图、校验及应用。
- `frontend/shared/src/batch/BatchAssignmentEditor.vue`、`BatchAssignmentInput.vue`：设计子字段和目录交互。
- `frontend/shared/src/batch/BatchAssignmentModeFrame.vue`：DW 与 Portal 共用的控件视图。
- `frontend/shared/src/batch/batchFormRules.ts`、`DesignedFormTitle.vue`：布局别名与原有标题控件。
- Portal/DW 对应封装、构建/测试解析配置和 Portal 依赖声明。
- 现有 Sub-Table 帮助文章三语及 llms-full 同步；保留原帮助入口。

## 修复验证

### 复现与根因

原 BatchWorkspace 额外生成提示段落和逐字段操作行；手工输入控件强制宽度；分派表单没有渲染设计容器中的子规则。两端由不同的呈现逻辑决定表单外观。

### 红 → 绿证据

- `SubTableBatchInputConstraints.test.ts`：修改前“不得额外添加字段意图操作行”失败（`expected true to be false`）；原生渲染后通过。
- 同一测试文件的分派样式透传用例：临时移除适配层根节点的属性透传，得到 `expected false to be true`；恢复后 9/9 通过。该用例断言配置的 421px 宽度及 19px margin 均保留。
- 自定义 row.gutter=8 的只读截图检查：修复前 body scrollWidth=932、clientWidth=928；按配置补偿原生网格负边距后相同实例检查为 928/928。失败截图保留为 `2026-10-20_batch-outer-custom-designed-before-gutter.png`。

### 执行结果

- Portal：`pnpm exec vitest run Batch batch --reporter=dot` → 19 文件，300/300。
- DW：`pnpm exec vitest run Batch batch miAssignmentWidget --reporter=dot` → 10 文件，79/79。
- MI 单元：`pnpm exec vitest run --config vitest.mi-regression.config.ts --reporter=dot` → 29 文件，234/234；不等同于完整 MI 回归通过。
- `node frontend/scripts/verify-help-portal.mjs` → 153/153。
- Portal/DW/help 均执行 `pnpm run build`，实际输出 `built in`；随后重建对应 Docker frontend 服务，容器启动日志正常。构建保留已有大 chunk/Sass 提示，没有降低规则。
- `git diff --check` → 无 whitespace 错误。

### 真实任务与双样本

`verify-batch-designed-form.mjs` 在新建、标记为 batch-verification-synthetic 的 FU 上运行，最终 PASS：

- 按真实保存的配置核对原生数字控件 computed width=173px、人员控件=287px。
- 核对背景、卡片、标签、控件顺序和 Assignment 容器配置；保留 DW 实际设计画布截图。
- 初始化显示 Designed default/5，但不操作直接 Apply：没有 submit 请求，服务端数据不变。
- 仅选择第一行、只修改 Amount：第一行 Amount=0，Caption 保持原值；第二行业务值和身份不变。
- 按设计放置的人员控件选择分派目标并正常保存；仅第一行分派变化，第二行不变。
- body clientWidth=scrollWidth=928；pageerror 与 console.error 均为空。

最终测试任务：`7c203532-cc3e-11f1-89f2-0a31b3ca61b3`，实例 `7c12ee7b-cc3e-11f1-89f2-0a31b3ca61b3`。测试设计 FU=50089，未改动业务 FU。

PostgreSQL 只读复核 `up_process_instance.variables.__subTables__`，限定上述实例及合成表：

- 第一行：Caption=First synthetic line，Amount=0，assigned=true。
- 第二行：Caption=Second synthetic line，Amount=2，assigned=false。

已有实例兼容检查：`verify-batch-outer-readonly.mjs` 在既有任务 `05f5addd-cb68-11f1-967e-82c0efc60df9`（developer）和已有批量分派测试任务（另一测试主体）上只选择、打开、取消，不写数据；均无横向溢出或 pageerror。

DW：`verify-batch-outer-preview.mjs` 在既有合成 FU=50086 验证批量新增、选中一行统一修改、未选中行不变；PASS，未调用保存或主键预留 API。

测试创建的合成 FU=50088、50089 及测试实例保留，便于复查；没有自动删除用户数据。50088 的设计更新后遇到一次部署上下文变化保护，未绕过该保护；使用独立的新合成设计完成验收。

### 截图

- Portal 自定义设计：`frontend/user-portal/verification-screenshots/2026-10-20_batch-designed-portal.png`
- Portal 设计中的分派字段：`frontend/user-portal/verification-screenshots/2026-10-20_batch-designed-assignment.png`
- DW 同一设计画布：`frontend/developer-workstation/verification-screenshots/2026-10-20_batch-designed-dw-canvas.png`
- DW 批量运行预览：`frontend/developer-workstation/verification-screenshots/2026-10-20_batch-outer-dw-native-edit.png`
- 已有实例：`frontend/user-portal/verification-screenshots/2026-10-20_batch-outer-existing-native-after.png`

## Code Review 结果

### 结论

CONDITIONAL。本轮已发现问题均已处置；必要 UI 与真实保存验证通过。完整 MI 回归受附件样本缺失阻塞，全量 vue-tsc 有范围外存量错误，不能声称完整工程门禁 PASS。

### 首轮 Findings 与逐条处置

#### Blocker

- 无已知未处置项。

#### Major

- `BatchDesignedForm.vue:27`：插槽适配器返回 Fragment，原生控件的 style/class 无法继承。
  - 反例：分派容器配置 width=100%、marginTop=17px，运行时仍按内容收缩，间距丢失。
  - 影响：仍违反“按 DW 自定义设计显示”。
  - 最小修复：适配器使用单根节点显式传递原生 attrs；不写固定表单尺寸。
  - 处置：已修。样式透传用例红→绿，最新真实截图已复核。

- `BatchDesignedForm.vue:2`：原生 row.gutter 的负 margin 超出弹窗内容边界。
  - 反例：自定义 gutter=8 时横向多出 4px。
  - 影响：设计布局出现非预期横向滚动。
  - 最小修复：外边界按配置的半 gutter 补偿；不改写设计 row 配置。
  - 处置：已修。真实 body 宽度检查 932/928 → 928/928。

- `BatchWorkspace.vue:61`、`BatchAssignmentEditor.vue:46`：只读布局祖先及冻结分派字段也须进入应用判据，不能只禁用叶控件。
  - 反例：伪造叶控件 update 或选择会清理受保护的分派字段的模式。
  - 影响：展示与批量应用判据不一致。
  - 最小修复：规则遍历继承只读；分派 tuple 包含冻结字段时保持不可变；原生字段适配也保留继承的 disabled。
  - 处置：已修。嵌套只读与冻结字段用例通过；服务端授权链未变。

- `BatchAssignmentInput.vue`：目录控件更新 uniform 状态时，也需向原生字段模型发出更新。
  - 反例：原生表单中的必填/联动仍读取旧的人员或角色值。
  - 影响：自定义校验与实际选择值不一致。
  - 最小修复：通过已有适配器 update 同步值；BU 同步配置中的业务代码，不将目录查询 ID 当成持久化代码。
  - 处置：已修。最新构建后真实人员分派、持久化读回、控制台零错误验证通过。

#### Minor

- 原手工 BatchFieldInput 与布局渲染树不再使用。
  - 处置：已删除本轮替代的死实现；布局识别与遍历保留在 batchFormRules.ts。未清理范围外存量代码。

#### Question

- 无待用户决定的业务分叉。保持既有实例冻结设计是原契约，不改为加载 DW 最新草稿。

### 重审结论

已对处置增量和本轮完整调用链重新检查：DW/Portal 的封装 → BatchWorkspace → 原生规则渲染 → 分派子字段 → intent/assignment 收集 → 原正常保存链。上述 Findings 已关闭；没有已知未接受的本轮 Major。结论仍为 CONDITIONAL，原因见下。

### Function Unit 通用性与生命周期

- 通用性：由规则、options、assignmentConfig 字段映射及冻结权限驱动；没有按具体 FU、表或业务列名分支。新建与已有实例均有运行证据。
- Save/Load：消费现有保存结构，不改设计持久化格式；真实 DW 设计加载及 Portal 冻结配置读取已验证。
- Export/Import、Clone、Version/Rollback、Admin 两条导入：N/A（没有新增设计属性或修改包、快照、ID 映射/解析链）。本轮不声称进行了这些生命周期的全流程测试。
- Portal runtime/parity：新设计、已有实例及 DW 预览已验证；实例冻结行为保留。

### 安全、依赖及性能

- 产品代码无特定业务 FU/字段名判据；目录字段读取配置；敏感值未写入新源码。
- 新依赖为 DW 已使用的同版本 MIT 运行时，不引入第二个设计器或未知库；lockfile 只有新增运行时依赖条目。
- 检查新增共享渲染源码和验证脚本，未发现 token、私钥、实际密码或 Bearer 字面量；未提交或推送。
- 遍历本次打开的 Batch Form，字段值没有新增后端逐行请求；目录远程查询沿用原目录 composable 与防抖。未宣称性能提升。

## 未验证项 / 范围外问题

- 完整 `pnpm run regression:mi`：单元通过，前两项截图场景通过，随后 `verify-mi-attachment-rows.mjs` 报 `no To Do or My Request with attachment rows`，整套 FAIL。需要补充有效附件样本后重跑，不能将 unit-only 当完整通过。
- Portal/DW 全量 vue-tsc 未通过：共享测试依赖解析、computedField/functionsText 联合类型、DW AI/debug/旧测试等已有诊断仍存在；本轮批量渲染路径的类型诊断已消除。未降低 strict 或 suppress 错误，未顺手修复其他模块。
- 没有测试每一种设计器控件和任意自定义脚本组合；批量编辑仍遵守现有支持类型及授权契约，不支持的控件不会静默转成普通输入框。
- 修改 DW Batch Form 后，已有实例仍使用各自冻结的设计；需要以新建实例验证新发布的设计。
