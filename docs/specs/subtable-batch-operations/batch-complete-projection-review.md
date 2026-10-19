# Code Review：Complete 复用冻结提交 binding

## 结论 / 范围

CONDITIONAL。本 concern 仅关闭 Save 构建后 Complete 再用实时 binding 过滤冻结草稿的问题；不宣称 P2/P3/P4 或全平台版本隔离完成。命中 MI 热路径、配置驱动行身份、UI 提交流程及共享提交契约；没有新增服务端权限、包字段、环境配置或数据库变更。

## Finding 与处置

- 已修：`useTaskActions.submitApprove` 在 `buildCurrentTaskFormSubmitPayload` 后，再以实时 `subTableBindings` canonicalize/投影。当冻结可写控件的实时表名/PK/模式已漂移时，批量草稿会被丢弃或重新分配错误身份。新增测试先失败：实际发出的 `formData.__subTables__` 为 undefined，期望冻结 store 的完整行。
- `useTaskForm.resolveSubTableSubmitBindings` 为 Save 和 Complete 共用已有冻结解析逻辑。detail.vue 显式接线，Complete 在原同步阶段取得一次解析结果，同时供 canonicalize 和可写投影使用；期间没有 await 或清除待提交批次。无声明时仍为原 binding，未接 callback 的原调用方也保留既有行为。
- 测试 mock 原零参数函数造成新增用例 tuple/never 类型错误；改成真实 TaskCompleteRequest 签名，修的是本测试装配，不降低类型严格度。全量类型错误从此前407降至383，仍为 FAIL，不宣称类型门禁通过。

## 验证

- `pnpm -C frontend/user-portal exec vitest run subTableBatch useTaskForm useTaskActionsSubTableCanonicalization writableSubTableProjection`：11文件63项通过。第二 reviewer 独立执行 actions/membership/scope 15项通过。
- `pnpm -C frontend/user-portal run build`：PASS，随后 `docker compose -f docker-compose.dev.yml --env-file .env up -d --no-deps --build user-portal-frontend` 部署新产物。
- `vue-tsc --noEmit --pretty false`：FAIL383；本批 useTaskForm/useTaskActions/detail.vue/测试无诊断。没有 suppress/忽略或修改编译配置。
- `MI_FIXTURE_UNIT_ID=50029 MI_FIXTURE_BATCH_COMPLETE=1 node frontend/scripts/start-mi-verification-fixture.mjs`：真实合成进程 `13bbd2b2-c86e-11f1-a63d-c6848797bef7` 完成 inline Save666 → Batch Edit777/Apply → GET仍666 → 真实 Approve/Confirm → 新阶段任务 `1fcaad39-c86e-11f1-a63d-c6848797bef7` 读回777；pageerror=0。没有API seed，Apply未提前保存/创建任务。
- 桌面/窄屏保存截图沿用绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-mi-save-desktop.png` 与 `2026-10-15_batch-mi-save-narrow.png`；真实普通页面保存态截图，不冒充配置漂移截图或全面 Preview parity。
- 完整 MI 234单测 + 6浏览器场景通过，输出 `OK — unit + screenshots`。执行最后一次63项前端测试也全绿；紧随其后的 compose ps 曾误在仓库根目录执行而报缺少.env，改到设计指定的部署目录核验，不把该错误当产品故障。
- diff、脚本语法和新增内容敏感检查由第二 reviewer 独立执行；无新增凭证/真实业务值。

## FU / 性能 / 残余门禁

绑定语义仍由服务器裁剪快照 rowDesign 提供，非名字猜测；旧实例路径未改变。Export/Import/Clone/Rollback/Admin 包链本 concern N/A（未修改包/设计态格式）；完整生命周期仍待验收。一次批次解析供两个真实消费者使用，无逐行HTTP；未压测，不称性能优化。运行样本一个用户两执行域，不替代两用户隔离验证。运行时未做 catalog 配置漂移，漂移红→绿为单元边界证据；后端 pinned-config 解析等其他漂移门禁保留。Assign 同工作区、Preview、New Request、启动失败/hydration 与完整 P4 仍未闭合。
