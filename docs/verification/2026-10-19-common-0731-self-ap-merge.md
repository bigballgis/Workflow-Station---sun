# common_0731_SELF_AP 合并验证（2026-10-19）

## 合并范围

- 当前分支：`liam_common_0731_task1118`；合并前：`57a5bf01e`。
- 目标本地分支：`common_0731_SELF_AP`，提交 `d69dd37eb`；未自动 fetch/push。
- 合并前工作区干净；已有 `zancun` stash 保持原样，未 pop/apply。
- 五个冲突文件均保留双方有效内容，不整文件选择 ours/theirs。

## 冲突处理

- 表单复制同时重新生成 Batch 控件 ID 和 Record Note 控件 ID。
- Designer Save 同时遵守 `savingForm`、`batchSaveBlocked`、`designerReadOnly`。
- Task Detail 保留 `recordSubTableBatchChange`，遵循目标分支仅显示 DW 配置操作的语义，不恢复隐式 Save。
- 两边问题记录均保留；目标分支 #1666–#1670 与本分支编号碰撞，重编为 #1679–#1683，并保留 upstream_id。
- 补齐原有 TaskCompletionObservationTest 构造器新增 BatchTaskComponent 参数。
- 只读导航测试纳入第七个 Batch Form 入口，并断言三个 Save 禁用条件均保留。

## 验证结果

- Portal、Developer Workstation、Admin Center 的 Vite 构建均通过。
- 后端四服务及依赖 Maven package 通过；选定的 Batch、Record Note、Workspace、Action、版本调用、克隆及人员摘要等共 682 项测试通过。
- 后端命令：`mvn -B -pl backend/user-portal,backend/developer-workstation,backend/admin-center,backend/workflow-engine-core -am package "-Dtest=*Batch*,*RecordNote*,*Workspace*,*CallActivity*,*CloneBinding*,*Action*,*UserDirectory*,TaskCompletionObservationTest,!*Database*,!*PostgresTransactionIT" "-Dsurefire.failIfNoSpecifiedTests=false"`。
- Admin Center 全量：17 文件、70 项测试通过。
- Portal 全量：311 文件通过、3 文件失败；2365 项通过、6 项失败。失败基线见下节。
- DW 首次全量：178 文件通过、4 文件失败；1144 项通过、7 项失败。修正合并后新增 Batch Form 导致的入口数断言后，只读交互 9 项测试通过；其他失败已在合并前复现。
- 重建全部四个后端和三个前端服务；四个后端健康。
- 重建完成后完整 `pnpm run regression:mi` 通过：234 项单测及全部 6 类 Playwright 界面场景。
- MI 仅使用原有隔离测试实例 `788a3ae1-cb90-11f1-967e-82c0efc60df9`，任务 `83319626-cb90-11f1-967e-82c0efc60df9`。
- 姓名回显只读验证：两份样本各打开/取消两次，保存数据和用户 ID 均不变，pageerror=0。
- `git diff --check` 通过。

截图保留在 `frontend/user-portal/verification-screenshots/`：

- `2026-10-19_app-788a3ae1-details-unprocessed.png`
- `2026-10-19_app-788a3ae1-details-completed.png`
- `2026-10-19_task-83319626-sex-before.png` / `sex-after.png`
- `2026-10-19_task-83319626-attachment-table.png`
- `2026-10-19_task-83319626-subtask-grid.png`
- `2026-10-19_task-83319626-subform2-people.png`
- `2026-10-19_task-83319626-people-inline-uuid.png`
- `2026-10-19_task-83319626-assignee-subtask-slice.png`
- `2026-10-19_batch-assignee-labels-after-{1,2}-{1,2}.png`

首次 MI 界面回归与后端重启重叠，登录失效，未计为通过；上面的完整通过结果来自所有服务重建健康后的独立重跑。

## 已有失败及未验证项

- 独立 worktree 检出合并前 `57a5bf01e`：Portal 的 inlineSubFormNestedSubTableContext、subTablePositionControl 共 5 项失败可复现（未初始化 Pinia）；DW 的 AiPanel、customModdleRoundTrip、i18n-no-chinese-comments 失败亦可复现。见 #1685。
- 独立 worktree 检出目标 `d69dd37eb`：recordNotePlanSubmissionContext 测试仍失败，FormData 实际重复 processInstanceId；见 #1684。本次未扩大到修复目标分支已有业务缺陷。
- 全量测试不是全绿，不能据专项通过宣称所有功能无影响。
- 第一次后端测试命中了依赖独立数据库的用例，因 BATCH_TEST_JDBC_URL / NOTE_TEST_JDBC_URL 未提供而失败；最终通过命令明确排除了这些数据库用例，不视为它们已通过。
- 本轮未执行用户实际申请的审批、对外邮件投递或完整新 Record Note 通知链路，未重新执行每一条批量 CRUD 业务路径。

## 本地数据库

仅在开发库执行仓库提供的 93、94、95 迁移，每个脚本使用事务与 ON_ERROR_STOP。执行前确认历史 Record Note 为 0、待转换旧邮件 Action 为 0。

- 93：邮件配置更新 0 行。
- 94：创建 Record Note 表/索引/列，历史数据更新 0 行。
- 95：历史通知更新 0 行；为 12 个用户继承附件通知默认偏好。
- 不清空数据卷，不重建数据库，不修改用户原 FU 或原申请。
