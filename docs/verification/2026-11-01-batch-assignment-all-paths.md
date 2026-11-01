# 2026-11-01：Batch Assign 入口全路径验收

后续用户确认原始MCY也在范围内，已另完成FU48配置发布1.0.133及6份Batch完整流程。三份FU合计18份全部结束，见 [原始MCY补充验收](2026-11-01-fu48-batch-all-paths.md)。下文保留本报告当轮两份副本的12份结果。

## 结论

本轮按用户要求，在 FU 50125、50126 各新建6份请求，全部先通过真实浏览器执行Batch人员分派，再完成后续流程。12份全部通过。没有将上一轮单行Edit分派的结果计入本轮，也没有复用已结束的请求。

最终独立读库复核：12份均已结束、零活动任务；共68次页面任务完成、24行已保存的人员Lookup身份及对应email精确匹配。

受测平台代码：`f864ef183c51635e7c342af9637a6ba088919587`。本轮没有修改产品代码、设计配置或发布版本。两份FU的1.0.2已经开启Allow Batch Edit和Allow Batch Assign，Assignment Batch Form使用人员Lookup和MI Assignment；DW读取配置与实际新请求冻结版本、运行结果均已核对。

范围为此前确认的两份MCY测试副本：50125普通分层双绑定，50126显式额外Case FK。原始MCY FU48未修改、未列入本轮通过样本。

## 每份请求的完整路径

1. New Request：两笔Transaction，各自一条Correspondence，另加独立Case Correspondence。
2. Save Draft并重开；编辑A子行，删除/替换B子行，编辑Case子行，再保存重开。
3. Case Submit → Transaction Assignment → Claim → 勾选两笔 → Batch Edit → Assign by person → 人员Lookup → Apply to page。
4. 两行分别打开Edit核对回填，email后缀分别对应当前card_number；再Confirmed all Assignment，服务端分派结果必须为VERIFIED。
5. 按指定组合办理两笔交易直到引擎结束；交替采用先办完一笔和交叉推进顺序。
6. My Request重新打开、核对流程图和Flow History，再点击Case Submission核对Case切片。

批准路径：Request Approval → Approve → Mark Completed。拒绝路径：Request Approval → Reject → 拒绝出口。直接路径：Investigation Mark Complete → Mark Completed。

## 12份实例

### FU 50125（版本1.0.2）

- 批准／直接完成：`3ac7576a-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片1行。
- 拒绝／批准：`5222cda7-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片1行。
- 直接完成／拒绝：`6b3e1c56-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片1行。
- 批准／批准：`80e9e751-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片1行。
- 拒绝／拒绝：`9ab8f526-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片1行。
- 直接完成／直接完成：`b1608443-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片1行。

### FU 50126（版本1.0.2）

- 批准／直接完成：`c8f5f6cc-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片3行。
- 拒绝／批准：`e0be5fa9-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片3行。
- 直接完成／拒绝：`f7437318-d5b7-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片3行。
- 批准／批准：`0cc7def3-d5b8-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片3行。
- 拒绝／拒绝：`24f54ba8-d5b8-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片3行。
- 直接完成／直接完成：`3a55dbd5-d5b8-11f1-8c70-6e7849f291cc`。Batch分派VERIFIED，最终0活动任务，Case切片3行。

## 实际核对的判据

- 所有办理步骤均由页面点击/填写/确认发出，读取响应body，不只看HTTP200。Batch初次分派明确VERIFIED，没有用单行分派绕过。
- 每个任务的Flow History按taskId核对实际APPROVE/REJECT操作，用户备注保留，不显示内部操作标记。
- 每次完成后Correspondence的值、顺序、版本与提交后基线一致；最终数据库中3个独立主键、2个交易父主键，交易子行各属一笔、不串行。
- 普通双绑定：两条交易记录只有Transaction FK，Case记录只有Case FK；Case页面仅1行。
- 显式额外FK：两条交易记录保留显式Case FK，Case记录没有Transaction FK；Case页面合法重叠3行，仍不跨交易串行。
- 人员字段保存为Lookup对象；两行回填各自保留当前交易号后缀。
- 最终Flowable有结束时间、活动任务数0；各组人工节点、MI容器、结束事件的SVG实际颜色与执行历史一致。全拒绝时Mark Completed与交易完成出口保持灰色；已结束MI不残留橙色。
- 12份完整流程pageerror均为0，结束后Case节点的真实表格行数与绑定设计一致。

## 截图

截图在 `frontend/user-portal/verification-screenshots/`，每份保留办理节点、Batch选择、结束图、历史及Case切片。以下是便于复查的代表：

- 两份FU实际Batch人员选择：`2026-11-01_fullflow-3ac7576a-batch-selected.png`、`2026-11-01_fullflow-c8f5f6cc-batch-selected.png`。

- FU 50125 批准／直接完成：`2026-11-01_fullflow-3ac7576a-completed.png`、`2026-11-01_batch-matrix-plain-0-case.png`。
- FU 50125 拒绝／拒绝：`2026-11-01_fullflow-9ab8f526-completed.png`、`2026-11-01_batch-matrix-plain-4-case.png`。
- FU 50125 直接完成／直接完成：`2026-11-01_fullflow-b1608443-completed.png`、`2026-11-01_batch-matrix-plain-5-case.png`。
- FU 50126 批准／直接完成：`2026-11-01_fullflow-c8f5f6cc-completed.png`、`2026-11-01_batch-matrix-extra-0-case.png`。
- FU 50126 拒绝／拒绝：`2026-11-01_fullflow-24f54ba8-completed.png`、`2026-11-01_batch-matrix-extra-4-case.png`。
- FU 50126 直接完成／直接完成：`2026-11-01_fullflow-3a55dbd5-completed.png`、`2026-11-01_batch-matrix-extra-5-case.png`。

## 复查方式与边界

本地Playwright驱动为 `frontend/scripts/_1101-batch-matrix.mjs`，运行 `cd frontend` 后 `node scripts/_1101-batch-matrix.mjs`；该验证脚本为忽略目录中的本地文件，没有作为产品代码提交。完整结果保留于 `.codex-1101-batch-matrix-results.json`，日志 `.codex-1101-batch-matrix.log`。每份详细结果 `.codex-1101-fullflow-实例ID.json`。

本轮只有浏览器/读库验收和文档补充，无部署代码变更，因此未重复构建或重跑全仓测试。最近代码修复的build/Compose、2613通过与1既有失败、类型381无新增、定向lint37基线无新增、MI250单测+6浏览器场景证据见2026-11-01-case-node-primary-identity.md，不冒称本轮全仓全绿。

样本Mark Complete的actionType配置仍为REJECT，所以整单终态REJECTED是配置结果；这里“完成”指引擎已结束，不等于业务状态COMPLETED。人员模式已配置，Role、撤回、退回等未配置分支不在本次路径集合中。

全部由FULL权限developer跨节点办理；不代替独立双账号、并发竞争、远程环境、原Credit Card、所有FU生命周期验收。原用户请求没有推进；旧版本单仍按冻结配置运行，未升级或绕过校验。

运行时修改没有MCY/FU/业务表字段硬编码。本轮没有新增运行时修改；实例ID和业务名称仅用于定位测试样本。
