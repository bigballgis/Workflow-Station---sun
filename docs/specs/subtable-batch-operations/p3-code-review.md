# P3 Batch Add/Delete 代码审查记录

## 结论

CONDITIONAL。本次 P3 代码批次已补齐工作区混合操作、冻结行设计、批量 PK 准备、明确删除及 Save/Complete 的共同校验边界；不等于 P3 运行验收或 P4 发布验收通过。

审查范围从 `50b453b65` 后的本次改动开始，包含后端、DW 和 Portal；未扩大到已生成任务改派、业务物理表、旧实例迁移或 Docker 数据修复。

## 首轮 Findings 与处置

### Blocker

- `SubTableBatchChangeGuard.validateSubmittedRows`：DELETE 原实现要求目标行仍在提交数组中，实际删除不能通过；ADD 又跳过 canonical 验证。
  - 影响：合法删除被拒绝，新增声明无法证明指向真实提交行。
  - 处置：DELETE 必须不存在于最终 canonical，且有明确的版本化删除声明；ADD 必须携带已准备的配置身份并匹配真实行。`SubTableBatchSubmittedRowsTest`、`SubTableBatchMembershipGuardTest` 覆盖正反例。
- `SubTableBatchRuntimeSnapshot` / `SubTableBatchRowDesign`：Admin binding 的 fieldDefinitions 来自实时 DW enrichment，并非已发布字段契约。
  - 影响：未重新发布的 PK/FK/字段变更会污染新实例捕获，并使“冻结配置”失效。
  - 处置：字段、PK 策略、FK 引用及父字段从发布包解析；仅采用解析出的环境表 ID。`SubTableBatchRowDesignTest` 使用不同的 live/published 字段证明不采信 live enrichment。冻结 scope 在捕获时校验。
- `SubTableBatchEditDialog`：按原数组下标计算变化；删除前一行后会丢失后一行修改。
  - 影响：混合操作会编辑错误的基线或丢失合法变化。
  - 处置：基线和草稿按稳定身份匹配；新增临时身份只保存在 WeakMap；整批成功只发一次工作区应用事件。混合操作组件测试通过。
- `SubTableBatchAddedRowGuard`：新增 canonical 行必须验证完整字段和不在批量表单中的额外字段，不能只验证声明字段名。
  - 影响：可伪造隐藏业务字段、非法值或重复身份。
  - 处置：完整新行按冻结字段类型、必填、范围、表单规则和允许列校验；拒绝非空计算字段赋值、未授权额外列及重复身份。新增负例通过。

### Major

- `useTaskForm` / `subTableAutosave`：保存成功后清空全部 pending，会抹掉请求期间的新批次；定时保存也可能并发发送同一批新增。
  - 处置：仅分离本次提交的 generation，失败恢复并归一化；后续批次保留；自动保存串行等待上一请求及版本刷新，手动保存先等待自动保存。保存期间的新页面数据不由旧 payload 覆盖。
  - 证据：新增/删除跨请求、失败恢复、后续编辑保留和保存串行测试通过。
- PK 准备路径若逐行 emit 或使用实时 DW 策略，可能部分应用父行或改变实例编号策略。
  - 处置：父/子 PK 在私有上下文准备，子 PK 按字段一次预留整批；只有全批成功才应用父 patch 和子行。分配端点通过任务资格和冻结控件检查，使用冻结策略。允许号段消耗，不宣称序列无间隙。
- 同 binding 的批量 scope / 行身份若继续读取 live PK/FK，部署覆盖后会与快照不一致。
  - 处置：仅携带 ADD/DELETE 声明的 binding 在提交构造及后端 scope 中使用冻结元数据；无批量声明的既有路径保持不变。更名/覆盖负例及删空 payload 测试通过。
- 新增必填或人工 PK 未出现在批量表单时仍允许保存设计。
  - 处置：DW 保存校验配置完整性，服务器捕获时再次检查必填用户字段。三语错误文案同步。
- Add-only/Delete-only 入口、读取混合 Assignment 表单、过期弹窗与重复 PK。
  - 处置：统一工作区按独立能力禁用普通编辑；专用 Assignment 不作为普通列解析；应用前后复核基线；重复 PK 全批拒绝；删除及编辑列表分页，单次最多 200 个受影响行。

### Minor

- 清理本次引入但不可达的独立 Add 弹窗和事件；复用原工作区，不保留第二套无调用点的 UI。
- 将 batch callback/请求数据从 unknown 收口到既有 TaskFormSubmitRequest 的具体类型。

### Question

无新增业务方向分叉；未因技术细节要求用户重新确认 D1-D7。

## 重审

已检查上述修复增量及完整本次范围。未发现仍未处置的新增代码 Blocker；运行门禁和存量类型问题使整体仍为 CONDITIONAL，不能发布 PASS。

## 范围 / 验收映射

- A11-A15：多行 PK/FK 准备、人工 PK、准备失败不应用、版本化删除、删空及关联限制。
- A07-A10/A20/A25：稳定行身份、混合操作、取消、非法值、过期基线、分页及批次上限。
- A01-A06/A18：旧请求路径保留；新能力读取快照；MI 编排 membership 不开放。
- Apply/Save 不调用 Assign/Reassign、创建或取消引擎任务；Complete 仍沿原流程链。

## 触发矩阵

已对照 implementation-autonomy、code-review、config-over-heuristics、secure-coding-sast、portal-dialog-form-labels、verify-ui-fix-with-screenshot，以及数据范围、版本、测试、安全和 MI 回归规则。

## 编译与静态检查

- `mvn -q -pl backend/user-portal -am package -DskipTests`：通过。
- `pnpm -C frontend/developer-workstation run build`：通过。
- `pnpm -C frontend/user-portal run build`：通过；现有 Sass / chunk-size warning 未隐藏。
- Portal 全量 `typecheck`：未通过，不能宣称全仓类型门禁通过。触及链上的剩余输出为存量 `SubTableField.vue` 的 null bindingLinkMode 类型和未使用 FieldRenderer；本批新增文件/函数的类型错误已消除。
- `git diff --cached --check`：通过。

## 测试与运行证据

- 后端实际执行：SubTableBatchRuntimeSnapshotTest、SubTableBatchRowDesignTest、SubTableBatchScopeDesignTest、SubTableBatchAddedRowGuardTest、SubTableBatchMembershipGuardTest、SubTableBatchSubmittedRowsTest、SubTableBatchDeleteReferenceGuardTest、SubTableBatchFormRulesTest、PortalBatchPrimaryKeyAllocationComponentTest、SubTableBindingScopeGuardTest、TaskApprovalCompletionSubTableGuardTest：通过。
- Portal 批量 helpers、工作区、保存串行、真实 submit payload 及 projection：本次组合命令 10 个文件、59 个测试通过；其后加入 mixed Assignment 解析测试，单独重跑相关两个文件，13 个测试通过。
- DW batchSubTableMembership / batchSubTableConfig：2 个文件、8 个测试通过。
- 完整 `pnpm -C frontend run regression:mi` 已执行，但 Playwright 打开 `http://localhost:3000/portal/` 30 秒超时，整体失败；不得用单元阶段通过代替完整门禁。
- Docker Desktop 报无法启动；未部署本批新产物、未取得新运行截图，未进行双 FU/多操作者 UAT 或真实数据库事务回滚测试。

## Function Unit 通用性与生命周期

- 通用性：静态以字段/binding/控件元数据驱动，无特定 FU 或业务列名判据；运行双样本未验证。
- Save/Load：本批新增 DW 完整性校验；既有 batch 容器保存链未更换。
- Export/Import、Clone、Version/Rollback、Admin 两条 Import：本批未修改这些消费者；沿用前序阶段的容器携带/重写链，完整运行往返仍属于发布门禁，不能以本批单测替代。
- Portal runtime/parity：两端 build 已通过，部署、截图及新旧实例成对回归未验证。

## 增量敏感信息

逐提交扫描 staged 新增/修改行和新文件，检查私钥、凭证赋值及邮箱模式：候选数 0。没有录入账号凭证或用户业务样本；提交说明不包含敏感值。

## 性能

子 PK 按字段整批预留，不逐行请求；提交仍沿一次既有 Save；工作区按 20 行分页、200 个受影响行上限。未压测，不宣称性能提升或生产容量结论。

## 残余风险 / 豁免

Docker、真实事务回滚、运行截图、双 FU/多主体、完整 MI 和完整类型门禁仍未闭合。当前仅允许作为代码批次保存，不允许报告功能运行完成或启用生产配置。后续恢复开发环境后按设计 A01-A41 执行验收，不放宽标准。

## 范围外问题

未顺手修复全仓既有类型问题、Docker Desktop 安装/系统设置或 P2 分派链的其他历史问题。此报告不声称 P0/P1/P2/P4 的全部门禁已重新验收。
