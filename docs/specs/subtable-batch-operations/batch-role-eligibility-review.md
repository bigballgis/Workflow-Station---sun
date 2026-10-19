# Code Review：初次分派的 BU/Role 实时准入

## 结论与边界

CONDITIONAL。本批在增强实例 Save 的实际分派变化、Complete 拟生成集合上，复核 BU/Role 的存在、状态、准入关系和至少一位有效成员。不是引擎最终候选人过滤、跨服务竞态或 P4 完成。旧无快照路径不变。

## Findings 与处置

- 新 `SubTableBatchRoleEligibility` 使用平台固定目录 schema，BU/Role 按 code 精确配对，要求 BU ACTIVE、Role ACTIVE 且 BU_BOUNDED、有 BU-Role 准入，存在 ACTIVE 且未删除的 UBR 用户。去重后单次参数化 SQL；IN 的交叉结果仍按原配对检查，不扩大授权，不读用户姓名或凭证。
- 配置有 `buField` 时从该冻结字段取 code；未声明时只从服务器 baseline 的 `activeBusinessUnitId` 精确查 code（额外一次查询）。来源依据 DW 现有 buFieldTip 及引擎角色分派契约，不按列名猜测、不把 ID 当 code、不取提交的工作台值。缺来源明确拒绝；前批已保护两个 workspace root 字段，但所有其他变量写入口仍未全部闭合。
- `SubTableBatchAssignmentDifferences` 抽取共享 changedRows，保留任一分派字段变化即复核最终目标的语义；普通字段变化、未提交 store 不产生角色查询，不检查同控件内无变化的其他行。角色目标不转换成个人。
- Save 接入既有事务的 scope/阶段/字段校验后、putAll 前；无客户端 batch 标记也检查。Complete 在严格行构造完成后、collection put 前同时复核个人及角色，保存后失效仍拒绝。不调整任务创建/领取/改派算法。
- checker6项先 RED4失败0错误，后 GREEN6。新增角色差异2项；collection新增已保存角色失去准入或全部活跃成员后拒绝且不 publish。旧可选 BU 测试补服务器 workspace ID 与真实 H2 目录，不删除可选字段正例、不放宽产品校验。
- 独立同源关键组 RoleEligibility6、Differences10、MiCollection17 共33项，失败/错误/跳过0；8份关键源码/测试 SHA256 一致。静态首审暂无新增 Blocker/Major；引擎最终成员、BU消费一致性及真实单人池仍列门禁，不能由此推导完成。

## 构建与部署

- 隔离副本 Maven Portal package：选定批量/MI/权限/事务边界/原任务路径/Client 共467项，失败/错误/跳过0。
- 新 Portal jar SHA256 `20CA9A266AD239CCF5551D0A4F7620C7DAF7362038BFF3589F14C6B3D80957D8`；重建镜像 `sha256:16d6a97b77e46fc543d947e9a607d99141d51cf7fd6ab199977492ee5bf3346e`，healthy 后运行。
- 未改前端产品或共享 jar、Entity/schema、FU包、API契约与配置路由；既有383项前端类型错误仍开放。没有新增全表目录查询或逐行 HTTP，但200行及目录大集合性能尚未实测。

## 真实运行

- 新合成 FU50036，实例 `bf0c9ec6-c939-11f1-8f36-4ee612046d7e`，Review `bf196f54-c939-11f1-8f36-4ee612046d7e`：`verify-batch-role-eligibility-runtime.mjs` 使用两行虚构 BU/Role、无 batch marker 的 Save/Complete，均明确拒绝；完整 fieldValues 不变、仍停留 Review。未改目录或真实成员。
- 随后缺目标拒绝、合法个人工作区 ASSIGN/Save 不创建任务、Complete 实际生成两项 AssignedWork `daf9d1c4-c939-11f1-8f36-4ee612046d7e` / `daf8c050-c939-11f1-8f36-4ee612046d7e`，身份及办理人对应、UI各一行、生成后伪造分派拒绝，pageErrors0。此正例是个人分派，不冒充真实角色池。
- 截图保留 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_role-preflight-workspace-only.png`、同前缀 `only-narrow.png`、`child-1.png`、`child-2.png`；工作区图为刻意清空目标的错误态。
- 新镜像完整 `pnpm -C frontend run regression:mi` 通过：29文件234单测及6浏览器场景，最终 `OK — unit + screenshots`。真实角色 Save 事务回滚、角色-only/单人候选池及预校验后目录变化的真实引擎结果未验证。

## 后续发布门禁

当前存在一个活跃成员不等于引擎最终只包含活跃成员。后续必须核对真实候选池、可选 BU 默认来源在引擎的消费一致性、创建失败可见性及 TOCTOU。全部变量写入口、非标准启动不可变来源、Preview/New Request、父 scope 限制和全生命周期往返仍开放，不将本批记为 P2/P3/P4 完成。
