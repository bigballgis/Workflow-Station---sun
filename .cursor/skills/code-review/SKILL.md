---
name: code-review
description: >-
  Read-only, evidence-based code review for staged changes or a PR branch. Enforces repository
  rules and skills, minimal diffs, compilation and test gates, security/privacy, performance,
  Function Unit portability and runtime completeness, generic behavior across new and existing
  Function Units, and incremental secret checks. Use when the user asks for code review, review
  code, PR review, review diff, pre-commit review, 代码审查, 提交前检查, 审查改动, FU review,
  or sensitive-information review.
---

# Code Review — 证据驱动的提交与 PR 审查

## 目标

- 对当前变更做**只读、可复现、按风险排序**的审查。先验证业务正确性和安全性，再检查质量与风格。不得仅凭代码看起来合理就判定通过；编译、测试、运行时或 UI 证据缺失时必须明确写出。
- 本 skill 是现有规则与专项 skill 的编排层，不替代它们，也不复制第二套业务规则。

## 审查边界

- 默认不得修改被审查的产品代码、测试或配置；用户明确要求修复后，应另起修复步骤。
- 不把 review 与「顺手重构」混在一起。
- 仓库规则要求的问题台账更属于独立治理动作，不等于可以修改被审查实现。
- 只报告有具体证据的问题，不猜测不存在的调用方、字段、API 或运行结果。
- 引用类、方法、字段和依赖前，必须先确认其真实存在及签名。

---

## 1. 确定审查模式

### A. 提交前 — staged diff

至少检查：

1. `git status --short`
2. `git diff --cached --name-status`
3. `git diff --cached --stat`
4. `git diff --cached`
5. 未跟踪文件列表

只审查工作区未 staged 的内容会遗漏实际提交内容；只审查 staged 内容而不管未跟踪文件会遗漏应提交的关联文件。

### B. PR 阶段 — branch diff

1. 明确目标基线分支；不确定时，禁止默认为当前分支。
2. 使用 `merge-base` 确认审查完整 `base...HEAD`，不是只看最后一个 commit。
3. 检查 name-status、stat、完整 diff、commit 列表和 commit message。
4. 修复 finding 后，先整理，再重新审查 `base...HEAD`。

### 共同的审查输入

必须确认回答：

- 需求与验收正例/反例是什么？
- 模块与变更类型是什么？
- 允许范围与禁止范围是什么？
- 哪些验证已经实际执行？

关键信息缺失时标 `CONDITIONAL / 无法验证`，不得静默假设通过。

---

## 2. 生成变更触发矩阵

### 每次都执行

- 需求与验收映射
- 业务正确性
- 最小 diff
- 影响链完整性
- 编译/build
- 测试质量与证据
- 安全、隐私与增量敏感信息
- diff 与提交卫生

### 按路径和语义触发

| 触发 | 必须加载/对照 |
|------|---------------|
| 任意代码 | `.cursor/rules/code-quality-standards.mdc`、`change-playbook.mdc`、`cross-cutting.mdc`、`ai-guardrails.mdc` |
| 异常、空值、降级 | `.cursor/rules/error-handling-governance.mdc`；需定量对照 `fallback-audit` |
| Auth、Input、URL、SQL、XML、LDAP、Credentials | `.cursor/rules/security-guard.mdc` + `secure-coding-sast` |
| 性能、cache、并发、批量、前端热路径 | `.cursor/rules/performance-guardrails.mdc`；Portal 热路径另加载 `performance-change-safety.mdc` |
| 测试文件 | `.cursor/rules/testing.mdc` |
| 可部署代码/配置 | `.cursor/rules/debug-mode-docker-workflow.mdc` |
| 可见 UI | `verify-ui-fix-with-screenshot` + 相关 frontend rules |
| FU export/import/clone/snapshot | `function-unit-portability` |
| FU version/rollback | `function-unit-version-rollback` |
| View access | `view-access-control` |
| Portal MI | `portal-mi-subtable-my-request.mdc` + `performance-change-safety.mdc` |

只展开命中的项，避免大量无意义 N/A；任何 N/A 必须说明依据。

---

## 3. 通用审查门禁

### 3.1 需求与业务正确性

- 每个需求都有对应代码与验证；每个改动可追溯到需求。
- 检查正/反例、异常路径、权限不足、空值、边界、高并发。
- 检查状态迁移、事务边界、并发、幂等、资源释放、错误传播。
- 检查是否新增 fallback；`catch` 是否仅 log 而不把数据错误吞成默认空值。
- 检查业务判断是否出现「第二决策点」；需求变更是否需同步别处。
- 检查修复是否针对根因，而非掩盖症状。

### 3.2 最小修改

- 对每个文件问：「删掉这段，验收还能过吗？」
- **拒绝**：无关重构、格式化、重命名、依赖升级；同一 PR 混 fix/feat/perf/refactor；改生成镜像而不改 `.cursor` 源；为假想未来加抽象/开关/接口/依赖；注释掉的旧代码、debug 输出、临时脚本或构建产物。
- 范围外存量问题应单独登记，不在当前 diff 里顺手修。

### 3.3 影响链完整性

按改动逐项触发：

- 接口 → 实现 → 所有调用方
- Entity → 新增 SQL → Repository/Mapper → DTO → 前端类型
- Controller URL/Response → 前端 API → store/composable/view
- i18n key → en、zh-CN、zh-TW
- 配置/环境变量 → application、Docker、K8s、Kong、healthcheck、文档
- `platform-*` / shared → 所有下游模块
- 删除或重命名 → 搜索 config、测试、文档、序列化字段残留引用

### 3.4 编译与静态质量

- 所有受影响模块必须有**实际命令与成功输出**。
- 后端至少编译/打包对应 Maven 模块；公共接口变更需编译下游。
- 前端跑对应 `build`，含 TypeScript 类型检查。
- 执行项目既有 lint：`lint`、`Checkstyle`、`SpotBugs`、SAST 等。
- IDE 无红线；禁止「理论上能编译」而无实际命令。
- **Blocker**：编译/类型/依赖解析失败；新增 `@SuppressWarnings`、`eslint-disable`、`ts-ignore`；为过关降低 strict/lint/安全规则或测试覆盖。
- 未执行时写「未验证」，不能输出 `PASS`。

### 3.5 测试质量与证据

- 新增/修改行为覆盖正常路径和至少一个异常/边界/权限路径。
- Bug 修复应先有能复现失败的测试。
- 测试验证外部可观察行为，不复制实现、不 mock 被测对象内部逻辑。
- 禁止 `skip`/`only`、无断言、固定 sleep、依赖执行顺序、共享脏数据或放宽断言。
- 测试可重复执行，并正确清理数据库、timer、线程、mock 和外部资源。
- 核心不变量适合时增加属性测试，而不只覆盖单个样例。
- 可见 UI 必须 build、部署并保留 Playwright 截图。
- Portal MI 热路径必须执行完整 `npm run regression:mi`；unit-only 不算完整通过。

### 3.6 数据库与迁移

涉及 Entity/schema/索引/seed 时检查：

- `deploy/init-scripts` 只新增、不改旧 SQL，且脚本幂等。
- 约束、索引、执行顺序、重复执行、已有数据和失败恢复。
- 禁止破坏性删列/改列、双轨 schema 来源或为 Relation Table 创建业务物理表。
- 高风险迁移有备份、验证、前滚/回退方案。

### 3.7 分布式契约与兼容

API、Kafka event、缓存结构、共享 DTO、持久化/导入格式变化时检查：

- 新旧服务混跑、部署顺序和回滚旧版本。
- 字段只增不破坏，未知枚举值可控。
- 消息重现、乱序、重试和幂等。
- 新版本写入的数据是否仍可被回退版本读取；不能隐含「所有服务同时升级」。

### 3.8 可靠性与可观测性

- timeout、有限 retry、退避、熔断/降级依据。
- 连接、流、线程、timer、listener 和事务正确释放/回滚。
- 写操作防重复提交，跨服务权威数据失败与空结果可区分。
- 关键日志包含业务上下文 ID，日志级别与结果一致。
- 禁止无限重试、吞异常或记录成功但实际失败。

### 3.9 前端 UX、可访问性与 i18n

可见 UI 检查：

- loading、error、empty、success、重复提交和危险操作确认。
- 未保存数据保护、权限态、键盘/焦点、label/aria。
- 1366×768 不产生不合理横向滚动。
- 三语言同步，无硬编码用户文案。
- 验证真实交互，不只断言 DOM 存在。

### 3.10 值语义边界

- 金额用 decimal 语义和显式舍入；禁止浮点金额。
- 时间用显式时区或 ISO 8601，覆盖 DST 和跨日。
- null、空串、空数组语义区分且不混淆。
- 排序稳定、分页边界正确、未知枚举有兼容策略。
- 不依赖数据库/JVM/浏览器默认 locale/timezone/排序。

### 3.11 依赖与供应链

- 新依赖必须必要，无可复用内部能力。
- 版本在根 dependencyManagement 对齐，lockfile 同步。
- 检查 license、已知漏洞、维护状态、包体积和运行时影响。
- Actions、脚本、镜像使用不可变 commit SHA/digest；禁止从未知源下载。

### 3.12 Diff 与提交卫生

- 检查新增、修改、重命名、删除、二进制、未跟踪文件。
- 无 conflict marker、debug 代码、死代码、产物、临时文件、snapshot 噪声或意外大文件。
- 原子 commit，message 描述意图且不含敏感或内部值。
- 文档、示例、API、环境变量和运维 runbook 与代码同步。

---

## 4. Function Unit 强制完整性矩阵

### 4.1 触发条件

以下任一项新增或修改时必须执行本节；每项必须输出「已同步 + 证据」或「N/A」，禁止留空：

- FU 设计产物、属性、字段（含 snapshot schema、Form、Table、View、Action、Decision、Email、BPMN、Access 等）
- Deploy、Import、Clone、Rollback 或 Portal runtime 语义
- 在单个 FU 上报告或复现的功能问题

### 4.2 通用性门槛

实现必须由 FU 元数据、schema、binding、稳定 code/name 或运行时上下文驱动。

**禁止硬编码**：

- FU id/code/name
- form/table/field/binding/view id
- process key、示例名称、固定 seed 行或单一 package 结构
- 通过某个具体 FU 标识决定业务语义

**必须支持**：

- 修改后新建的 FU
- 升级前已存在的 FU
- Export/Import、Clone、Snapshot/Rollback 后的 FU
- 有新配置和没有新配置的旧 FU

若能力确实只适用于某类 FU，使用可配置、可验证的能力条件，并提供需求依据。「通用」仅指需求范围内行为一致，不允许为假想未来场景过度抽象。

验证至少包含两个结构和标识不同的 FU：

1. 一个新建 FU
2. 一个已有或导入 FU

只用用户报告的单一 FU 或固定 seed 数据通过，不得判定通过。

### 4.3 生命周期矩阵

| 阶段 | Review 必查 |
|------|-------------|
| DW Save/Load | 设计器配置、DTO、持久化、重新打开一致 |
| Export | `FunctionUnitExporter` ZIP、manifest/components、v2 snapshot 完整 |
| Import | `FunctionUnitImporter` 新导入/同名覆盖、顺序、稳定键与 ID remap、显式失败 |
| Clone | `FunctionUnitCloner` 深拷贝、引用重写、状态和访问规则 |
| Version | `VersionComponentImpl` / `FunctionUnitSnapshotRestorer` v2 + legacy、clear/flush/session 安全 |
| Admin JSON import | `/function-units/import` → `FunctionUnitPackageParser` 包含同一能力 |
| Admin deploy import | `/function-units-import/import` → `FunctionUnitImportController` 包含同一能力 |
| Portal backend | FU 内容、Form、View、Access 和数据语义同步 |
| Portal frontend | renderer、task detail、application detail 和 Designer parity 同步 |

不得只更新 Admin 两条导入链中的一条。

### 4.4 Round-trip 证据

至少按风险覆盖：

- Export → Import
- Clone
- Snapshot → Rollback
- DW Deploy → Admin Import → Activate → Portal runtime
- 负例：同名重复 import、缺字段、无法 remap、旧 snapshot/package 等

---

## 5. 安全、隐私与增量敏感信息

### 5.1 安全门禁

检查场景：

- 服务端鉴权、资源归属、BU/Role、水平/垂直越权
- `SYS_ADMIN`、普通用户、发起人、处理人、未授权用户的权限矩阵
- 输入校验、SQL 注入、SSRF、XXE、LDAP、XSS、路径穿越
- 缓存 key 含身份、BU、`viewContext` 等隔离上下文
- 日志与响应不暴露 PII、token、堆栈、内部实现

### 5.2 增量 secret 范围

- 只治理门禁启用后的新增内容（staged、PR diff、push commit 范围）。
- 历史 commit 或未修改旧值不扫描/不报，除非被复制或修改。
- 检查代码、配置、SQL、脚本、文档等中的：密码、API key、token、Cookie、JWT、私钥、证书、keystore 密码、DB/LDAP/外部系统凭据、加密密钥、client secret、PII（姓名、邮箱、电话、证件号）。
- 发现即标 **Blocker**；报告只写「类型 + 文件位置 + 处置」，**不得重复敏感值**。
- 真实凭据已 push 须立即吊销/轮换并从 commit 移除；`.gitignore` 不保护已跟踪文件。

---

## 6. 性能专项

### 后端

- N+1、循环内 DB/HTTP、逐行写入
- 拉全量后 count/aggregate、无分页或无界查询
- 可并行 I/O 串行化、阻塞 I/O 使用公共 ForkJoinPool
- 缓存 TTL/容量/穿透/失效/身份隔离和 key 完整性
- 大 payload、重复序列化、慢 SQL、缺索引
- timeout、retry storm、线程池/队列无界

### 前端

- 重复 API 和缺少 in-flight Promise 去重
- 可并行请求串行 `await`
- 远程搜索无至少 300ms debounce
- deep watch、大对象深克隆、模板循环内 filter/find/format
- 大列表无分页/虚拟滚动，重型库未动态导入
- timer/WebSocket/listener 未清理

### 性能变更证据

- 性能改动不得与 fix/feat 语义修改混 PR
- 提供同环境、同数据、同口径的优化前后结果；没有测量不得声称提升
- 不得以缓存、并行、少 clone 改变 FU、权限、MI merge/filter 或错误语义
- MI 热路径执行完整 `npm run regression:mi`

明显回退为 `Major`；可能导致不可用、资源耗尽、越权或数据错误为 `Blocker`。

---

## 7. 发布、回退与豁免

- 高风险改动检查 feature flag/default、灰度/canary、混合版本兼容、紧急关闭和可执行回退步骤。
- 低风险内部改动可有依据地标 N/A。
- 所有「N/A」、风险接受、规则例外或未执行验证必须记录：原因与外部依据、影响范围、责任人/Issue、后续动作和临时逻辑删除条件。
- 「时间不足」或「现有代码如此」不是豁免依据。

---

## 8. Finding 严重级别

### Blocker

编译/类型失败、敏感信息、可利用安全缺陷、越权、数据损坏/丢失、破坏性不兼容、核心业务错误、FU 生命周期缺失。**必须修复**。

### Major

高概率正确性/兼容性/通用性问题，关键测试或运行证据缺失、明显性能/可靠性回退。原则上修复；接受风险必须有责任人和依据。

### Minor

不影响正确性和安全的局部维护性问题。不得用个人风格偏好制造噪声。

### Question

需要作者澄清但尚无反例的问题。Question 不能伪装成 finding。

**每条 finding 必须包含**：

1. 文件与行号
2. 可复现反例或具体触发条件
3. 业务、安全或运行影响
4. 最小修复方向，而非整段重写
5. 对应 rule/skill
6. 缺失的验证证据

---

## 9. 放行规则

仅在以下条件**全部满足**时输出 `PASS`：

- `Blocker` 和未接受的 `Major` 为零
- 所有受影响模块实际编译/build 通过
- 必需测试、运行验证和 UI 截图通过
- 增量敏感信息扫描通过
- FU 变更的通用性和生命周期矩阵完整

环境、权限、需求或验证不可用时只能输出 `CONDITIONAL`，不能假装已验证。

---

## 10. 固定输出格式

```markdown
# Code Review 结果

## 结论
PASS | CONDITIONAL | FAIL

## Findings

### Blocker
- [文件:行] 标题
  - 反例:
  - 影响:
  - 最小修复:
  - 依据:
  - 验证缺口:

### Major
...

### Minor
...

### Question
...

## 范围 / 验收映射
...

## 编译与静态检查
- 命令: ... -> PASS / FAIL / 未执行

## 测试与运行证据
- 命令/截图/logs: ... -> PASS / FAIL / 未执行

## Function Unit 通用性与生命周期
- 通用性: PASS / N/A / 未验证
- Save/Load: ...
- Export/Import: ...
- Clone: ...
- Version/Rollback: ...
- Admin 两条 Import: ...
- Portal runtime/parity: ...

## 增量敏感信息
- 扫描范围: ...
- 结果: PASS / FAIL / 未执行

## 性能
- 结果与证据: PASS / N/A / 未验证

## 剩余风险 / 豁免
...

## 范围外问题
无 / ...
```

没有 finding 时也必须列出实际执行的命令、未验证内容和残余风险，**不能只回复「LGTM」**。

---

## 11. 推荐协作流程

1. 开发前明确验收、范围、变更类型；FU 改动先列生命周期矩阵。
2. 一个 concern 一组最小改动，不混 fix/feat/perf/refactor。
3. 只 stage 目标 hunks，运行 staged review，解决 Blocker/Major。
4. 编译所有受影响模块，运行相关测试和本地增量 secret scan。
5. 使用不含敏感信息的 Conventional Commit。
6. PR 前更新基线，对 `base...HEAD` 运行完整 review。
7. 作者提供实际编译、测试、Docker logs 和截图证据。
8. Reviewer 独立复核需求、完整 diff、高风险路径和至少一个关键命令。
9. `platform-common`、安全鉴权、schema、FU portability/rollback 或跨服务契约的需要领域 owner/第二 reviewer。
10. 修复后先审增量，再审完整分支；scope 变化先更新验收。
11. 满足放行规则后合并。
