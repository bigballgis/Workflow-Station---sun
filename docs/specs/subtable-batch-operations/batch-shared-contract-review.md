# Code Review 结果：批量配置共享结构契约

## 结论

CONDITIONAL。窄范围代码审查未发现未处置 Blocker/Major；不放行 P3/P4 或生产配置。部署后的完整 MI 浏览器回归仍失败，保留失败结果并继续排查。

## Findings 与处置

- 已修：Portal 旧版本判据使用 `Number.intValue()`，`schemaVersion: 1.5` 被当成 1；DW 发布与 Admin 导入也没有同一格式判据。新增纯结构契约，共用于 DW 发布、Admin ZIP/FORM 写入前校验、Portal 新实例捕获，拒绝未知、小数及字符串版本。
- 已修：JSON `null` FORM 内容在写前校验会产生空指针；显式转换为包校验错误，补测试。
- 重审：第二 reviewer 对完整相关工作区只读检查，无新增 finding；独立 JShell 验证旧配置、非 seed binding 正例与小数版本反例。未把整体尚未完成的门禁视为已经解决。

## 范围 / 验收映射与触发矩阵

A13/A24/A40 的结构门禁：四开关严格布尔、启用控件标识、binding、独立批量定义、Assign 控件存在性。关闭定义可保留空 rule，未启用旧包不强行补新字段。命中 code-review、function-unit-portability、secure-coding-sast、共享依赖、错误治理、MI 回归规则。

## 编译与静态检查

- `mvn -pl backend/developer-workstation,backend/admin-center,backend/user-portal -am clean test "-Dtest=SubTableBatchDefinitionTest,FunctionUnitBatchValidationTest,ImportBatchDefinitionValidatorTest,FunctionUnitPackageParserFailClosedTest,FunctionUnitPackageParserEmailTemplateTest,FunctionUnitPackageParserRelationTableTest,SubTableBatchRuntimeSnapshotTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：38 测试通过。
- `mvn -pl backend/developer-workstation,backend/admin-center,backend/user-portal,backend/workflow-engine-core -am package -DskipTests`：全部受影响服务打包通过。
- null FORM 补丁后 Admin helper 两项测试及 package 通过（未把重复执行计入独立测试数）。
- `node --check frontend/scripts/verify-batch-package-validation.mjs`、diff check 通过。无前端 UI 变更，本 concern 不重跑前端 build；已有全量类型失败仍保留。

## 测试与运行证据

- RED：移除 Admin parser 新校验后，以 clean test 运行小数版本反例，真实断言失败为“Expecting code to raise a throwable”；恢复后 GREEN。早期增量 target 的 VerifyError 不计作产品反例。
- 2026-10-15 四后端服务均重建，新 Admin null 补丁再单独重建。四服务健康，启动日志无 VerifyError/ClassFormatError/NoClassDefFoundError/启动失败匹配；没有改数据卷。
- `node frontend/scripts/verify-batch-package-validation.mjs`：两个真实 HTTP 接口都以 400 和 batch 校验错误拒绝同一坏 ZIP；前后版本查询均为空，无残留 catalog 版本。只使用自动生成的 synthetic code，不修改已有 FU。
- 完整 `pnpm -C frontend run regression:mi`：28 文件 226 单测通过，浏览器阶段 FAIL。第一次错误 ORIGIN 导致样本身份检查失败；修正为 3000 后 My Requests Details 场景失败：已保存 UUID 行仍显示为空，未放宽断言。任务与流程详情 API 确有 synthetic People 行，需继续定位只读展示链。

## Function Unit 通用性与生命周期

通用性静态 PASS，无 FU/业务列名硬编码。DW 发布检查在状态/版本写入之前；Admin 两 Controller 均进入同一 import 编排，FORM 校验在 catalog 查询/写入前；ZIP parser 也拒绝坏格式。legacy JSON 当前内容主要为 PROCESS，不能将 helper 的 FORM 容器单测宣称成该入口已有完整 FORM 导入能力。Export/Clone/Rollback 格式不变，完整 round-trip 与 Preview parity 仍未完成。

## 增量敏感信息 / 性能

新增/修改内容及脚本未添加凭证、token 或真实业务数据；登录沿用既有助手。第二 reviewer 扫描 325 行候选 0；最终提交仍执行 staged 扫描。内存树遍历无新增 DB/HTTP 循环，未执行负载压测。

## 残余风险 / 范围外问题

共享类只处理结构，不替代字段元数据、完整可写范围、BPMN/阶段/目标资格。发布前完整语义校验、MI 权威上下文、Preview、同工作区 Assign、启动失败隔离及全部生命周期验收仍是后续门禁。独立第二 reviewer 未重跑 Maven，使用真实 Surefire 报告和独立结构调用核验。当前 MI 浏览器失败不归因于此结构补丁，尚未闭合，不宣称不回归。
