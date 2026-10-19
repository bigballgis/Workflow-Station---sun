# Code Review：批量行版本和删除身份保留

## 结论

PASS，仅限本批运行态数据保留修复。不是 P3/P4 完成或生产放行结论。

## Findings 与逐条处置

### Blocker

- `TaskFormComponent.persistTaskFormSnapshot`：初始任务完成时，引擎的无版本行覆盖本地已初始化的 `_wsRowVersion`。
  - 反例：真实新批量实例的两条初始行没有行版本；新增 SnapshotMerge 测试在修复前失败。
  - 影响：合法批量删除无法执行；不能把缺版本默认为 0 放行。
  - 处置：按冻结的配置主键保留已存在行的服务端版本，不给未知新行编造版本；原输入不变。
- `EngineSubTableHydrator`：空表与未填表混同，读取重新补入旧引擎行。首轮“启用批量的表一律不补”又误拦初始空表的后续 Service 输出。
  - 反例：真实 Delete→重开出现旧行；新增 initially-empty 测试证明整表保护不正确。
  - 处置：只记录已通过权限、scope、版本及完整批次校验的实际删除身份。读取按配置主键过滤这些旧行；新身份的服务输出仍可补入。删除记录与不可变能力快照分开，均为本地服务端所有。
- Complete 将合并后的本地内部元数据送入引擎，完成后的写回也需要保留本地删除状态。
  - 处置：发引擎前剥离保留字段；本地写回单独保留快照、版本及删除身份。客户端/引擎不能创建或覆盖保留字段；响应 DTO 不序列化它们。

### Major

- 合法重用人工 PK 时，删除身份不能永久阻断普通或批量 Add。
  - 处置：经过正常写边界的已有新行释放对应删除身份；Save 和 Complete 都执行。只释放实际存在的身份，不清空整张表的记录。

### Minor / Question

无剩余本批 finding。没有改变业务方向、任务创建时机、存量实例或业务表结构。

## 范围 / 验收映射

A11/A12/A14/A19/A26：初始行版本、保存与删空、元数据保留及隔离。可变删除记录 `__subTableBatchDeletedRows__` 只用于区分已删除旧引擎行和新服务输出，不是第二份业务数据或客户端删除协议。

## 触发矩阵

implementation-autonomy、code-review、bugfix-verification、config-over-heuristics、secure-coding-sast；JSON 行存储、旧请求兼容、MI 保存/完成边界、批量性能。

## 编译与静态检查

`mvn -pl backend/user-portal -am package`，指定 DeletedRows、HydratorOwnership、SnapshotMerge、ResponsePrivacy、RuntimeSnapshot、WriteIsolation、CompletionSubTableGuard 七类测试：BUILD SUCCESS，45 个测试，0 失败。已有 deprecation/unchecked、JaCoCo 历史执行数据 warning 未隐藏。完整 staged diff、diff check 和增量敏感信息检查通过。

## 测试与运行证据

- 关闭删除身份过滤再执行 DeletedRows/HydratorOwnership：5 个测试中 4 个失败；恢复过滤后全部通过。覆盖旧行排除、同表新服务身份、初始空表、旧实例、人工 PK 重用、坏元数据及引擎伪造。
- package 后重建 user-portal Docker 服务，新容器健康；不是仅 mvn test 后测试旧镜像。
- 隔离新建手工 PK / UUID PK 两套 FU，字段和 binding 不同：真实 Add→Apply→自动保存→重开→Delete-all→重开→Complete 均通过；重复 Add 409 / 假控件 403 均未写入伴随字段改动。桌面和窄屏截图保留，无浏览器错误。
- 本批完成后的两条测试实例数据库状态 COMPLETED，业务行为空，本地快照与删除身份仍在；Flowable `act_hi_varinst` 中两个内部变量名记录数为 0。完成响应不含内部字段。
- 同表服务输出正反例以真实组件+mock 引擎单测验证；未执行真实 ServiceTask 写回节点、事务中途数据库故障注入或跨用户完整 UAT，不将预校验拒绝冒充事务回滚证据。
- 可重跑脚本随本批保存：create/start/verify/complete-batch-verification-fixture.mjs，deploy 状态等待由 fixture-deployment.mjs 复用。只操作已标记的 synthetic FU/固定任务；不自动选择真实业务任务。

## Function Unit 通用性与生命周期

主键与 store 来自实例冻结的 binding，不猜列名。运行覆盖两套新建不同 PK/字段 FU；MI 的已有克隆样本仍需最新后端再回归。设计包结构、本次 Export/Import/Clone/Rollback/Admin 导入 N/A：删除身份属于实例本地状态，禁止导出到其他实例。无 SQL/实体列/业务物理表变化。

## 增量敏感信息

扫描新增/修改行及新增源码、测试、本文：无私钥、凭证赋值或真实邮箱候选；测试业务值是明确的 synthetic 数据。错误不包含内部字段正文。

## 性能

身份过滤使用 Set，按行与配置 PK 列线性处理，无每行远程调用。实例的删除身份累计容量与回退旧代码的保留兼容仍需 P4 验证，不宣称生产容量结论。

## 残余风险 / 范围外问题

P1/P2 的权限投影、完整摘要、DW Preview/New Request、统一 Assignment 工作区、目标资格/阶段及创建结果门禁尚未全部闭合。P3 真事务故障回滚、P4 全生命周期和完整类型基线仍未通过；不能由本批 PASS 推导完整功能完成。回退不得删除实例保留元数据或恢复整份旧 JSON。
