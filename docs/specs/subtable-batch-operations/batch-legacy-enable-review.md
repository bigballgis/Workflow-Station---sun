# Code Review 结果：旧子表首次启用批量操作

## 结论

CONDITIONAL，可提交本窄修复；不是 P4 总体验收通过。

产品变更仅限 DW 属性事件初始化缺失标识，不更改服务端权限、实例快照或保存校验。
独立 reviewer 完整复核 staged 四个源文件/脚本，无新增 Blocker 或未接受 Major。
全量 DW typecheck 仍有 128 条历史诊断；与 `batch-unmount-typecheck.log` 的原始诊断逐条比较差异为零。

## Findings 与逐条处置

- Blocker：无。
- Major：产品补丁无新增问题。首轮验证脚本用固定等待同步 Save，已替换为精确 form PUT 响应或错误 UI，并等待 Save loading 结束；重审确认闭合。
- Minor/Question：无未闭合项。补充验证 root/props 双槽均缺 ID；截图裁设计区域，避免登录身份进入证据。

## 范围 / 验收映射

- 报告的 FU50057 仅正常认证 GET：旧子表没有 `batchControlId`，批量配置尚未保存；没有改写用户设计。
- 根因：新拖入控件的 `rule()` 有生成逻辑，但旧控件首次打开四个批量开关没有对应初始化。
- `main.ts` 四个开关 watch 共用 `initializeBatchControlIdOnEnable`。
- 仅值为布尔 `true` 且 root/props 均完全缺失 ID 时生成安全随机标识，同时放入画布与属性槽；重复开关、保存不重新生成。
- 已有空、null 或重复标识不自动修复；保存/发布的唯一性校验仍保持。
- 未启用批量的旧控件普通 Save 不生成标识，不给旧实例增加能力。
- 前序 role outcome 工作区改动未纳入本提交。

## 触发矩阵

bugfix-verification、implementation-autonomy、code-review、verify-ui-fix-with-screenshot；稳定控件身份契约。
无新增接口、SQL、数据库结构、依赖、环境变量或三语文案。

## 编译与静态检查

- `pnpm -C frontend/developer-workstation run build`：通过，36.31s。
- `pnpm -C frontend/developer-workstation run typecheck`：128 条既有诊断，与最近 unmount 基线完全相同；不是全量类型检查通过。
- `git diff --cached --check`、脚本 `node --check`：通过。
- 独立 reviewer 复跑 `batchSubTableConfig.test.ts`：7/7 通过。

## 测试与运行证据

本次七个测试文件共 70 项通过：

```powershell
pnpm -C frontend/developer-workstation exec vitest run src/utils/__tests__/batchSubTableConfig.test.ts src/utils/__tests__/batchSubTablePreview.test.ts src/utils/__tests__/batchSubTableMembership.test.ts src/composables/formDesigner/__tests__/useFormSave.batchMembership.test.ts src/composables/formDesigner/__tests__/useFormPreviewBuild.batch.test.ts src/components/designer/__tests__/subTablePermission.property.test.ts src/utils/__tests__/formCreateDefaultEvents.test.ts
```

同一真实 UI 路径：旧控件 → Allow Batch Edit → Save → GET 读回。

- 红：合成 FU50051 / 新建 legacy 表单50777，准确复现用户报错，未写入批量开关或标识。
- 绿：重建 DW 前端后，同表单保存成功，读回新标识；重开/重复运行保存后标识不变。
- 第二样本：合成 FU50056 / legacy 表单50778，普通关闭状态 Save 不生成 ID；首次 UI opt-in 生成并保存，重复保存稳定。
- 两样本的业务字段名/FK 分别不同；原批量字段、类型、配置保持（比较允许既有序列化补空 `props: {}`）。
- 两样本 `pageerror=0`；实际进入 Sub Table → Batch Form 并截图。未部署或启用这两个新增测试表单到业务流程。

Docker：仅重建 `developer-workstation-frontend`，容器运行镜像：
`sha256:6be783ee1f59c6c3d68d6bb703d892e11c6ba9c34076c5af32c83326ee7f8024`。

截图绝对路径（保留）：

- `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_batch-legacy-enable-50051-before.png`
- `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_batch-legacy-enable-50051-after.png`
- `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_batch-legacy-enable-50056-after.png`
- `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_batch-legacy-enable-50056-after-designer.png`

## Function Unit 通用性与生命周期

- 判据不含 FU/binding/table/field 名字；ID 独立于 binding，不扩大同 binding 其他控件权限。
- Save/Load：两套隔离合成 FU 实测；普通关闭保存和再次保存均覆盖。
- Export/Import/Clone/Rollback：格式未变化；仍使用原 root `batchControlId` 契约。已检查既有重写器及其生成新控件 ID 测试，不新增迁移分支。本窄修复未重新运行完整生命周期集成矩阵。
- Admin 两条 Import/Deploy：现有共同纯契约校验不变，未知/重复 ID 仍拒绝；本次无消费者新增格式。
- Portal runtime/parity：未改 Portal；已运行实例不受 DW 此次 ID 初始化反向影响。

## 增量敏感信息 / 性能

独立 reviewer 对 staged 增量四类敏感模式扫描零命中；截图仅隔离合成数据。
初始化是单次属性事件 O(1)，不加定时器、网络请求或全表遍历。

## 残余风险 / 未验证项

- 用户任务 `26a50770-ca2b-11f1-b00d-96c2314bbe84` 正常认证 GET 返回403，没有绕过权限或修改任务；不能声称已验证该具体任务数据。
- FU50057 的实际设计未代用户保存；需刷新 DW 加载新前端，在控件上重新打开批量开关后保存。无需重建旧控件。
- Batch Form 支持独立字段/顺序/支持控件属性/校验，不是任意页面布局设计器；Portal 行编辑区、统一赋值区和操作按钮仍是固定工作区外壳。
- 完整生命周期、角色创建结果与 P4 其他验收仍以各自交付记录为准，不借本修复宣称完成。
