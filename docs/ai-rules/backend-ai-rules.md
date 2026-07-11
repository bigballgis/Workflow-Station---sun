# Hermes — 后端代码治理与规则摘要

面向 Workflow Station（Maven 多模块、`platform-*` 共享 jar、四座业务服务、Controller→Component→Service→Repository）。本文可与 `RULES.md`、`.cursor/rules/`、`BUILD_GUIDE.md` 并列使用。

---

## 一、治理目标

- **可预测**：异常形态、`ApiResponse`、日志字段、配置来源一致。
- **可演进**：`platform-common` 等高爆炸半径变更可控；业务服务可独立发版。
- **可观测**：靠 `traceId` + 业务 `errorCode` 定位问题，不靠猜堆栈或未脱敏明细。
- **可审计**：权限、密钥、动态 SQL、跨服务调用可复查。

---

## 二、架构与边界

| 事项 | 要求 |
|------|------|
| 模块依赖 | `platform-common` 变更影响下游全部服务；新增/修改共享 API 须评估消费者清单。业务服务之间禁止 JAR 级依赖（仅 REST / Kafka 等契约）。 |
| 分层 | Controller 不直连 Repository；跨聚合编排放在 Component；与现有 playbook 一致。 |
| 共享 DTO | 仅稳定性高的契约放 `platform-common`；易变字段优先考虑各服务自有 DTO + client 映射。 |
| 防回潮（可选落地） | 使用 ArchUnit 或等价约定测试：禁止 Controller 引用 `repository` 包等。 |

---

## 三、异常与 API 契约

- 业务失败使用**带 `errorCode` 的业务异常**，由 `@ControllerAdvice` 统一映射 HTTP 状态与 `ApiResponse`。
- **限制**生产路径中裸 `RuntimeException`；仅限「不应发生的缺陷」场景，且须记录日志。
- 对用户响应**不要拼接**底层 `Throwable.getMessage()`；对外给固定码 +（可选）i18n key；详细原因进日志。
- 与前台对齐：`errorCode` 建议维护注册表（枚举或集中文档），避免同一语义多套 code。
- admin-center `/ developer-workstation` / user-portal 的认证链路上，优先与已存在的 `AdminBusinessException` + `i18nService.getMessage(...)` 模式对齐，不要用散落英文文案 + `RuntimeException` 长期坚持。

---

## 四、配置与安全

| 红线 | 做法 |
|------|------|
| JWT / 加密密钥 | 仅从环境变量或受控密钥管理注入；**禁止**在主代码或可上线 profile 中带弱默认密钥。 |
| Redis / DB 默认口令 | `platform-common` 等默认 Java 字段值不得作为生产真值；未配置时应校验失败或通过 profile 限定为本地示例。 |
| Spring `@Value` 默认值 | localhost URL、超时等在 dev 可接受；生产必须由环境覆盖，并在文档中列出必填变量。 |

### JWT 与安全组件特例

- 避免无参构造 / 测试代码路径向生产泄露「占位密钥」；未配置密钥时建议在非 test profile **启动失败**。
- `workflow-engine`、`developer-workstation`、`user-portal` 等对同一 JWT 的信任边界要在文档中单点说明。

---

## 五、数据访问与 SQL

- **参数化**：JPA `@Query`、JdbcTemplate **禁止**将用户输入拼进 SQL 文本。
- **动态表名 / 列名**：仅允许白名单或通过 DB 元数据解析后再校验（如正则 `^[a-zA-Z_][a-zA-Z0-9_]*$`）；类上注释说明数据来源与安全假设，便于审计。
- **JPQL / 原生 SQL 中的状态字面量**（如 `PENDING`、`ACTIVE`、`assignmentType = 'USER'`）：与 Java 枚举或数据字典变更保持同步策略（常量集中、参数绑定、或配套单测）。
- `nativeQuery = true` 的变更须 PR 中高亮，便于 reviewer 核对表名、字段与租户/软删条件。

---

## 六、国际化与可读错误

- 用户可见后端文案：优先 `i18nService.getMessage("key"...)`（或等价 `MessageSource`），与 `messages_*.properties` 同步。
- 错误响应结构建议：**`code`（机器可读） + `message`（人类可读或占位）**，前端可按 `frontend/*/src/utils/errorTranslator.ts` 等模式二次映射。
- audit / 内部事件字符串（如 `"ROLE_ASSIGNED"`）可作为内部常量枚举化，避免与业务 errorCode 混淆。

---

## 七、权限与角色

- 角色码、permission 字符串建议**单源定义**（常量类或与 DB `role.code` 一致），`@PreAuthorize` 与实际角色命名一致。
- 若 Spring Security 为 `permitAll` + 自定义 JWT 过滤器兜底，须在代码注释与安全文档中写明：谁在何时做授权，避免误判「网关即安全」。

---

## 八、消息与定时任务（如适用）

- Kafka topic、`groupId`：默认可用常量类；多环境隔离时改为可配置 (`spring.kafka.consumer.group-id` 等)。
- 消费者失败策略：区分可重试、死信、与业务补偿；日志须带 partition/offset/key（避免 value 含敏感信息全文落日志）。

---

## 九、可观测性与日志

- **结构化**：`traceId`、可选 `userId`/主体标识（脱敏）、`errorCode`、耗时等字段统一。
- **禁止**：密码、refresh token、API key、完整身份证等 PII、未过滤的外部响应体写入日志。
- 健康检查 URL 与 Kong / K8s 探针保持一致；infra 变更时更新 `BUILD_GUIDE.md`。

---

## 十、测试与 CI 门禁

| 层级 | 建议 |
|------|------|
| 单元 / 切片 | Service、Component；Repository 选型统一（如 `@DataJpaTest` 或 Testcontainers 二选一成标准）。 |
| 属性测试 | jqwik 用于规则密集型逻辑（校验、映射、边界）。 |
| CI | PR 至少跑受影响模块 + `platform-common`（若以根 POM 聚合则明确 profile）。 |
| 静态分析 | SpotBugs / Error Prone / Checkstyle 等任选并固定版本与规则集；禁止随意整包 `@SuppressWarnings` 且无说明。 |

---

## 十一、依赖与 CODEOWNERS

- 新三方依赖须有理由与安全/许可粗评；避免同一关注点多种客户端库并存。
- `platform-common`、`platform-security` 建议 CODEOWNERS 或最少 reviewer 人数，拉高合并门槛。

---

## 十二、技术债登记

范围外但必须记录的问题写入 `.kiro/issues/index.yaml`（见项目 issue-radar 规则）；本文件不代替该索引。

---

## 十三、建议落地顺序（治理视角）

1. **配置与安全红线**（密钥、默认口令、JWT 占位）
2. **异常与认证链路的契约统一**（`errorCode` + Advice + i18n）
3. **CI / ArchUnit（或等价）**防止分层与高危模式回潮
4. **JPQL/SQL 字面量与动态 SQL**的评审清单 + 常量收敛
5. **角色常量集中**与权限文档对齐

---

*文档版本：与仓库同步维护；大规模流程变更时请更新本节与 `BUILD_GUIDE.md`。*
