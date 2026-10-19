# Code Review 结果：Edit/Assign-only 冻结字段元数据

## 结论

CONDITIONAL；本 concern 不代表 P3/P4 完成。第二 reviewer 未发现范围内 Blocker/Major/Minor；剩余阶段门禁继续保留。

## Findings 与逐条处置

- 已修：快照只在 Add/Delete 启用时捕获 rowDesign，Edit/Assign-only 缺失字段/身份/权限元数据，Edit 的冻结权限检查和普通保存当前只读检查因此跳过。现在所有启用控件捕获发布字段；Edit 和任务权限校验缺元数据明确拒绝，不再视为合法宽松协议。
- 已修：字段捕获和 membership 资格分开。Edit/Assign 支持已声明 structuralFk 或 miParticipantRow；Add/Delete 仍只支持 structuralFk，仍经原 scope 与 membership 校验，不开放参与者增删。
- 兼容边界：无增强快照的旧实例路径不变；既有试验性增强快照若缺 rowDesign，表写入现在返回错误，不能回填实时设计。不是对所有历史增强快照的兼容承诺。
- 第二 reviewer 只读复核完整产品 diff、InitialRows/mergeSnapshot/前端解析/ScopeDesign 消费者与验证脚本，无新增 finding。其未重复运行 Maven，避免共享 target 冲突；核验真实 Surefire 报告。

## 范围 / 触发矩阵

A04/A06/A19/A26：冻结字段权限、缺元数据拒绝、当前任务权限不可因省略批量意图绕过。命中 config-over-heuristics、code-review、安全鉴权、错误治理、测试和部署规范；只改 Portal 新能力的元数据捕获/校验。未重构普通流程或分派算法。

## 编译与静态检查

- RED：Edit-only 新断言运行真实捕获，rowDesign 为 null，1 测试断言失败；最初测试泛型编译错误已修，编译错误不算 RED 证据。
- GREEN：`mvn -pl backend/user-portal -am test "-Dtest=SubTableBatch*Test,EngineSubTableHydratorBatchOwnershipTest,PortalBatchPrimaryKeyAllocationComponentTest,SubTableBindingScopeGuardTest,CatalogPinnedTaskFormLoaderTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：117 测试，0失败/错误/跳过。
- 新增覆盖冻结只读权限不随后续配置放开、缺字段快照拒绝、未标记普通保存只读校验、MI 字段可捕获但不能 membership。
- `mvn -pl backend/user-portal -am package -DskipTests`：BUILD SUCCESS；仅 Portal 产品代码变动，无 shared jar 源码变更。
- 两验证脚本 `node --check`、diff check PASS。前端产品代码未变，本 concern 沿用前一提交已部署产物；完整类型/lint 债务未因此变绿。

## 测试与运行证据

- Portal 后端重建部署健康，启动日志未匹配 VerifyError/ClassFormatError/NoClassDefFoundError/启动失败。未改数据卷。
- 新建并部署唯一合成 FU 50028，PROCESS/TASK 均 Edit-only 且数值字段只读，通过真实启动和任务保存验证。`verify-batch-editonly-runtime.mjs` 核对冻结字段、初始版本、无 Add/Delete 命令、独立草稿不落库、Apply 后 GET、未改兄弟行业务值、未标记只读伪造返回403且后续 GET 完全不变、刷新恢复。
- 首次脚本对兄弟行完整比较遇到版本 1→2。源码确认现有 ScopeGuard 对所有 claimed 行精确递增一次；脚本改为只允许该精确版本变化，其余字段仍完整 deepEqual，另起新合成实例重跑 PASS。第二 reviewer 核查这不是放宽业务断言。
- 首次截图拍到过渡动画，不计视觉通过；等待有限动画完成后对同一已保存任务只读重拍，已查看桌面和窄屏，字段标签完整、按钮不遮挡，表格内部滚动。
- 截图绝对路径：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-editonly-desktop.png`、`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-editonly-narrow.png`。
- 完整 `pnpm -C frontend run regression:mi`：29 文件234项单测及全部6个浏览器场景 PASS。第一次执行遇登录限流，未计通过；未修改限流，冷却后完整重跑通过。

## Function Unit 通用性与生命周期

静态通用性 PASS：配置驱动、无 FU/业务列名硬编码。新 Edit-only 合成 FU 完成部署/启动/保存；MI Clone 样本保留旧无快照路径回归。配置 JSON 格式未改变，DW Export/Import、Clone、Rollback、Admin 两条导入结构 N/A（本补丁不改这些序列化器）；它们的完整批量 round-trip 验收仍未完成。新实例字段快照与版本初始化不反向写旧实例。

## 增量敏感信息 / 性能

第二 reviewer 扫描产品新增90行候选0；最终 staged 内容另扫。测试仅指定 synthetic FU 和任务，不使用真实业务数据；未添加凭证或更改登录限流。新增本地字段捕获无远程查询或逐行数据库访问；未做大定义负载压测。

## 残余风险 / 范围外问题

Edit/Assign scope overlay 仍需冻结完整 binding 语义；BPMN 实际执行阶段/目标资格/结果核对、Preview、同工作区 Assign、New Request 摘要复核与完整生命周期仍未完成。H2事务测试不代替真实 PostgreSQL/JPA 失败竞态验收。不能以本批117测试或单合成样本替代这些门禁。
