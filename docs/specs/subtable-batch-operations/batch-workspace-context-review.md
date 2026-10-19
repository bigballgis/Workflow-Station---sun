# Code Review：增强实例工作台上下文保护

## 结论与范围

CONDITIONAL。本批保护增强实例既有服务端根字段 `activeBusinessUnitId`、`activeRoleId`，不让后续表单输入改写初次分派的工作台上下文。不是完整 BU/Role 资格检查或 P4 完成；不改变无快照旧实例。

## Findings 与处置

- 服务端 ProcessStart 已剥离客户端这两个字段，再从认证工作台上下文写入；后续 header-only 保存却可能绕过子表早退。新增 `SubTableBatchWorkspaceContext` 对服务器 baseline 校验，允许省略或完全相同的回显，拒绝改写、清空或客户端补建 baseline 不存在的值。
- 接入 `SubTableBatchTaskPermissions` 和共享 `SubTableWriteIsolation` 的子表早退之前；不依赖 `subTableBatchChanges` 或 `__subTables__` 的存在，不增加目录查询、权限缓存或列名猜测。
- 新4项测试先 RED（3失败、0错误），实现后通过。独立审查重跑 WorkspaceContext/TaskPermissions/BatchTaskWriteIsolation/WriteIsolation 共23项，失败/错误/跳过0；4份关键源码 SHA256 与隔离构建副本一致。当前无新增 Blocker/Major。
- Process Return_To_Requester 仅有共享写边界代码与测试证据，不标作真实入口通过；引擎同步及其他变量写入口未由本批全部闭合。

## 构建与部署

- 隔离副本 Maven Portal package：选定 Batch/MiCollection/TaskProcess/Scope/Isolation/Complete/Client 共458项，失败/错误/跳过0。
- Portal jar SHA256 `46C3D2B5B3620F8130568E16A47E34126F504D7DBE8631F0C78AB185B1008588`；重建镜像 `sha256:200a1046d2596eb841b02e7f0095dbda67681f732cb39764fa447948f432632a`，服务 healthy。
- 不改前端产品、共享 jar、包格式、数据库结构、环境变量或部署路由；既有383项全量前端类型错误未关闭，不报告 typecheck PASS。

## 真实运行记录

- 合成 FU50036 新实例 `30dbc72f-c937-11f1-8f36-4ee612046d7e`，Review `30efc4ad-c937-11f1-8f36-4ee612046d7e`：两个根字段分别对 Save/Complete 提交伪造值，4请求均明确拒绝，完整 fieldValues 不变且仍为 Review；无子表和客户端批次标记。
- 后续虚构个人的 Save/Complete、缺目标 Complete 均拒绝。首次串行验证在正常工作区登录前遭 API rate limit，真实失败保留，不调整限流、不把它写成全部通过；恢复后继续验证。
- 限流恢复后，同一合成 Review 的缺目标拒绝及正常工作区链复验通过：ASSIGN/Save 不增加任务，随后 Complete 生成实际 AssignedWork `8f3ba1bd-c937-11f1-8f36-4ee612046d7e`、`8f3ab759-c937-11f1-8f36-4ee612046d7e`。两行身份、办理人与 UI 切片一致，生成后未标记分派修改被拒绝，pageErrors=0。没有切换工作区、修改目录成员、改派或取消现有任务。
- 截图保留于 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_workspace-context-workspace-only.png`、同前缀 `only-narrow.png`、`child-1.png`、`child-2.png`；已目视 child-1 的脱敏行切片。工作区截图是故意清空目标的错误态，不用它冒充成功态。
- 新镜像完整 `pnpm -C frontend run regression:mi`：29文件234单测和6浏览器场景通过，最终 `OK — unit + screenshots`，不是 unit-only。

## 未完成门禁

角色/BU 当前资格与成员状态、声明的 BU 来源、真实角色池、创建阶段异常可见性、跨服务竞态、所有变量写入口保留、非标准启动、Preview/New Request 和完整生命周期往返仍需后续闭合。此批不能证明所有工作台授权或批处理整体已经完成。
