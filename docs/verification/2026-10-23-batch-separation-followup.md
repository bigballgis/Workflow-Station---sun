# Batch 与普通子表写入分离：复核及修正

2026-10-23，基于 `6762b6ee5` 的后续修正；提交记录见本文件所在 Git 历史。

## 结论与根因

此前把现场问题归因于 FU 配置不准确。普通写入复用了 batch 严格标量类型校验，导致原先可保存的 VARCHAR + 数字输入控件的 JSON number 被拒绝。修复恢复普通表单原有值契约；权限、未知字段、PK/FK、MI 归属仍检查。没有 FU 名称或金额字段名特判。

## 分离审计与处置

1. **已修：普通写入调用 batch 类型校验。** 移除 TaskSubTableSubmission 对 batch metadata validator 的复用。
2. **已修：声明一个 batch 操作却扫描其他控件、其他行。** batch 附加规则限定为显式声明的 control/binding/row；另一个行的普通编辑不继承 batch 类型规则。
3. **已修：ASSIGN 混有普通字段编辑时整行走 batch 类型规则。** 始终验证 assignment intent 和全部分派目标字段；其他普通字段沿普通权限链。新建行没有 baseline 也必须检查分派目标类型。
4. **已修：普通单 binding 不生成 scope。** 使用原有 binding 身份与 scope 构造器，普通可写 binding 也发送行声明；保留共享只读 binding 原有 scope 行为。
5. **已修：Complete 前 GET 最新版本号覆盖旧业务数据的版本。** Complete 只等保存结束并检查失败状态，不通过刷新版本掩盖旧数据。成功保存后的 GET 仅接受本次 claimed row 的预期递增版本，否则拒绝回填并显示失败状态。
6. **已修：API 省略 scope 可绕过版本隔离。** 已版本化 store 发生显式修改或删除时，服务端要求 scope，再由既有 guard 检查身份及版本。省略整个已版本化 store 时保留 baseline，不把部分保存当删除。
7. **已修：异步保存失败后没有持续状态，Complete 仍可能继续。** 保留草稿、持续提示、提供重试，失败时阻断 Complete。MI 的重试走现有 MI 保存 wrapper。保存返回后刷新失败也保留错误状态。
8. **已修：并发事务乐观锁冲突显示 500。** 真实双页加载复现 ObjectOptimisticLockingFailureException；通用异常处理映射为 409 / DATA_CONFLICT，响应不暴露实体详情。
9. **已修：负例测试因错误原因失败。** Lookup 只读测试明确配置只读权限，行身份负例实际调用身份 guard；普通数字兼容测试不再沿用 batch 类型断言；事务 harness 补充发布 binding 和普通 scope。保存两次测试改为服务端实际 +1 契约，不再模拟无因跳到版本 5。

## 保留逻辑与后续疑点

- `SubTableBatchTaskPermissions.activate` 仍按 runtime marker 激活 publishedFieldPermissions；CatalogPinnedTaskFormLoader 已对所有任务设置同一 published 权限。没有发现二者实际内容不一致。本轮保留；后续可用统一 loader 契约测试消除重复装载，不能直接删除权限检查。
- 已开始任务的分派锁定、人员资格、workspace 元数据保护仍覆盖实际分派变化。这些是业务授权要求，不能因操作来自普通 Edit 就绕过。
- 未版本化的旧 V1 store 保留历史无 scope 行为；本轮不宣称把所有历史 API 一次性迁成强版本协议。普通页面的新保存会携带 scope，进入版本化路径后不得再降级。彻底迁移应先盘点旧客户端，再统一版本协议。
- 页面初始化仍可能触发 autosave；两个页面同时加载可产生无用户编辑的并发写竞争。数据不会因此绕过版本/事务锁；本轮补了明确冲突响应和失败状态，但**尚未消除初始化的无效写入**。后续应区分 hydration 与用户编辑事件，并以“仅打开页面不发 submit、真实增删改仍发 submit”的页面用例验证，不能简单关闭 watcher。
- 页面内离开确认已实测：失败后点击 To Do 链接出现“Unsaved data will be lost”确认，接受后才离开；截图 `2026-10-23-unsaved-leave-confirm.png`。新建空白页的 Back to List（router.back）返回 about:blank；直接关闭/跨文档跳转的 beforeunload 仍需单独验收，不把页面内路由确认当成所有离开方式的证明。

## 红→绿与回归

- 普通 VARCHAR + Number：两套 PK，batch runtime 开/关；修复前 2 error，修复后通过。
- batch 跨行/跨 control 串扰：修复前因另一普通行金额类型失败，修复后通过；同一 ASSIGN 行普通金额编辑也覆盖。
- 新建行伪造分派目标：修复前接受 map，修复后拒绝；合法人员分派仍通过。
- 无 scope 改写/删除版本化行，以及省略版本化 store：分别先红，后通过。
- 普通 scope 开/关及 Complete 保持读取版本：3 red → green。
- 保存后 GET 取得另一写者版本：禁用新检查时 red，恢复后 green；本次正常 +1 回填仍通过。
- autosave 失败保留草稿、拒绝 Complete、重试成功才清错：先红后绿。
- 乐观锁错误处理 MockMvc：原 HTTP 500 → HTTP 409，固定安全响应。
- 最终后端：Portal **408 项通过**，common 本次选择 **8 项通过**；前端 tasks/subTableField/taskDetail **527 项、79 文件通过**；另 MI 配置回归 **234 项通过**。这些不是根目录完整 MI 页面门禁。
- Portal Vite build、Maven package 成功，两个 Docker 服务已重建；最终后端额外重建以纳入通用 409 处理。
- typecheck 仍有 **383 项既有错误**；本轮未修复该基线，不能称全量类型检查通过。

## 真实页面与数据库

所有写入经 Portal 页面完成，SQL 仅只读核对。

- MCY Debit Card_20260525，任务 `607e5b3d-ceb2-11f1-ac67-1ab7605b2169`：此前 Billing Amount 1→2 返回 400，数据库仍 1；本轮同一路径保存并刷新后为 2。最终恢复 1。
- 同任务创建合成行 `975d4232-f56f-4e2c-a412-306078fa7892`，Billing Amount 0；页面 Batch Edit 分派 liam 成功；普通 Edit 到 7 成功。
- 该合成行在旧镜像无 scope 路径复现旧页 7→9 覆盖新页 8。最终 scope 路径新页保存 10 / v3，旧页保存 11 返回 409，数据库仍为 10 / v3。
- 删除上述本次合成行后，数据库保留原来的三条用户记录；未推进用户 Assignment 任务。
- Batch Assign Demo，任务 `8631db55-ce91-11f1-ac67-1ab7605b2169`，不同 `entry_token / owner_ref / handler_code / units` 配置：普通金额 2→3 保存且刷新保持 3；最终恢复 2。证明未按 MCY 字段名实现。
- 本轮没有从页面重新完整跑 Investigation→Request approval→Approval 全生命周期；不能以这些 CRUD 结果代替该验收。

截图（相对仓库）：

- `frontend/user-portal/verification-screenshots/2026-10-23-billing-after-fix-reload.png`：刷新后 Edit 中金额 2。
- `frontend/user-portal/verification-screenshots/2026-10-23-alt-fu-after-reload.png`：另一 FU 刷新后金额 3。
- 失败状态由 AX 与控制台 409 核实；早先名为 `stale-save-banner` 的截图视口低于提示区域，不能作为提示可见的截图证据。
- `frontend/user-portal/verification-screenshots/2026-10-23-unsaved-leave-confirm.png`：未保存状态下页面内导航要求确认；最后使用无业务变化的旧页 Save 复验 409，再确认离开，数据库原三行金额仍为 1、2、3。

## 独立 Code Review

审查先后发现：新建行 ASSIGN 漏类型检查（Major）、MI Retry 无请求（Minor）、共享只读 scope 丢失（Major）、保存后 GET 采用其他写者版本（Major）、API 无 scope 降级（Major）、省略整个版本化 store 可清空数据（Major）。均已修正并重审。

最终重审 **PASS**，没有残余确凿 Blocker/Major；通用乐观锁 handler 最后一轮只读复核也 PASS。页面未验收项目和后续疑点如上，不能用 PASS 覆盖这些限制。

## 提交前 Code Review 结果

### 结论

CONDITIONAL（验证保留项）。代码审查未发现新增阻断问题；用户已在本地手工验收并于 2026-10-23 明确授权提交。此结论不替代完整 MI 页面门禁。

### Findings

- Blocker / Major / Minor / Question：本轮暂存区重审均无新增 finding；此前 finding 及修复已在上节列明。

### 范围 / 验收映射

- 24 个相关文件：普通与 batch 规则分离、版本化写入隔离、失败状态与冲突响应、三语文案、回归测试及验证记录。未纳入原始日志、构建产物或测试凭据。

### 触发矩阵

- 后端权限与并发、通用异常处理、Portal 热路径与 MI、三语 UI、测试及安全检查命中；设计器配置及 FU 迁移链未修改。

### 编译与静态检查

- 已核验前述 Maven package、Portal build 成功输出；本轮 `git diff --cached --check` 通过。
- typecheck 383 项既有错误与上次记录归一化比较无新增；不宣称全量类型检查通过。

### 测试与运行证据

- 提交前重新执行原选择的 Maven 回归：Portal 408 项、common 8 项通过，BUILD SUCCESS。
- 核验前端 527 项及 MI 配置 234 项通过、两个 Docker 服务重建和双 FU 页面/查库证据；本轮用户另已手工验收。
- 完整 `pnpm run regression:mi` 页面门禁、Investigation→Request approval 全链路及跨文档 beforeunload 本轮未重跑。

### Function Unit 通用性与生命周期

- 通用性：未按 FU、表或业务列名硬编码；现有 MCY 与另一套主键/字段名的 Demo FU 页面验证通过。全新 FU 生命周期本轮未重新创建验证。
- Save/Load、Portal backend/frontend：已验证，见上节。
- Export/Import、Clone、Version/Rollback、Admin JSON/Deploy import：N/A；本提交未变更设计产物、序列化字段或这些消费者。Deploy/Activate 沿用现有配置，不新增部署参数。

### 增量敏感信息

- 扫描暂存 diff 新增行及新增文件：未发现私钥、访问 token 或凭据赋值。日志与本地验证凭据未入提交。

### 性能

- 新增工作限定于写入及保存后的版本核对，无新增逐行网络或 SQL 请求。初始化 autosave 无效写入仍是下列后续项，不声称已消除。

### 残余风险 / 范围外问题

- 保留项、未版本化旧 V1 协议、权限装载重复及初始化无效 autosave 的处理办法沿用上节清单；本次提交不把它们标为完成。
