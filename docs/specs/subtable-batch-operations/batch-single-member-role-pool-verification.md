# 验证记录：实际单人角色候选池

## 范围与结论

本记录仅是已部署批量分派链的合成运行验收，不改产品代码或目录。验证已有合法 BU/Role 的普通 Save→Complete→真实 Flowable 候选身份，仍不能标 P4 完成。所有生成任务保持未领取，不执行任务补偿、取消或改派。

## 脚本与修正

`frontend/scripts/verify-batch-role-pool-runtime.mjs` 通过正常 Portal/Admin 登录读取既有目录，选择有准入且成员去重后恰好一人的 BU/Role。代码按配置字段设置两条新合成行；Save 不增加任务，Complete 后核对引擎实际任务的 `candidateUserIds`、`CANDIDATE_USERS`、空办理人及 role/BU code。目录用户 ID 不打印、候选人姓名不写入证据。

首轮发现脚本将 `{eligible: boolean}` 响应对象直接用作布尔条件。已改为显式验证类型并检查 `eligible === true`，另补两个子任务各自稳定行身份且不重复的断言。首轮实际 Save 的服务端资格检查及候选池仍已成功，但不拿脚本的错误 admission 断言冒充正确证据，以下独立新实例重跑为准。

独立审查 Minor：候选集合 deepEqual 失败时会把真实目录用户 ID 打到 stderr，与不打印身份的要求不符。已改为排序后精确序列比较的布尔断言和固定消息，保留完整集合相等要求，不以数量相等替代。

## 真实结果

- 部署 Portal 镜像 `sha256:16d6a97b77e46fc543d947e9a607d99141d51cf7fd6ab199977492ee5bf3346e`，引擎镜像 `sha256:6b38be811725133793be66efba53033f0313ab2eb1076ac4685f7323f105418a`，不更换旧产物进行冒充验证。
- 修正后新合成实例 `08e08aa9-c93c-11f1-8f36-4ee612046d7e`，Review `08ebfca7-c93c-11f1-8f36-4ee612046d7e`。普通 Save 成功且引擎任务 ID 集合不变；Complete 生成 `36e8e838-c93c-11f1-8f36-4ee612046d7e` / `36e76193-c93c-11f1-8f36-4ee612046d7e`。
- 两项真实任务均为 AssignedWork，未领取/无个人办理人，候选身份各恰好一位且与目录成员集合相等；实际 role/BU code 一致，两份行身份各唯一、不重复。脚本 `synthetic-single-member-role-pool` 退出0，directoryWrites=0。
- 首轮合成实例 `c6a918a7-c93b-11f1-8f36-4ee612046d7e` 及其任务保留，不取消或重建它们来掩盖脚本修正。该批没有真实用户停用、目录变更或业务 FU 修改。
- 增加桌面/窄屏重开检查后，新实例 `518c366b-c93c-11f1-8f36-4ee612046d7e` 的两个角色候选子任务 `7c768a1a-c93c-11f1-8f36-4ee612046d7e` / `7c748e45-c93c-11f1-8f36-4ee612046d7e` 核对通过。
- 增加真正 UI 统一赋值路径：新实例 `af40dbdd-c93c-11f1-8f36-4ee612046d7e`，Review `af4a520b-c93c-11f1-8f36-4ee612046d7e`，在工作区选中两行→统一“Assign by role”→选择已有 BU/Role→Apply。捕获真实 Save 的 ASSIGN 意图、rowPatches=2，随后正常 Complete 生成 `d96a9eac-c93c-11f1-8f36-4ee612046d7e` / `d968c9e7-c93c-11f1-8f36-4ee612046d7e`；真实候选池、BU/Role 及两份行身份均通过。
- 角色保存后重开，两个 cascader 证明恢复角色模式；脱敏截图 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_role-pool-workspace.png` 及 `2026-10-16_role-pool-workspace-narrow.png` 保留。截图不是错误态冒充成功；这次是在成功保存后重开，未再次 Apply。
- 最终加入重开/关闭全 fieldValues 与任务 ID 不变、pageerror=0 的断言后，再用新实例 `2b8a7c0f-c93d-11f1-8f36-4ee612046d7e`、Review `2b94405d-c93d-11f1-8f36-4ee612046d7e` 重跑 UI 统一角色链通过；实际子任务 `54ba286e-c93d-11f1-8f36-4ee612046d7e` / `54b853a9-c93d-11f1-8f36-4ee612046d7e`，pageErrors=0、candidateCount=1、unclaimed=true。最终布尔集合比较修改只隐藏断言失败时的身份值，不改变通过判据。
- 另建独立 role-only FU50037 `batch_verify_roleonly1016`，PROCESS/TASK 50724/50725、binding51016/51018，设计 validate 无 errors/warnings，Deploy 成功。fixture 新增严格枚举 `FIXTURE_ASSIGNMENT_MODE=both|role|user`，缺省仍 both，不改既有 FU。新实例 `39999d93-c93e-11f1-8f36-4ee612046d7e`、Review `39a2c5a1-c93e-11f1-8f36-4ee612046d7e`：服务端投影 allowUser=false，UI 下拉不存在个人模式，统一选 BU/Role Apply 两行后，实际子任务 `95c002e2-c93e-11f1-8f36-4ee612046d7e` / `95bca77d-c93e-11f1-8f36-4ee612046d7e` 均通过候选池/身份核对，pageErrors=0。
- role-only 桌面及窄屏独立截图 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_role-only-pool-workspace.png`、`2026-10-16_role-only-pool-workspace-narrow.png` 保留并已目视窄屏。截图 tag 只接受字母/数字/连字符，不覆盖前一模式证据。

## 未验证项

API 路径、UI 两行统一角色赋值和 role-only 模式已有独立真实实例证据；逐行不同角色、多成员池及个人/角色切换尚不能由本记录推出。仅验证单人角色池的当前状态，不证明多成员混入停用用户时的最终过滤、预校验与创建之间的竞态、无 buField 的引擎默认来源或创建失败可见性。完整功能阶段仍为 CONDITIONAL。
