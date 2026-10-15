# Code Review：批量保存 PostgreSQL/JPA 事务验证

## 结论

第二 reviewer 完整 staged 重审：本验证 concern PASS，无剩余 finding。本批仅新增验证，不改变产品行为。完整功能仍为 CONDITIONAL，不代表 P3/P4 验收完成。

## Findings 与处置

- 镜像使用可变 tag：第二 reviewer 指出后改为 PostgreSQL 16.5 的固定 SHA-256 digest。
- 脚本要求 PowerShell 7：显式 `#Requires -Version 7.0`，不依赖 Windows PowerShell 5.1 的不存在 API。
- 手动 RepositoryFactory 最初缺事务 advisor、fixture 行为不可变 Map、Mockito spy 无法 unwrap Spring AOP proxy：均为测试装配错误，分别补事务 advisor、JSON 可变深拷贝、interface mock 委派真实 Repository。没有修改产品实现或放松断言来消除错误。

## 范围 / 验收映射

对应 A09/A10/A15 的本地事务、整批拒绝和并发边界。真实 `TaskFormComponent.submitTaskForm`、`ProcessInstance`、Hibernate JSONB、JPA Repository、JpaTransactionManager 和 `@Version`；仅外部元数据、引擎和无关旁路使用既有 stub。

## 验证

- 命令：仓库根目录 `pwsh -NoProfile -File scripts/verify-batch-postgres.ps1`。
- 最终结果：PostgreSQL/JPA 7项，H2 12项；合计19项，0失败、0错误、0跳过，BUILD SUCCESS。
- 两种不同 PK 字段：真实 flush 后抛异常，新的持久化上下文重读完整 JSONB、行版本、快照与实例 lockVersion 均未变。
- 第二行版本冲突：整批拒绝，数据库 JSONB 和 lockVersion 未变。
- 合法删空：空集合、两条服务端墓碑及原快照一起提交，实例 lockVersion 精确递增1。
- 两个真实并发写事务通过 latch 读到相同基线：恰好一个提交，一个乐观锁失败；最终墓碑不重复、lockVersion=1。
- 反向有效性验证：临时让晚失败用例的 SQL 使用 REQUIRES_NEW 提前提交，两个 PK 样本均在 fresh-read 完整 JSONB 比较处失败（7项中2断言失败、0错误）。恢复正确事务后同命令19项全绿。临时突变未留在提交中。
- 脚本成功和失败均进入 finally 清理；最终按 verification label 查询没有遗留容器。仅移除了本脚本新建的临时容器及其临时数据，开发栈/卷/截图未删除。

## 通用性 / 生命周期

字段名参数化，不依赖实际 FU。测试不等价新建/导入 FU 的运行双样本。未变更配置格式、生命周期消费者、数据库脚本或部署产物，Save/Load 以外的 FU 生命周期本 concern 为 N/A；完整生命周期发布门禁继续保留。

## 安全、性能与静态检查

回环随机端口、独立 batch_verification 数据库、随机内存口令；不读取平台 .env，不输出口令。删除前核验随机名称与专用标签。IT 无环境变量显式失败，默认单测不自动连接数据库；只通过上述 opt-in 脚本运行。没有新增依赖或性能改变；不以该测试宣称容量结论。

测试源码编译已通过；第二 reviewer 独立核验 Surefire 7+12项、PowerShell AST 0错误、staged diff check、274行新增内容敏感候选0及无遗留容器。没有可见 UI 或可部署代码改动，不需要重建产品服务或新增截图。

## 残余风险 / 范围外

这是生产实体映射和实际 PostgreSQL 的局部持久化集成测试，测试库 schema 由 Hibernate 建立，不是全部生产 init-script schema 验收。未证明 Flowable 跨服务事务、真实权限目录、服务端 MI 权威范围、完整分派链、Preview、New Request 摘要或 P4 生命周期。不能用本批通过替代它们。
