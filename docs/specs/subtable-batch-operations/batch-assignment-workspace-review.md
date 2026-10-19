# Code Review 结果：D7 同工作区草稿

## 结论

CONDITIONAL。该工程批次将初次分派编辑嵌入同一批量工作区，并完成草稿与真实 Save 验证；不等于 D7、P2 或 P4 完整验收。后端阶段历史、目标资格、Complete 再校验和真实任务/候选池仍需闭合。

## Findings / 逐条处置

### Blocker

- 本批复审未发现新增 Blocker。

### Major（已修）

- 原 `SubTableBatchEditDialog.openAssign` 先关闭并丢弃普通草稿，再打开另一份 Assignment 草稿。改为嵌入 `SubTableBatchAssignmentEditor`，统一 Apply 先收集两类意图、校验，再准备 PK、一次页面应用；移除独立 AssignDialog 及其接线。两条反例先失败：没有嵌入控件、Assignment 失败却 emit 普通修改；实现后通过。
- 首轮只读 review：已有 BU+Role 仅保留 code，查询 ID 空且角色禁用。按真实 code 唯一反查 ID，缺失/歧义报错，同 BU 的初始化角色请求合并；恢复不是业务修改。组件测试覆盖重开、查询一次、零 ASSIGN 声明。
- 首轮 review：多行与统一区共用用户搜索计时器/结果。改为按行身份和统一控件隔离搜索、300ms 防抖、过期响应丢弃；查询失败可见，不调用会吞错返回空数组的旧用户搜索方法。
- 首轮 review：BU 响应只比当前 ID，A→B→A 可能接受第一次 A。行与统一区独立请求代次，模式切换同时使旧请求失效；新增 ABA 组件测试。

### Minor（已修）

- 真实桌面截图和 DOM 断言证明新增区使页脚超屏；初次 scoped CSS 无效，传送后的 `.el-dialog` 没有调用方 scope 属性。改用专属 class 限定全局布局，固定头/页脚、body 纵向滚动；最终桌面/窄屏按钮 bounding box 位于视口内。
- 错误态截图发现旧 `subTable.requiredField` 不存在。新增 `batchAssignmentRequired` 三语提示，真实错误文案断言与消息截图通过。
- 未改目标不提交 ASSIGN；显式清空目标整批拒绝；role-only 初始模式正确；Assign-only 普通字段及统一赋值只读；无变化 Apply 不 emit 数据。

### Question

- 无需新增业务选择。本批未触及已有任务改派、任务取消或真实任务创建时机。

## 范围 / 验收映射

- D1/D7、A20/A21/A27/A29/A30/A33：普通字段与 Assignment 私有草稿、专用映射与意图、两侧校验失败保留草稿、取消、单次页面应用、原自动保存。
- A22/A25：工作区桌面/窄屏、20 行分页、200 个受影响身份上限；不宣称压测通过或 DW Preview 已等价。
- Assignment 只作用于既有授权行；新行/已删除行不进入该专用编辑区。服务端仍是权限和阶段的最终边界。

## 触发矩阵

- implementation-autonomy、bugfix-verification、code-review、config-over-heuristics、secure-coding-sast、frontend-design、portal-dialog-form-labels、verify-ui-fix-with-screenshot；更新既有 Help 时使用 extract-help-guideline 的三语/索引同步要求，没有新文章或新站点。

## 编译与静态检查

- `pnpm -C frontend/user-portal run build`、`pnpm -C frontend/help run build`：PASS，保留已有 Sass/chunk 警告。
- Portal 全量 `vue-tsc --noEmit --pretty false`：383 项既有错误，FAIL 基线未闭合；本次修改/新增产品与测试文件无增量诊断。没有用 Vite build 冒充完整类型检查。
- 三个 fixture/验证 mjs 语法、`git diff --check`：PASS。
- 本批无后端、共享 jar、Entity、SQL、环境变量或路由变更，不重建后端或更改平台数据卷。

## 测试与运行证据

- `pnpm -C frontend/user-portal exec vitest run batch Batch`：11 文件 65 项 PASS；其中 collector 8、目录 5、Assignment editor 2、workspace 17。目录覆盖 BU ID/code、坏响应/合法空集、防抖、搜索隔离与过期响应；组件覆盖恢复去重和 ABA。
- 旧单行 Assign/只读相关三文件共 26 项 PASS；存量未注册组件 warning 未伪装为无警告。
- 最终完整 `pnpm -C frontend run regression:mi`：234 单测 + 6 浏览器场景 PASS，`OK — unit + screenshots`，不是 unit-only。首次并发登录触发 API 限流失败，未改认证或限流；恢复窗口后重跑通过。
- Help 重建部署后 `node frontend/scripts/verify-help-portal.mjs`：149/149 PASS；现有文章、三语、llms 索引同步。
- 最终 Portal frontend 镜像 `sha256:df6a2c74129e0b7b0b1f7f3c0a603173f54a89981476d255716276eace1fb3fa`，Help `sha256:7b1c635bf92d91cc898c1078ffbd3fe3c3868bbbcb05a750f40bd6876a1ac0b7`，compose `--no-deps --build` 后运行，frontend fatal 日志计数 0。

真实样本只用新建合成 FU50035/50036（异于已有业务 FU），独立 BPMN Review 宿主之后才到 AssignedWork；本轮未推进到该目标：

- 混合：流程 `c6fca8f9-c897-11f1-a63d-c6848797bef7`、任务 `c707f3e7-c897-11f1-a63d-c6848797bef7`，真实 UI 普通 Caption + 单行个人配置，Assignment 缺目标/普通必填失败均保留另一份草稿且 GET 不变；一次 submit 携带 EDIT + ASSIGN，GET/reload 两值保留，任务数量/ID 不变。
- Assign-only：流程 `14817089-c897-11f1-a63d-c6848797bef7`、任务 `148bd017-c897-11f1-a63d-c6848797bef7`，普通字段无输入框，仅 ASSIGN 声明，未选行目标不变，GET/reload 保留，任务数量不变。
- 两个宿主均追加真实“统一给选中第二行设置个人”与取消测试：缺目标显式错误、Cancel 确认后 GET 不变；选第二行并统一设置，只有该行 ASSIGN 声明，第一行业务值不变（合法 scope claim 行版本按既有规则递增），pageErrors=0。
- 初次 fixture 部署因 collection 使用表达式而被现有校验拒绝，改为平台要求的变量名后成功；未绕过部署校验。统一测试首轮点击 EP 隐藏 checkbox input 超时，未保存；改点可见 checkbox label 并等待关闭动画后通过。

最终脱敏截图均保留：

- `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-assignment-mixed.png`
- 同目录 `2026-10-15_batch-assignment-mixed-narrow.png`、`2026-10-15_batch-assignment-only.png`、`2026-10-15_batch-assignment-only-narrow.png`、`2026-10-15_batch-assignment-error.png`、`2026-10-15_batch-assignment-error-message.png`。
- Help：`F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-15_help-portal-form-ctl-sub-table.png`。

## Function Unit 通用性与生命周期

- 运行时只读冻结 binding/字段映射与稳定行身份；产品代码没有测试 FU/表/列名白名单。
- Save/Load：上述两独立合成 FU PASS。包含实际 BPMN 的未来目标关联，但不是角色池任务生成证明。
- Export/Import、Clone、Rollback、Admin 两条 Import：本批未改配置协议或生命周期实现，增量 N/A；整体门禁仍未验收，不能借此填写 PASS。
- Portal runtime：上述草稿/个人 Save/取消/统一与布局 PASS；DW Preview parity 未完成。

## 增量敏感信息

- 独立 review 对修改和新增文本扫描四类凭证/私钥模式零命中；提交前再扫 staged 增量。目录实际人员显示在截图中遮盖，不把用户目标值写进验证日志。

## 性能

- 每个选择器防抖并隔离请求，初始化同 BU 去重；表格每页 20 行。不宣称 200 行压力或跨 BU 目录负载已通过。

## 残余风险 / 范围外问题

- 保存成功仍不表示真实任务分派成功；本批未执行未来 MI Complete、角色目标/候选池、目录失效竞态和完整资格校验，既有阶段历史门禁也仍待实现。
- New Request、DW Preview、启动本地失败且引擎取消失败的隔离、非标准 hydration 来源、全生命周期、多主体与压测仍开放。不宣称 P2/P3/P4 完成。
- 旧全量类型错误与旧用户搜索吞错路径不在本批顺手重构；新增批量路径已避开吞错方法。
