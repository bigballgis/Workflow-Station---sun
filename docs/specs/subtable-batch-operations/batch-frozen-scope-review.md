# Code Review 结果：全部批量操作的冻结绑定范围

## 结论

CONDITIONAL；仅交付本 concern，不代表 P3/P4 完成。第二 reviewer 完整重审无 Blocker/Major/Minor。

## Findings 与处置

- 已修：Edit/Assign 未进入冻结 scope overlay，同 catalog 内容覆盖后仍使用新 PK/FK。删除仅 Add/Delete 的筛选，所有已声明批量操作均从实例快照恢复绑定；普通无批量意图请求保持原路径。
- 已修：控件 ID 仅表单内唯一，跨表单按 ID 取 first 可能串用。现在同时匹配控件 ID 和 binding ID，并要求恰好一个结果；缺失或歧义返回409。
- 已补：启动捕获时所有批量控件均验证可解析的过滤 FK、父表及身份；不能延迟到第一次 Apply 才发现范围元数据缺失。
- 第二 reviewer 建议的 MI 覆盖已补：自身 PK 型 foreignKeyField 不可抢占显式 filterFkFieldName；Edit/Assign 保留 miParticipantRow 分类，不开放参与者 Add/Delete。

## 范围 / 触发矩阵

A06/A16/A40：快照绑定隔离、父行范围、控件身份。命中 code-review、config-over-heuristics、安全鉴权、错误治理、MI 热路径和部署规范。没有新增 DTO/数据库列或改变普通实例配置协议。

## 编译与静态检查

- RED：参数化测试四操作中 Edit/Assign 两项失败，实际为覆盖后的 other_key，期望冻结 line_key。编译通过后的真实断言失败。
- GREEN：`mvn -pl backend/user-portal -am test "-Dtest=SubTableBatch*Test,EngineSubTableHydratorBatchOwnershipTest,PortalBatchPrimaryKeyAllocationComponentTest,SubTableBindingScopeGuardTest,CatalogPinnedTaskFormLoaderTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：126项，0失败/错误/跳过，BUILD SUCCESS。
- Scope 9项包含四操作、MI、缺快照拒绝、普通保存不变、其他绑定同名控件、重复快照拒绝；调用真实 ScopeGuard 验证合法父范围通过、越父拒绝及不查询 JDBC。
- DeletedRows 手写 fixture 缺捕获必有的 bindingId 曾导致2错误；补齐字段后完整重跑，不删除或放松墓碑断言。
- `mvn -pl backend/user-portal -am package -DskipTests`：BUILD SUCCESS。无共享 jar 源码变化，仅重建 Portal。

## 运行证据

- 最终 Portal jar 已重新构建 Docker 服务并确认 healthy；未修改数据库或共享 jar。
- 最终部署后创建合成 Edit-only 实例：逐行 Apply、持久化重读、版本递增、未选行不变通过；省略批量意图伪造只读字段返回403且重读未变，浏览器 pageerror=0。
- 完整 `pnpm -C frontend run regression:mi`：29个测试文件、234项单测及6个浏览器场景通过，包含真实页面截图；不是 unit-only。
- 桌面截图：`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-editonly-desktop.png`；窄屏：同目录 `2026-10-15_batch-editonly-narrow.png`。无本 concern 的可见 UI 变化。

## Function Unit 通用性与生命周期

配置驱动，不按业务列名判语义。序列化格式未变，生命周期重写器 N/A；完整批量 Clone/Import/Rollback 验收仍保留。无快照旧实例、普通空 changes 请求不因本补丁自动升级。

## 增量敏感信息 / 性能

第二 reviewer 复核79行新增候选0，diff check PASS；最终 staged 内容另扫。无新增 DB/HTTP 调用；未执行大定义负载压测。

## 残余风险 / 范围外问题

仅覆盖声明批量操作的冻结 binding overlay。当前 catalog 解析仍先于 overlay，损坏的当前产物可能导致拒绝；不宣称普通保存或所有表单解析已摆脱 live catalog。MI 当前参与者的服务端权威来源、真实分派阶段/资格、同工作区 Assign、Preview、New Request 摘要、完整生命周期和 PostgreSQL/JPA 并发验收尚未完成。H2事务测试与单主体合成样本不替代这些门禁。
