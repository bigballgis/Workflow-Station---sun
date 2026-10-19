# Code Review：批量表单生命周期的权限 binding 引用

## 结论 / 范围

CONDITIONAL。本批修正重建 binding 时遗留的字段权限 ID，并验证合成 FU 的 Clone、两类表单复制、DW Import/版本回滚、两条 Admin 导入。不是完整 P4，也不修改存量实例/业务数据或普通保存授权语义。

## Findings 与逐条处置

- Major（已修）：真实生命周期脚本增加完整 fieldPermissions 比对后 Clone 失败。原实现重写 configJson 但直接复制权限 Map，`oldBindingId:fieldName` 未改成新 binding ID。组件测试 RED：utility/Clone 合并6项中1失败0错误，明确 expected501/502、actual101/102。
- 新纯工具 `FormFieldPermissionBindingIdRewriter` 只匹配已声明映射的精确 `bindingId:` 前缀，一次映射，不用全局字符串替换或列名猜测；原值、普通字段键、未映射键及 null 语义不变，独立新 Map，不改源配置。目标键冲突显式拒绝，不覆盖/合并扩大权限。4项测试覆盖不同 binding、前缀边界、非递归映射、冲突及 legacy 空/无 scope 情况。
- Clone、ImportWriter（v2 Rollback 共用）、两类表单 Copy 均在完整 binding map 生成后少量接线，保存与 configJson 同一实体/事务。权限值不升级，不自动补 EDITABLE。
- 独立首审发现遗漏：`FormTableBindingRestorer` 在缺失 binding 时也实际重建 ID，必须同步改权限。新增原组件反例 RED7项中1失败0错误；补同一工具接线后通过。未顺手修改普通 Save、paste、字段改名或运行态权限算法。
- 脚本断言修正：同 FU 表单 Copy 复用业务表，因此不能套用 FU Clone 的 table ID 必须变化要求。已明确区分：Clone 新 form/binding/table ID；Copy 新 form/binding ID、原 table ID。首次失败保留，不改产品去迎合错误断言。

## 测试 / 构建 / 独立复审

- 隔离副本 Maven DW package：9类53个 suite 测试条目，失败/错误/跳过0；jqwik property 的生成次数不冒充独立 suite 测试数量。
- 独立 reviewer 重跑 utility/CloneBinding/Restorer/ConfigRewriter/VersionRollbackParity 共31项0失败/错误/跳过，8份关键源码/测试 SHA256 同源。初审无新增 Blocker/Major，BindingRestorer finding 已闭合；最终 staged 签核待补。
- DW jar SHA256 `876B38F92243FA8BC2F4D9A875516D032F814954EACC9033AF1A01A07694E4B3`，重建镜像 `sha256:20e28d030e71fa6d3eb3e8804c612f59f404e5ef801e34262203776b19ada1f5`，healthy 后真实验收。无共享 jar 或前端产品变更，不需要用旧后端镜像假测新源码。
- 未更改包 schema、Entity/SQL、路由、配置项、Admin parser/sync 或引擎；同一包中的权限引用已在 DW 写出前重写，Admin 保留包内绑定而不另分配 DW ID。不将本批扩大成旧实例权限迁移。

## 真实生命周期证据

- 源合成 FU50037 `Batch verification roleonly1016`。此前仅表单 JSON 检查在 Clone50038、50039 通过，后来新增权限断言发现缺陷；先前通过不作为权限完整证明。
- 修复镜像上最终新 Clone50042：`verify-batch-config-lifecycle-runtime.mjs` 全链 exit0。Clone 原单行 subForms 与独立 batchForms 完整保留、key/props binding ID 对应新记录、scoped fieldPermissions 引用新 binding，源 FU 全部 forms/bindings 不变。
- Clone ZIP 经 JSON `/function-units/import`、multipart `/function-units-import/import` 两条真实 Admin HTTP：catalog `136f390d-e5d2-471f-b304-f46e22164b6f`（同 code/version 原位覆盖）。逐表单核对完整 configJson、formType、sourceId 和 publishedFieldPermissions 与 DW 导出前一致，不只看 status 成功。
- 同包 DW reimport 重建，再在新 Clone 上将四开关全关并移除 batchForms→Publish→Rollback 到快照85，恢复批量定义、单行定义、binding 和权限；设计 validate=true。DW Rollback 后再次核对 Admin catalog 内容不变，未自动 Deploy、未改运行实例。
- Rollback 后真实 TASK Copy 和 PROCESS→TASK Copy 得新表单50737/50738，新 binding 权限映射正确、引用相同 FU 业务表、batchControlId 与来源不同。未把旧的 simulateCopy 属性测试冒充真实服务 Copy。
- 生命周期脚本仅写新建合成 Clone/导入设计及目录内容，无任务创建/完成、业务行写入、任务取消/改派。失败/中间合成对象保留，不删除业务数据掩盖失败。
- 新 DW 镜像完整 `pnpm -C frontend run regression:mi` 通过：29文件234单测及6浏览器场景，最终 `OK — unit + screenshots`，未用 unit-only 替代。该回归只操作既有脱敏合成 MI fixture。
- 最终 staged10文件独立完整复审无新增 Blocker/未接受 Major；264条新增非空行四类增量 secret0命中、脚本语法及 cached diff检查通过。本窄批可提交 CONDITIONAL，不是整体 P4 PASS。

## 未完成门禁

本例无 lookup/复杂联动、嵌套 scope、邮件/Action 等其他引用；不证明所有 FU 设计产物完整往返。真实跨库 Import、未知格式拒绝、完整 DW 编辑器 Preview parity、启动失败隔离与引擎分派异常可见性仍需验收。旧383项前端类型错误仍开放，不报告整体 PASS。

## 后续：Clone → Deploy → 新实例 → 实际角色任务

- `deploy-start-cloned-batch-fixture.mjs` 正常 DW/Admin/Portal 登录，只接受名称及 tag 均明确为合成的源 FU；从源50037新建 Clone50043，不改源设计。流程键由新 Clone.code 确定，而非修改已有流程键；真实 DW Deploy 状态轮询成功后才启动。
- 新 catalog `adbb2c28-5812-45d6-b389-70dae553b3e8`，仅给新合成 catalog 的测试工作台角色授予 FU Access；不改用户目录或已有 catalog 权限。新实例 `d4026529-c971-11f1-8f36-4ee612046d7e`，Review `d415c567-c971-11f1-8f36-4ee612046d7e`。
- 角色工作区脚本接受 Clone 场景的显式期望 process key，并同时匹配实际 task 的 catalog ID；无此参数仍只接受原 `batch_verify_*`。新键不靠放宽正则后盲目写任务，synthetic name、Review、全新两行及能力检查保留。
- 真正 UI 选两行→统一 BU/Role→Apply，捕获 ASSIGN 两补丁，Save 的引擎任务集合不变。Complete 实际生成 `db921fb8-c971-11f1-8f36-4ee612046d7e` / `db8d16a3-c971-11f1-8f36-4ee612046d7e`，均未领取、候选集合各1人、role/BU 与目录和配置一致、两份冻结身份不同，pageErrors=0。此项证明迁移后的 scoped 权限确实支持 Portal 批量操作，不只是包内字符串相等。
- 独立脱敏截图 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_cloned-role-pool-workspace.png` 及 `2026-10-16_cloned-role-pool-workspace-narrow.png` 保留。没有领取、改派、取消或补偿任务。
- 审查补强两个 fail-closed 边界：Deploy 前 clone.code 必须与 source.code 不同，授予新 catalog Access/Start 前 catalog.code 必须等于 clone.code；角色脚本 Clone 模式要求期望 catalog ID 是非空 UUID，不能让两个缺失值相等冒充关联验证。
- 补强后独立新 Clone50044 重跑全链通过：catalog `4a5d50fe-ec4a-4a7d-a2c0-32803e820c8a`，新实例 `2baf9d7d-c972-11f1-8f36-4ee612046d7e`、Review `2bbc20eb-c972-11f1-8f36-4ee612046d7e`，实际角色子任务 `342c9e3c-c972-11f1-8f36-4ee612046d7e` / `342942d7-c972-11f1-8f36-4ee612046d7e`，所有候选身份/BU/Role/两行身份及无提前创建均通过、pageErrors=0。未修改产品或更换镜像。
