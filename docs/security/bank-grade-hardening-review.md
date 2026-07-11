# 外资银行级要求 — 审查结论与待决策项

> **状态**：仅作记录，**未纳入实施计划**。标准高于当前产品默认基线，由业务/合规/安全最终决策是否采纳及优先级。  
> **日期**：2026-04-04  
> **范围**：基于仓库静态代码与部署配置的审查摘要，非渗透测试、非完整监管对标。  
> **边界**：本文与 **Demo 演示约定**（英文界面、种子数据口径）**无关**。Demo 要求见 **`docs/demo-data-requirements.md`** 及 **`BUILD_GUIDE.md` §2.5**；银行级硬本文档不替代、也不修改上述演示策略。

---

## 1. 高优先级（建议最终决策时优先讨论）

| # | 主题 | 摘要 | 代码/配置线索 |
|---|------|------|----------------|
| H1 | 平台 `@Audited` 与内存审计 | `com.platform.common.audit.@Audited` 默认走 `DefaultAuditService`（内存），重启即失；与 DB 审计（如 `AdminAuditAspect`）可能双轨并存，易产生「以为已全量留痕」的误解。 | `PlatformCommonConfiguration`、`DefaultAuditService`、`UserManagerComponent` 等上的 `@Audited` |
| H2 | 网关不校验 JWT | Kong 仅转发 + CORS + 限流；JWT 由各后端 `JwtAuthenticationFilter` 校验。若存在绕开 Kong 直达后端的路径，纵深防御变薄。 | `deploy/kong/kong.yml.template` 注释与插件配置 |
| H3 | 全局限流故障策略 | Kong `rate-limiting` 使用 `fault_tolerant: true` 时，Redis 异常下的行为需与银行「故障时是否仍限流」策略对齐。 | 同上，全局 `rate-limiting` 插件 |
| H4 | 多因素认证（MFA） | 当前以密码 + JWT 为主，未见产品内 TOTP/WebAuthn 等；外资行常要求特权访问 MFA 或由企业 IdP 承担。 | 认证相关模块检索结论 |

---

## 2. 中优先级（架构与合规成熟度）

| # | 主题 | 摘要 |
|---|------|------|
| M1 | 多法人 / 多租户 / 数据驻留 | 现为统一 schema + RBAC/工作区；不等于监管意义上的法人隔离或数据驻留分区，若银行有此要求需单独设计。 |
| M2 | 职责分离（SoD） | Maker-checker 依赖流程与权限配置，非平台强制规则；需制度 + 配置双重落地。 |
| M3 | AI 与长超时路由 | Kong 对 AI、门户等长超时；需明确是否调用外部 LLM、数据出境、日志与合同（DPIA）。 |
| M4 | 审计日志完整性 | 保留期、防篡改（WORM/哈希链）、SIEM 对接等需按辖区与内规对标。 |
| M5 | 密钥与 Secret 治理 | 环境变量方向正确；银行常额外要求 KMS、轮换、分环境密钥、最小权限。 |

---

## 3. 低优先级 / 工程卫生

| # | 主题 | 摘要 |
|---|------|------|
| L1 | `PlatformCommonConfiguration` 中 `System.out.println` | 生产日志与信息暴露习惯上宜改为受控 logger。 |
| L2 | N8N 回调日志 | Info 级记录 token 等标识符，可按银行日志规范做脱敏或降采样。 |
| L3 | 前端 `v-html` | 现有路径多已配合 DOMPurify/sanitize；新增页面需保持同一规范。 |

---

## 4. 已具备能力（决策时可作为「现状基线」）

- JWT 黑名单在 Redis 不可用时的 fail-closed 倾向（需结合具体实现版本再确认）。  
- 登录相关路由在 Kong 上的单独限流。  
- 密码 BCrypt、CORS 模板化、关联 ID（X-Trace-Id）等。  
- Developer Workstation 工作区拦截器 + 组件层二次校验（`docs/developer-workstation-workspace-rbac.md`）。

---

## 5. 若未来采纳时可参考的改进方向（非承诺）

- **审计**：统一审计落库出口，明确保留期与查询责权。  
- **身份**：企业 IdP（SAML/OIDC）+ MFA，或产品内建 MFA；break-glass 策略。  
- **网关**：评估 Kong 层 JWT 或 mTLS 与后端双重校验；文档化禁止直连后端。  
- **合规交付物**：单页数据流图（含 AI、N8N、Kafka），标明数据类别与是否出境。

---

## 6. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-04 | 初稿：从外资银行视角审查结论单独成文，供后续决策 |
| 2026-04-04 | 增补：与 Demo 英文/种子数据约定的边界说明 |
