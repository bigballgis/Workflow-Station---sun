---
name: secure-coding-sast
description: >-
  Coding rules distilled from resolved Checkmarx/Cyberflows SAST findings so new backend/frontend
  code triggers as few alerts as possible. Covers SSRF (SafeUrlInput), SQL injection (SqlIdentifiers),
  XXE (inline hardening), LDAP cleartext/injection, hardcoded credentials, Parameter Tampering /
  Unsafe Object Binding, PII/logging, security headers. Use when writing or reviewing code that builds
  internal service URLs, concatenates SQL identifiers, parses XML, does LDAP, handles auth/credentials,
  reads @PathVariable/@RequestBody, logs user data, or when the user mentions SAST / Checkmarx / 安全告警 /
  漏洞扫描 / SSRF / 注入.
---

# 安全编码规范 — 让新代码少触发 SAST 告警

来源：Checkmarx 两轮扫描治理（Issue #1476/#1477/#1478）。总结见 `docs/SAST_REMEDIATION_SUMMARY.md`。
本 skill 与 `.cursor/rules/security-guard.mdc` 配套——security-guard 讲通用红线，本 skill 讲**如何写才不被
Checkmarx 报警**（扫描器是过程内分析、不认自定义 sanitizer）。

## 核心认知（决定怎么写）

> Checkmarx taint 分析基本 **intraprocedural**，且**不识别**项目自定义 sanitizer、不追"校验后原样返回"的
> helper、不追既有授权逻辑。所以：**净化必须内联在 sink 拼接点、并使用其返回值**；放进 helper 方法、
> 吞异常、或分散在上游都会让扫描器继续报警。

## 规则 1 — 内部服务 URL 拼接（SSRF）

任何 `fixedBaseUrl + "/path/" + 变量` 或 `?param=" + 变量` 都必须内联净化：

```java
import com.platform.common.util.SafeUrlInput;

// ✅ 路径段 ID/code（UUID、数字、Test-000060、HMDC_ 码、dotted username 等）
String url = adminCenterUrl + "/api/v1/admin/users/" + SafeUrlInput.requirePathToken(userId) + "/roles";
// ✅ query 自由文本（keyword / reason / status）
String url = engineUrl + "/api/v1/tasks?keyword=" + SafeUrlInput.encodeQueryValue(keyword);
```

- `requirePathToken`：allowlist `[A-Za-z0-9._:@-]{1,256}`，非法即抛 `IllegalArgumentException`。
- 非法 ID 落入调用方**原有 try/catch** → 与原「调用失败」降级一致，合法值不受影响。
- ❌ 不要对 `long`/`int` 参数（如 `rowId`）套 `requirePathToken`（类型不符、编译错，且基元无法注入）。
- ❌ 不要把校验放到单独方法再返回后拼接——要**就地**调用并用返回值。

## 规则 2 — 动态 SQL 标识符（SQL / 二阶注入）

SQL **值**永远用绑定参数 `?`；只有**无法参数化的表名/列名**才用白名单校验，且内联在拼接点：

```java
import com.platform.common.jdbc.SqlIdentifiers;

// ✅ 裸表名/列名
String col = SqlIdentifiers.requireIdentifier(fieldName);
// ✅ schema 限定 / quote_ident 名
String tbl = SqlIdentifiers.requireQualifiedName(tableName);
String sql = "SELECT " + col + " FROM " + tbl + " WHERE id = ?"; // 值仍走 ?
```

- 即使上游已校验，也要在 **sink 处再内联一次**（否则扫描器不认，二阶注入尤甚——它会追 catalog/metadata 回读值）。
- ❌ 禁止 `String.format`/`+` 直接拼未校验的表名/列名（真实缺口，如曾经的 `SubTableDataInjector`）。
- ❌ 禁止按 `table_name` 动态 `CREATE/ALTER/DROP TABLE`（见 `json-row-storage-no-physical-tables.mdc`）。

## 规则 3 — XML 解析（XXE）

`DocumentBuilderFactory` 等的加固特性**内联在 `parse()` 之前**，不要包进 helper、不要吞异常：

```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
factory.setXIncludeAware(false);
factory.setExpandEntityReferences(false);
// ...紧接着 parse()；ParserConfigurationException 直接抛，不要 catch 后静默
```

## 规则 4 — LDAP

- **注入**：所有过滤值经 RFC 4515 转义（本仓库 `LdapClient.escapeFilterValue`：`\ * ( ) NUL /`）。
  绝不把 username 原样拼进 `(uid=...)`。
- **明文传输**：simple bind 会明文发送口令 → 生产必须 `ldaps://` 或 `tls=true`。本仓库用
  `ldap.allow-insecure`（默认 false）在 `@PostConstruct` fail-fast 拒绝明文启动；仅本地 mock 显式开。
  新增 LDAP 相关配置时 **不要**默认放行明文。

## 规则 5 — 认证与凭证

- ❌ **绝不**硬编码任何账号/口令/密钥/token（后门）。测试账号走**配置注入 + 默认关闭**：
  `workflow.security.test-users.*`（`WORKFLOW_TEST_USERS_ENABLED`/`WORKFLOW_TEST_USERS`），dev 开、生产关。
- 口令比较用常量时间 `java.security.MessageDigest.isEqual`（防时序攻击）。
- 生产登录用 `platform-security` 的 `BCryptPasswordEncoder`；确定性摘要用途（JWT 签名 key、脱敏摘要）
  的 SHA-256 保留即可，不要盲目换 BCrypt（会破坏签名/掩码）。
- token 走 **HttpOnly cookie**，前端**禁止**写入 localStorage/sessionStorage。

## 规则 6 — Parameter Tampering / Unsafe Object Binding（多为假阳性，但新代码仍按此写）

Checkmarx 会把每个 `@PathVariable`/`@RequestBody` 当污点。写新 controller 时遵循既有正确模式即可，
**不必**为哄扫描器额外加 guard：

- 身份/租户/BU/Role/当前用户一律用 **`@CurrentUserId`** 从认证态派生，**不信任**请求参数里的 userId。
- 按 ID 操作资源时在 **Service 层**做 ownership/归属校验（越权返回 403），不只靠前端。
- 请求体用 **`@Valid` + 专用 Request DTO**（不要直接绑 Entity/Map）；path 里的 id 由服务端回填
  （`request.setTaskId(pathId)`）。
- 这些既有防线扫描器不追 → 已鉴权端点应走 **not-exploitable triage / preset**，而非重构。

## 规则 7 — 日志与数据暴露（Privacy Violation / Excessive Data Exposure）

- 日志**禁止**明文 email/phone/身份证/token/口令/PII；只记脱敏值或业务 ID。
- Controller **不返回裸 Entity/Map/List**，用裁剪过的 Response DTO（也符合 `code-quality-standards`）。
- 统一 `ApiResponse<T>`，错误经 `GlobalExceptionHandler`，**不**把堆栈/内部细节返回给用户。

## 规则 8 — 前端

- 用户内容渲染富文本必须 `DOMPurify.sanitize`；不用 `v-html` 直渲外部/AI 生成内容。
- 正则用到外部输入的字面量部分用等价 `Pattern.quote`/转义，避免 ReDoS。
- 敏感数据不落 web storage（见规则 5）。

## 提交前自检清单

- [ ] 新的内部 URL 拼接：path 段 `requirePathToken`、query 值 `encodeQueryValue`，**内联**且用返回值？（基元 ID 除外）
- [ ] 新的动态 SQL：值用 `?`；表名/列名在 sink 处 `SqlIdentifiers.require*`？无 `CREATE/ALTER/DROP TABLE`？
- [ ] XML 解析：加固特性内联在 `parse()` 前、未吞 `ParserConfigurationException`？
- [ ] LDAP：过滤值转义？未默认放行明文传输？
- [ ] 无任何硬编码凭证/密钥？测试账号走配置且默认关闭？口令常量时间比较？
- [ ] 身份用 `@CurrentUserId`；按 ID 操作有 Service 层 ownership 校验；请求体 `@Valid` 专用 DTO？
- [ ] 日志无 PII/token；Controller 返回 DTO 而非裸 Entity；统一 `ApiResponse`？
- [ ] 若命中"已防护但仍报警"的类别 → 记录建议 triage/preset，不堆冗余校验。

## 参考

- 工具类：`platform-common` `util/SafeUrlInput.java`、`jdbc/SqlIdentifiers.java`
- 治理总结：`docs/SAST_REMEDIATION_SUMMARY.md`
- 通用红线：`.cursor/rules/security-guard.mdc`；JSON 行存储：`.cursor/rules/json-row-storage-no-physical-tables.mdc`
