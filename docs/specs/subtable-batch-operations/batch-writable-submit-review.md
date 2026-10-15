# Code Review：增强任务只提交可写子表

## 结论

CONDITIONAL。本批修复增强实例页面 Save 的展示副本回传问题，不代表 P3/P4 完成。完整 MI 234 单测及 6 浏览器场景通过；全量类型检查仍是既有 407 错误、涉及本批文件的新错误为零。

## Findings 与逐条处置

- 已修：页面将只读/非当前可写 binding 的展示副本作为业务写入回传，增强服务器按完整行差异拒绝。新增响应布尔 `subTableBatchRuntimeEnabled` 由本地服务端快照存在性产生；仅严格 true 使用可写 canonical store 投影。缺省/false 保持旧 Save 行为。权限验证没有放宽，客户端布尔不授予权限。
- 已修：投影后保存成功若直接替换页面 tables，会丢失只读展示数据。增强路径仅合并已提交 store，保留未提交的页面展示副本；测试实际调用 Save 验证。
- 已修（第二 reviewer Major）：原前端冻结 binding 只覆盖 Add/Delete，实时 binding 变只读会过滤已有 Edit/Assign 草稿却保留其声明。所有声明操作统一用冻结元数据；两条漂移测试先真实失败，再修到通过。缺冻结配置/歧义仍显式报错，无实时设计兜底。
- 验证脚本修正：参与者表在本合成 TASK 中实际 EDITABLE，不能把它一概断言为只读而必须省略。按是否实际提交核对其 FK，并始终读回核对，不弱化业务状态判据。

## 范围 / 验收映射与触发矩阵

A01/A02/A06/A16/A19/A21/A26：新旧实例兼容、冻结传输、明确删空、保存时机与行隔离。命中前端 MI 热路径、接口只增字段、安全响应、截图及 config-over-heuristics。未改数据库、包格式、工作流动作或发布配置。共享纯函数同时服务 Save/Complete，保留现有 Complete 的可写 store 规则及明确空数组。

## 编译与测试

- RED：新增 `useTaskFormBatchMembership` 用例在父表未被过滤处失败；修后包含 Save 页面保留、旧路径及增强删除到空的测试通过。
- RED：scope 的 EDIT/ASSIGN live READONLY → frozen EDITABLE 两条用例均失败；修后通过。
- `pnpm -C frontend/user-portal exec vitest run subTableBatch useTaskForm useTaskActionsSubTableCanonicalization writableSubTableProjection`：11 文件 62 项通过。
- `mvn -pl backend/user-portal -am test "-Dtest=SubTableBatch*Test,TaskFormComponentMiSubmitIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：107 项通过；新增响应隐私测试再执行 2 项通过。
- `mvn -pl backend/user-portal -am package -DskipTests`：BUILD SUCCESS。Portal Vite build 通过，前后端新产物 compose 重建。首次 compose 包含依赖重建，无删除卷或业务数据；最终前端用 --no-deps 重建。
- `vue-tsc --noEmit --pretty false`：FAIL，407 既有错误；本批 useTaskForm/useTaskActions/新 helper/测试/API 文件无诊断。没有忽略错误或降低严格度，不报告全量类型 PASS。
- 完整 `pnpm -C frontend run regression:mi`：234 单测 + 6 浏览器场景通过，旧实例使用原无快照路径。

## 真实运行与截图

- 合成独立 FU50029，默认 UI 模式（没有 API_SEED）：进程 `7f12d687-c86c-11f1-a63d-c6848797bef7`，真实 inline Save → GET 读回 age666/PK/FK → 真实 Approve/Confirm → 下一阶段任务 `8a47cbce-c86c-11f1-a63d-c6848797bef7` 出现且数据仍在。pageerror=0；非只看返回码或 toast。
- 另一个合成非 MI Edit-only FU50028，新任务 `d368a630-c86c-11f1-a63d-c6848797bef7`：逐行 Apply、持久化、版本递增、未选行保留、无批量标记的只读伪造请求拒绝，PASS，pageerror=0。
- 已目视截图：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-mi-save-desktop.png` 与同目录 `2026-10-15_batch-mi-save-narrow.png`。非 MI 截图为同目录 `2026-10-15_batch-editonly-desktop.png` / `2026-10-15_batch-editonly-narrow.png`。姓名区域遮罩，业务值均为合成值，截图不删除。窄屏局部截图不等于完整响应式/Preview parity 通过。

## FU 通用性 / 生命周期 / 性能 / 安全

可写范围来自 bindingMode 与 canonical store 配置，声明批次用已冻结 rowDesign；无 FU/业务列名硬编码。新增响应不进入 FU 包，Save/Load 及两种运行实例已验证；Export/Import/Clone/Rollback/Admin 包消费者本批 N/A（未改设计产物），整体验收仍保留。按 binding 线性投影，无逐行新 HTTP。没有性能提升结论。服务器强鉴权及 readonly 拒绝不变；服务端快照不发浏览器，响应隐私测试覆盖。diff/脚本语法与增量敏感信息检查执行后纳入最终 staged 复审。

## 未验证 / 残余风险

两个执行域仍使用同一合成操作者，不等于两用户授权验收。完整生命周期、真实 Batch Assign 同工作区事务、冻结 BPMN/Complete 资格链、New Request 摘要、Preview、启动跨边界/hydration、上限压测及完整 P4 未完成。普通 Complete 的实时 binding 再投影仍需专项核对配置漂移；本批已闭合 Save 的冻结上下文，不宣称全平台版本隔离。
