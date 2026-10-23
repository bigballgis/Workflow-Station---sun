# Code Review 结果 — staged changes（2026-10-23）

## 结论

**CONDITIONAL**。已审完整 staged 范围，未发现未解决的 Blocker/Major；一项验证契约问题已修正并由独立 reviewer 重审 PASS。代码、构建和相关回归证据支持本地提交；完整 FU 生命周期组合、全套 MI 截图管道和原 UAT 仍未验收，不宣称运行验收全部 PASS。

用户已在上一轮明确列出未验证范围的交付后授权“review staged changes，没问题就提交”。本次提交保存已审变更，不执行 push 或生产发布。

## Findings

### Blocker

无确凿新增 Blocker。

### Major

**R1（已修，重审 PASS）：旧测试及脚本把普通编辑误当成缺少 batch 声明的非法写入。**

- 位置：`backend/user-portal/src/test/java/com/portal/component/SubTableBatchLookupAssignmentTest.java:38`；`frontend/scripts/verify-batch-lookup-assignment-runtime.mjs:294`。
- 反例：harness 的普通写入 binding resolver 返回空 Map，只有带 batch changes 时 scope overlay 才补 binding。旧 `assertThrows(RuntimeException.class)` 实际因缺 binding 而通过，不能证明普通修改需要 batch 声明。真实 form 应提供固定版本的绑定。
- 影响：旧负例与“batch仅追加限制”的确认方案冲突，可能误报正确行为或掩盖另一原因的失败；未发现因此新增的产品代码缺陷。
- 最小修正：该测试提供 published binding，再改为不带 batch 声明的普通分派成功；断言目标字段实际保存、5个Lookup及兄弟行不变。脚本保留显式 ASSIGN 声明，仅将 assignment intent 置空，验证非法批量意图。
- 证据：补齐 binding 后旧断言失败：`Expected java.lang.RuntimeException to be thrown, but nothing was thrown`（本地 `.codex-review-ordinary-fixture-red2.log`）；改正确断言后相关 Portal **368项通过**（`.codex-review-final-green.log`）。独立 reviewer 对两个文件只读复审 PASS。
- 依据：需求“batch只追加批量限制”、code-review 测试质量、bugfix-verification 同一证据与禁止假证据。
- 验证缺口：调整后的脚本未完整重跑；它是本地合成 fixture 工具，产品代码没有因本 finding 改动。

### Minor

**R2（已修）：历史类型检查计数错误。**

验证报告此前写364条，重新按完整 `error TS` 行统计基线、前一轮最终及本轮日志均为 **383条**。归一化行列号后逐项 Counter 比较，无新增/消失签名。已更正报告及外部问题文档；类型检查仍为 FAIL。

### Question

无遗留需要用户决定的业务分叉。

## 范围 / 验收映射

- 初始66个staged文件：Portal普通保存/完成及batch分派、Lookup身份与人员编辑、虚拟User关系表发布/导入、测试、验证脚本/合成CSV、问题记录、截图。
- 独立Portal reviewer审查全部11个生产文件及相关测试，共21个文件；独立portability reviewer审查Common/DW/Admin消费者链；主审核对前端、脚本、文档、证据与增量扫描。
- 修正R1/R2和新增本报告后合并纳入同一提交；没有改动产品业务代码，没有schema、依赖或配置变更。
- 本次没有相关未跟踪源码漏提交；先前用户授权的暂存内容保留。

## 触发矩阵（命中项）

- 通用正确性/最小diff/错误处理、输入权限与XML、批量并发、前端Lookup热路径、i18n、弹窗label、MI隔离、FU可移植性。
- 配置驱动：PK/字段/MI管理字段/分派字段均从元数据读取；平台虚拟User稳定ID及平台显示字段属于既有平台契约，无MCY具体ID或业务表名分支。
- N/A：无Entity/schema迁移、部署环境变量变更、View access规则变更、新依赖、生成指导文件变更。
- backend/AGENTS.md、frontend/AGENTS.md当前磁盘不存在；读取根规则及命中路径规则。

## 编译与静态检查

- `mvn -o -pl backend/admin-center,backend/developer-workstation,backend/user-portal,backend/workflow-engine-core -am package -DskipTests` → **BUILD SUCCESS**，Common及直接服务均编译打包。
- `pnpm build`（frontend/user-portal）→ **PASS**；保留现有Sass/chunk提示，没有降低门禁。
- `pnpm typecheck` → **FAIL（既有383条）**；与本轮修改前工作区基线归一化比较新增0/消失0，未降低strict或忽略错误。
- `node --check frontend/scripts/verify-batch-lookup-assignment-runtime.mjs` → **PASS**。
- `git diff --cached --check` → **PASS**。

## 测试与运行证据

- Portal相关宽回归：`SubTableBatch*Test,TaskSubTableSubmissionTest,TaskApprovalCompletion*Test,TaskFormComponentMiSubmitIntegrationTest,TaskFormComponentSubTableFieldPermissionTest,CatalogPinnedTaskFormLoaderTest,PortalRelationTable*Test,ProcessSubTablePrimaryKeyEnricherComponentTest,PortalCatalogPrimaryKeyAllocationTest` → **368 Portal +5 Common通过**，R1修正后复跑仍通过。
- 独立Portal reviewer抽查policy、assignment-only、Lookup assignment与真实事务 → **53项通过**（上组子集）。
- 独立跨模块：Common2、Admin7、DW25 → **34项通过**。包含虚拟schema JSON roundtrip、冲突拒绝、snapshot/ZIP普通/虚拟relation、DW/Admin导入、相关clone/export/import/rollback parity测试。
- 前端Lookup、子表弹窗及lookup cell：**60项通过**；核验上一轮MI单元日志 **234项通过**。
- 本轮未重新运行 `frontend/pnpm regression:mi` 完整“单元+全部截图”管道，不能把234项写成完整门禁PASS。上一轮真实Edge/CUA截图与查库证据见 `2026-10-22-batch-assign-lookup.md` 的方案二节。
- 原失败Investigation同任务红转绿、原FU新请求普通Add/Edit/批量分派/进入Approval、第二FU不同映射保存均有已部署页面和数据库证据。本轮review未新增业务写入。

## Function Unit 通用性与生命周期

- 通用性：**静态 PASS，运行有边界**。独立合成新FU初轮及原FU、第二已有FU有不同范围运行证据；完整新建/导入FU生命周期组合未覆盖。
- Save/Load：原FU页面保存、刷新及固定版本取表单已验证。
- Export/Import：Exporter snapshot/ZIP统一relation结构与名称，DW importer通过同一Portability映射，当前规范schema roundtrip测试通过。
- Clone：既有Cloner复制稳定虚拟relation ID；相关binding测试通过，完整虚拟User的页面clone未验收。
- Version/Rollback：SnapshotRestorer使用同一name→id映射；相关parity测试通过，完整页面rollback未验收。
- Admin两条Import：两个HTTP入口经同一Manager/ImportComponent/StructureImporter；parser纳入RELATION DATA_TABLE catalog；不创建sys_users对应业务rt表。
- Deploy/Activate：原FU 1.0.132实际发布通过，DW Deploy交同一ZIP给Admin。
- Portal runtime/parity：虚拟User公开已有8个schema字段；Lookup真实PK和设计显示列分离，原FU编辑及后继任务验证通过。未把本地Select样本写成UAT五个Lookup已验收。

## 增量敏感信息

- 范围：staged新增/修改文本、脚本/fixture/文档、文件名、拟提交message，及截图可见内容核对；不扫描本diff之外历史凭证。
- 文本扫描私钥/JWT/云key/token/凭据字面量无候选；脚本凭据来自既有环境变量登录工具；合成CSV卡号为显式测试值。
- 截图包含本地测试账号、合成交易和部署诊断；未发现明文口令/token/私钥。原始本地日志、fixture状态文件不提交。
- 结果：**PASS（本次增量）**。

## 性能

静态检查未发现新增逐行数据库/HTTP调用、无界业务查询或未清理监听。Lookup schema请求按表去重并限容量128；服务器提交准备为内存字段/行校验，发布元数据按任务读取。不是性能优化提交，没有声称P95改善；没有独立负载测量。

## 残余风险 / 豁免

- 类型检查存量383条不是本次新增，后续单独治理；本提交不扩大成全仓类型修复。
- 原UAT五个Lookup、所有审批/MI分支结束、最新policy双账号页面隔离、全新/导入FU完整Clone→Rollback→Deploy→Portal组合及全套MI截图管道未验证。责任范围仍是后续环境验收，详情关联既有#1694及验证报告。
- 调整后的fixture脚本负例未运行，只通过语法和静态核对；不把历史missing-intent输出当新方案的正确负例。
- 用户授权本地提交，不执行push或生产部署。

## 范围外问题

全仓TypeScript存量错误；此前无开发组克隆草稿权限问题仍见既有#1695。本轮未改台账状态或处理范围外产品问题。
