# Code Review：Portal 工作区接线与 MI 数据保护

## 结论

CONDITIONAL。当前增量的运行反例已修正，允许保存为代码批次；完整前端类型基线、跨用户和全生命周期门禁仍不通过/未验证，不允许生产放行，不代表 P2/P3/P4 完成。

## Findings 与逐条处置

### Blocker

- `detail.vue` 的普通当前任务 renderer 未接批量能力/事件，三个页面 extractor 又丢失显式 `batchControlId`。
  - 反例：已冻结控制器无法匹配，普通任务页面不出现工作区；三个 extractor 的新增稳定 ID 用例均先失败。
  - 处置：只传服务端投影与原 pending 事件；提取器无损保留显式 ID，同 binding 的多个控件不合并，旧控件不补 ID。
- `SubTableBatchEditDialog` 模板反复 slice 产生新数组，使选择丢失；成功 Apply 后关闭回调仍提示丢弃。
  - 处置：分页投影改为稳定 computed，已关闭不再次确认；两条新增测试先失败后通过，真实 Delete-all 和 Apply 不再丢选择/弹错误确认。
- MI 嵌套子行写入只带显示名，写出错误 canonical key；Details 取行又按相近字段/标签选中别的 binding。
  - 处置：写回传完整已配置 binding；已明确绑定 ID 不允许用同名其他表替代；已有 canonical（包括 []）优先，不按重叠字段恢复已删行。
- Details 把子行自己的 PK 当成父参与者 PK，或对两个结构 FK 用任一匹配合并范围。
  - 处置：按配置关系与明确过滤 FK 判断；无声明的多 FK 歧义返回空授权范围，不能并集。更名 PK、普通同名字段和多 FK 反例均先失败后通过。
- `repairMisassignedLinkChildStructuralFk` 把 collection 的主表 FK 修成自己的参与者 ID。
  - 反例：真实 Age 编辑 Save 的 POST 已将 main_id 改成参与者自己的 ID；新增集合行测试精确复现。
  - 处置：显式 `miParticipantRow` 不运行 child FK repair。标量编辑也不能覆盖配置 PK/FK；新的标量测试保留另一参与者行。

### Major

- 旧截图脚本把缺控件当 PASS，首个截图也可能拍到无关表单。
  - 处置：增加固定 synthetic 实例模式，预检查 Task/Process 属于同一克隆；缺真实控件、缺空/已填写 Details、错误行数均失败。截图定位实际 People/参与者表；遮罩目录信息，不屏蔽业务断言或 pageerror。
- 全量 vue-tsc 仍失败。抽查 `linkFormMiIsolation.test.ts` 的三处旧 `.id` 类型错误均在 HEAD 原文件存在，新增 helper 和页面本次类型错误未出现；未降低 strict 或忽略检查。
  - 处置：保留完整类型门禁为未闭合，不用 Vite build 或过滤输出宣称 typecheck PASS。完整阶段放行仍被阻止。

### Minor / Question

无新业务分叉。原单行保存时机和任务完成链不改；测试夹具是明确重设计的隔离克隆，不充当 Clone 原样保真证据。

## 范围 / 验收映射

A01/A03/A10/A14/A16/A17/A20-A22/A26/A36：控件身份、有效入口、取消/选择、canonical 保留和 MI 切片。不是 Assignment 功能完整验收。

## 触发矩阵

implementation-autonomy、code-review、bugfix-verification、config-over-heuristics、verify-ui-fix-with-screenshot；Portal MI、表单 label、性能及隐私规则。

## 编译与静态检查

Portal Vite build 成功，已有 Sass/大包 warning 保留。全量 vue-tsc FAIL，属于当前不能发布 PASS 的原因；不宣称全仓类型通过。完整增量 diff/check 与 secret 扫描记录随提交保存。

## 测试与运行证据

- 工作区/三页面 extractor/自动保存同步：48 个测试通过。
- 完整 `pnpm -C frontend run regression:mi`：28 文件、226 单测和 6 个真实 Playwright 场景通过，未使用 unit-only 替代。最新 Portal 重建后再次执行。
- 隔离已有 FU 克隆实际 Age 编辑→Save→Complete→后续节点：POST 和重读均检查两条参与者父 FK 不变、自己子行 PK/FK/666 正确；数据库两行 FK 均为同一原主表 UUID。
- 两个新建批量 FU（人工/UUID PK、不同字段）：新增、删空、自动保存和重开通过。完整跨用户 UAT、所有调用形态和 DW Preview parity 未验证。

截图目录：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots`。实际核看 `2026-10-14_task-6e558986-subform2-people.png` 的 People UUID、sub task id、Age 666，以及同实例 Details 空/填写和参与者切片；目录信息遮罩。批量桌面/窄屏截图为 `2026-10-14_batch-manual-*` / `batch-uuid-*`。截图不删除。

## Function Unit 通用性与生命周期

产品判断来自稳定控件 ID、binding metadata 和结构 FK，不硬编码样例 FU/字段。新建两份及已有克隆样本均实际运行；跨用户、Export/Import/Rollback/Admin 双路径、DW Preview 完整矩阵尚未验证。本增量没有新增设计字段、导入格式或数据库结构，因此不以夹具重设计代替生命周期保真。

## 增量敏感信息

增量源码、测试、脚本与文档扫描，无新增真实凭证/邮箱/私钥。夹具通过正常登录与权限 API，只写明确标记的 synthetic FU/过程，不修改源 FU；固定 task/process 预检查防止误选真实任务。截图必须仅引用已脱敏并检查的文件。

## 性能

稳定分页投影避免选择事件替换列表引用；无额外每行服务调用。未做容量压测，不宣称性能提升。

## 残余风险 / 范围外问题

既有猜字段兼容路径未全部治理；本批只收紧已复现且有权威配置的入口。P1/P2 的冻结授权/摘要、New Request、共享 DW Preview、统一 Assignment 草稿及服务端目标资格/阶段仍需继续实施；不能以本批截图和 MI 门禁宣称整个需求完成。完整类型基线也仍需处理/确认。
