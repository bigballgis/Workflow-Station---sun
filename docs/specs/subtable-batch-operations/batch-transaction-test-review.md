# Code Review：批量保存的真实 JDBC 事务测试

## 结论

PASS，仅限新增测试本身。PostgreSQL/JPA 集成验收未完成，不能由本测试宣布 P3/P4 放行。

## Findings 与处置

### Blocker / Major / Minor / Question

重审未发现本批剩余 finding。测试明确区分“写前拒绝”与“已执行 SQL 后回滚”，不把 mocked repository.save 调用计数当作数据库状态证据。

## 范围 / 验收映射

两套不同配置 PK，各运行三类场景：第二行版本冲突时全批写前拒绝；实际 SQL 更新之后注入异常时两行、版本、能力及删除身份全部回滚；合法删除两行和内部元数据一起提交。

## 触发矩阵

code-review、bugfix-verification、secure-coding-sast；测试、事务边界、批量与配置主键。只有 test 源码，未改产品/API/Entity/配置或部署产物。

## 编译与静态检查

user-portal 与依赖 Maven package 成功。事务、删除身份、MI SubmitIntegration、BindingScopeGuard 四类测试通过；已有 compiler/JaCoCo warning 保留。diff check 与新增文件敏感信息扫描通过。

## 测试与运行证据

`SubTableBatchRealTransactionTest` 共 6 个测试通过。使用真实 TaskFormComponent.submitTaskForm、批量声明/成员/范围/版本校验与其 TransactionTemplate，持久化接入独立 H2 内存库的 JdbcTemplate + DataSourceTransactionManager。

把该 manager 临时替换为旧 noop 测试 manager：2 个写后失败回滚用例均失败，查询看到删空及删除身份已提交；恢复真实 manager：6 个测试均通过。不是恒绿测试，也不是修改产品事务逻辑制造结果。

Repository 是一个连接真实 SQL 的 Mockito adapter；其他元数据、引擎 I/O 和无关 Request ID enrichers 被 stub。没有使用 JPA EntityManager，也没有把 H2 CLOB 当作 PostgreSQL JSONB 等价证明。真实 PostgreSQL/JPA 写后异常/提交失败/乐观锁并发仍是 P4 门禁。

## Function Unit 通用性与生命周期

两个配置主键名称均走同一生产边界。没有新增配置格式，Export/Import/Clone/Rollback/Admin 两路径本批 N/A。测试不连接现有 Docker 数据库，不写任何 FU 或业务实例。

## 增量敏感信息

新增源码和本文无凭证、私钥、真实邮箱或业务数据；全部 synthetic 值。SQL 标识符固定，值绑定参数；URL 是随机命名的 jdbc:h2:mem 内存库，不能从环境误连生产。

## 性能 / 残余风险 / 范围外问题

测试每个场景独立数据库，无每行网络调用或顺序依赖。六个内存库随测试 JVM 结束释放。产品代码未修改，无需仅为测试变化部署容器。跨服务原子性、真实 JPA 回滚和全部字段/任务授权仍未验证，不扩张本批 PASS。
