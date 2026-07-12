# SAST 安全告警治理总结（Checkmarx / Cyberflows）

> 生成日期：2026-07-06 · 关联 Issue：#1476 / #1477 / #1478 · 报告来源：`8.cyberflow_sast漏洞扫描结果/`（仓库外）
>
> 本文档记录两轮 Checkmarx 扫描的治理进展、根因结论、遗留项与下一步建议。新代码的**编码规范**见
> `.claude/skills/secure-coding-sast/SKILL.md`（处理相关文件时自动加载）。

---

## 1. 第二轮扫描类别与数量（986 条 finding）

| 漏洞类型 | 数量 | 治理判定 |
|---|---:|---|
| Parameter Tampering | 456 | 以假阳性为主（已有鉴权，扫描器不识别）→ triage |
| SSRF | 245 | **已用内联 sanitizer 全量修复可修 sink** |
| Unsafe Object Binding | 151 | 以假阳性为主（@Valid 专用 DTO）→ triage |
| Second Order SQL Injection | 60 | 多为已防护代码；真实缺口已修 → triage 余量 |
| Privacy Violation | 20 | 日志 PII 脱敏（首轮已处理主路径） |
| SQL Injection | 14 | 已防护 + 修复 1 处真实未净化拼接 |
| Heap Inspection | 9 | 低危，char[] 建议（见 §4） |
| Frameable Login Page | 7 | X-Frame-Options / CSP（首轮已加，待复扫确认） |
| JWT Sensitive Information Exposure | 6 | HttpOnly cookie（已符合），triage |
| Client HTML5 Store … Web Storage | 4 | 前端不落敏感数据到 storage（见 §4） |
| Excessive Data Exposure | 4 | DTO 裁剪（见 §4） |
| Missing HSTS Header | 2 | 首轮已加 HSTS，待复扫确认 |
| Unchecked Input For Loop Condition | 2 | 循环上界校验（见 §4） |
| LDAP Injection | 2 | 已 `escapeFilterValue`（RFC4515）→ triage |
| Cleartext Submission of Sensitive Information | 1 | **已加 LDAP 明文 fail-fast 守护** |
| Improper Restriction of XXE Ref | 1 | **已内联 XXE 加固** |
| ReDoS From Regex Injection | 1 | 已 `Pattern.quote` → triage |
| Use of a One Way Hash without a Salt | 1 | 确定性摘要用途，保留（见 §3） |

第一轮 → 第二轮变化：`SSRF 228→245(+17)`、`Parameter Tampering 452→456(+4)`、`SQL Injection 18→14(-4)`。

---

## 2. 关键根因结论（务必知会安全团队）

> **Checkmarx 的 taint 分析基本是过程内（intraprocedural），且不识别项目自定义的 sanitizer，
> 也不追踪"校验后原样返回"的方法与既有授权逻辑。** 这直接导致：
>
> 1. 首轮把 XXE 加固放进 helper 方法并吞异常 → 扫描器不认作 sanitizer，计数不降（XXE 汇点行号
>    42→44 恰好与编辑后一致，证明扫的确是本仓库改动却仍报警）。
> 2. 大量 SQLi / 二阶 / LDAP / ReDoS 告警所在代码**本就已正确防护**（见下），但因防护是自定义
>    校验器/转义器，扫描器不认，计数不降。
> 3. Parameter Tampering / Unsafe Object Binding 把每个 `@PathVariable` / `@RequestBody` 都视作
>    污点，不追 `@CurrentUserId`、ownership 403、`@Valid` DTO 等既有防线。

**已存在但未被识别的防护样例：**

- `SubTableAssignmentHandler` / `MultiInstanceDataResolver`：已内联 `requireSafeIdentifier`
- `PortalRelationTableServiceImpl`：已 allowlist + `sanitizeIdentifier`
- `LdapClient`：全部 `escapeFilterValue`（RFC 4515）
- `ProcessDebugProbeRunner`：已 `Pattern.quote`

**因此**：根治这几类的正确做法是在 **Checkmarx preset 里把这些校验器登记为 sanitizer**，或对确认安全的
路径做 **not-exploitable triage**，而非继续往代码里堆冗余校验（既是 churn，又可能改坏合法访问模型）。

---

## 3. 本轮已完成的修复

### 3.1 SSRF — 内联 sanitizer 全量覆盖（约 50 处 / 18 文件）

新增 `com.platform.common.util.SafeUrlInput`：
- `requirePathToken(String)`：allowlist `[A-Za-z0-9._:@-]{1,256}`，非法即抛（用于 URL **路径段** ID/code）
- `encodeQueryValue(String)`：`URLEncoder` 百分号编码（用于 **query 值** 如 keyword/reason/status）

**必须内联在拼接点、且使用其返回值**，以打断 taint。覆盖文件：

| 模块 | 文件 |
|---|---|
| user-portal | `RoleAccessComponent`、`FunctionUnitAccessComponent`、`VirtualGroupAccessComponent`、`WorkflowEngineTaskClient`、`WorkflowEngineTaskHistoryClient`、`WorkflowEngineProcessClient`、`WorkflowEngineClient`、`AdminCenterClient`、`ProcessComponent`、`ProcessDraftComponent`、`ProcessStartAssigneeResolver`、`UserPermissionController`、`TaskController` |
| workflow-engine-core | `AdminCenterClient`、`ProcessCompletionListener` |
| developer-workstation | `DeploymentComponentImpl`、`UserDisplayNameService`、`DeveloperPermissionChecker` |
| admin-center | `DepartmentRoleTaskServiceImpl` |

> 注意：`long`/`int` 类型的 ID（如 `rowId`）本就无法注入 URL 分隔符，**不要**套 `requirePathToken`（编译报错）。

### 3.2 XXE — 内联加固（`Improper Restriction of XXE Ref`）

`BpmnDeployEnhancer.enhance`：把 `factory.setFeature("disallow-doctype-decl"...)` 等**内联到 `parse()` 前**，
不再走 helper、不吞 `ParserConfigurationException`（try 内直接抛，沿用原 catch 兜底）→ 扫描器可识别。

### 3.3 SQL Injection — 新增 sanitizer + 修复 1 处真实缺口

新增 `com.platform.common.jdbc.SqlIdentifiers`：
- `requireIdentifier(String)`：裸标识符 `[A-Za-z_][A-Za-z0-9_]{0,127}`（表名/列名）
- `requireQualifiedName(String)`：schema 限定名 `[A-Za-z0-9_."]{1,258}`

**真实缺口（已修）**：`SubTableDataInjector.querySubTableData` 的 `subTableName/assigneeField/foreignKeyField`
原为裸 `String.format` 拼接，现内联校验。其余 `SubTableRowAssignmentComponent`、`SubTableEnrichmentComponent`、
`RelationTableDataServiceImpl` 在 sink 处内联校验（多数上游已防护，此处为让扫描器识别）。

### 3.4 LDAP 明文传输（`Cleartext Submission of Sensitive Information`）

- 结论：`LdapClient` simple bind 会把服务账号+用户口令**原文**发给服务器；生产/UAT/SIT/K8s 均为
  `ldaps://…:3269 + LDAP_TLS=true`（无隐患），仅 dev compose 本地 mock 用 `ldap://…:389 + TLS=false`。
- 加固：新增 `ldap.allow-insecure`（`LDAP_ALLOW_INSECURE`，默认 false）。`LdapClient.@PostConstruct`
  检测「`ldap://` 且 `tls=false`」而未显式允许时 **fail-fast 拒绝启动**；dev compose 显式 true，
  K8s uat/preprod ConfigMap 显式 false（已按 CONFIG_SYNC 同步）。

### 3.5 硬编码后门 → 配置注入（#1477，兼顾本地测试需求）

移除 `SecurityManagerComponent.validateCredentials` 里硬编码的 `admin/admin123`、`user/user123`。
本地测试账号改为**配置注入**：`workflow.security.test-users.enabled`（`WORKFLOW_TEST_USERS_ENABLED`，默认
false / fail-closed）+ `.accounts`（`WORKFLOW_TEST_USERS`，格式 `admin:admin123,user:user123`）。
口令比较用 `MessageDigest.isEqual` 常量时间比较、命中打 WARN。dev compose 默认开启注入这两个账号；
K8s 显式关闭。代码中无任何明文口令 → 硬编码凭证告警不复发，本地登录体验不变。

---

## 4. 遗留项与建议下一步

### 4.1 建议优先在扫描器侧处理（不改代码）

| 动作 | 目标类别 | 说明 |
|---|---|---|
| 登记自定义 sanitizer 到 Checkmarx preset | SQLi / 二阶 / SSRF / LDAP / ReDoS | 登记 `SafeUrlInput`、`SqlIdentifiers`、`escapeFilterValue`、`sanitizeIdentifier`、`Pattern.quote` 用法 |
| not-exploitable triage | Parameter Tampering(456) / Unsafe Object Binding(151) | 已有 `@CurrentUserId` + ownership 403 + `@Valid` DTO 的端点逐条标记 |
| 复扫确认 | Missing HSTS / Frameable Login Page | 首轮已加 HSTS + X-Frame-Options/CSP，确认是否已消除 |

### 4.2 可用代码继续收敛的低量真实项（各 ≤ 20，建议单独小 PR）

- **Privacy Violation(20)**：核对剩余日志/响应中的 PII（email/phone/token），补脱敏。
- **Excessive Data Exposure(4)**：Controller 若返回裸 Entity/Map，改 DTO 裁剪。
- **Client HTML5 Web Storage(4)**：前端确认 token 走 HttpOnly cookie，不写 localStorage/sessionStorage。
- **Unchecked Input For Loop Condition(2)**：外部输入作循环上界处加范围校验。
- **Heap Inspection(9)**：口令等敏感值考虑 `char[]` 并用后清零（低优先，收益有限）。
- **Second Order SQLi 余量**：确认动态表/元数据链路 sink 都过 `SqlIdentifiers`；剩余归 triage。

### 4.3 明确**不建议**的动作

- ❌ 对四百多个已鉴权端点做全量授权重构（高风险、易改坏合法跨用户/只读访问，且计数未必降）。
- ❌ 继续往已防护代码堆冗余校验只为"哄"扫描器（churn 大、维护差）。
- ❌ 恢复任何硬编码凭证/后门（用配置注入替代）。

---

## 5. 验证记录

- `mvn clean install -pl backend/{user-portal,admin-center,workflow-engine-core,developer-workstation} -am -DskipTests` → BUILD SUCCESS
- `SecurityManagerComponentTest` 通过（凭证 mock 预置存储哈希，替代原硬编码后门）
- 四个后端 Docker 服务重建后 `docker compose ps` 全部 healthy
  - 期间遇到 user-portal `NoClassDefFoundError: CircuitBreakerRegistry`：根因是本地 Maven 仓库旧
    `platform-*` jar 与新代码混包（非本次改动），`clean install -am` 重装依赖后恢复

---

## 6. 参考

- 编码规范 skill：`.claude/skills/secure-coding-sast/SKILL.md`
- 工具类：`platform-common` 的 `util/SafeUrlInput.java`、`jdbc/SqlIdentifiers.java`
- Issue：`.kiro/issues/index.yaml` #1476（首轮）/ #1477（后门）/ #1478（第二轮 + 根因结论）
- 既有安全规则：`.cursor/rules/security-guard.mdc`
