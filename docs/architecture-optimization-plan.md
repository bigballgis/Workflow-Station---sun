# Workflow Station 架构优化方案（不迁 schema 版）

> 约束前提：**保持共享 PostgreSQL schema 不变**，不做数据库所有权拆分、不做表迁移。
> 所有优化集中在 **应用层（代码耦合、查询性能、韧性）** 和 **运维层（HA、可观测、交付）**。
> 目标终态：**扎实的模块化单体（Modular Monolith on shared DB）**，而非真微服务。

---

## 0. 为什么是这个方向

当前系统本质是"分布式单体"——4 个 Spring Boot 应用共用一个 schema，付了微服务的运维成本，却没拿到独立演进的好处。

业界对这种形态的成熟解法不是"更微服务化"（那会引入分布式事务、Saga、最终一致性，得不偿失），而是 **Martin Fowler 的 "Monolith First"**：先把模块边界和工程纪律做扎实，把生产可用性补齐，等组织规模真把你逼到不得不拆库，再沿清晰边界拆。

既然不动 schema，本方案就**不碰数据所有权**，只做下面三类不需要迁移数据的优化：

1. **生产可用性（P0）** — 不做就是线上事故
2. **降耦合 / 控爆炸半径（P1）** — 纯代码层，不动表
3. **前端去重 + 可观测性（P2）** — 工程纪律

---

## 1. 现状评分（代码层面，非文档）

| 维度 | 真相 | 性质 | 本方案是否处理 |
|---|---|---|---|
| 服务间通信 | 全 `RestTemplate` 同步阻塞 + 手写 CircuitBreaker | 🟡 | ✅ P1 |
| `platform-common` | 110 类 god module，4 服务全依赖，改一处全重编 | 🔴 | ✅ P1 |
| JSON 行存储 | `rt_table_data_rows.data` JSONB **无 GIN 索引**，搜索全表 ILIKE 扫描 | 🔴 | ✅ P0（加索引，不迁表）|
| K8s 部署 | 所有服务 `replicas:1`，无 HPA / PDB | 🔴 | ✅ P0 |
| Kafka / Redis | 单副本，丢 pod 丢数据 | 🔴 | ✅ P0 |
| 服务发现 / 配置中心 | Spring Cloud 声明但未用，URL 硬编码 | 🟡 | ✅ P1（轻量）|
| 前端 3 SPA | auth/api/util 代码复制（自标 ISSUE-095）| 🟡 | ✅ P2 |
| 可观测性 | 有 Kong correlation-id + Prometheus + Istio，缺落地 | 🟡 | ✅ P2 |
| 共享 schema | 4 服务共用，跨服务读表 | 🔴 | ❌ **本方案不处理（按约束保持现状）** |

> 唯一做对的边界：**Flowable 工作流引擎**独占 `act_*` 表、只通过 HTTP 被调——保持不动。

---

## 2. 优化路线图（按 ROI 排序）

### P0 — 生产可用性（不迁数据，但不做就是事故）

#### P0-1　JSONB 查询加索引（最硬的扩展性天花板）
- **问题**：`rt_table_data_rows` 搜索是 `data->>'field' ILIKE ?` 全表扫描，10w 行即退化；`PortalRelationTableServiceImpl` 把行全量反序列化到内存再过滤。
- **做法（不动表结构，只加索引）**：
  1. 对高频检索字段建**表达式 B-Tree 索引**：`CREATE INDEX ON rt_table_data_rows ((data->>'field_name'));`
  2. 全文/模糊检索字段建 **GIN 索引**：`CREATE INDEX ON rt_table_data_rows USING gin (data jsonb_path_ops);`，或 `pg_trgm` 支持 ILIKE：`CREATE INDEX ON rt_table_data_rows USING gin ((data->>'field') gin_trgm_ops);`
  3. 把内存过滤下推为 SQL `WHERE data @> '{...}'` / `data->>'x' = ?`，分页前过滤而非分页后。
- **验收**：50w 行表，按索引字段查询 P95 < 200ms（当前 1–5s）。
- **工作量**：S（1 个 Flyway 迁移 + 改 `RelationTableDataServiceImpl` / `PortalRelationTableServiceImpl` 查询）。
- **注意**：索引是元数据，不迁数据，与"不迁 schema"约束不冲突。

#### P0-2　后端服务多副本 + 探针 + PDB
- **问题**：所有 Deployment `replicas:1`，pod 重启即停服，滚动更新有窗口。
- **做法**：业务服务 `replicas: 2`（admin-center / user-portal / workflow-engine）；补 `PodDisruptionBudget minAvailable: 1`；确认 readiness/liveness 探针都生效（admin-center 已有，核对另两个）。
- **前置依赖**：多副本要求**会话无状态**——核对 JWT 是否纯无状态（看代码是 httpOnly cookie + 后端校验，应 OK）、有无本地内存缓存需迁 Redis（见 P0-4）。
- **验收**：滚动更新期间 `/health/ready` 不中断；删任一 pod 服务不掉。
- **工作量**：S（改 k8s yaml）。

#### P0-3　HPA 自动扩缩
- **做法**：给业务服务加 `HorizontalPodAutoscaler`，按 CPU 70% / 自定义 QPS 指标扩缩，`minReplicas: 2, maxReplicas: 6`。
- **前置**：需 metrics-server；资源 `requests` 要设准（HPA 按 request 算百分比）。
- **工作量**：S。

#### P0-4　Kafka / Redis HA
- **问题**：单副本，pod 丢则数据丢；多副本后端若依赖本地缓存会不一致。
- **做法**：
  - Redis：上 3 节点（Sentinel 或 Cluster），或换公司托管 Redis；确认应用缓存、Kong rate-limit 都指向 HA 实例。
  - Kafka：副本因子 ≥ 3、min.insync.replicas=2（KRaft 3 节点），或换托管。
- **验收**：杀一个 Redis/Kafka pod，业务无感知。
- **工作量**：M（有状态组件，需测故障切换）。

---

### P1 — 降耦合 / 控爆炸半径（纯代码层，不动表）

#### P1-1　拆 `platform-common` god module
- **问题**：110 类一个 jar，4 服务全依赖，改 1 个 DTO → 全部重编 → 发版必须齐步走（这是"分布式单体"耦合的代码根源）。
- **做法（按职责切，服务只依赖所需）**：

  | 拆出模块 | 内容 |
  |---|---|
  | `platform-dto` | Response 信封、分页、过滤 DTO、enums |
  | `platform-exception` | 11 个异常类型 |
  | `platform-audit` | 审计框架 |
  | `platform-saga` | Saga 工具（实际用得少，单独隔离避免误依赖）|
  | `platform-jdbc` | JdbcTemplate / JSONB helper |
  | `platform-functionunit` | FunctionUnit 元数据（边界大，单列）|

- **验收**：改 `platform-audit` 不再触发 4 服务全量重编；依赖图无环。
- **工作量**：M（机械拆分 + 改各服务 pom 依赖；零行为变更，可被测试守住）。
- **不迁 schema 影响**：无，纯 Maven 模块重组。

#### P1-2　同步调用换 Resilience4j
- **问题**：developer-workstation 手写 `CircuitBreakerRegistry` / `GracefulDegradationManager`，无标准指标、无统一超时。
- **做法**：换成 Resilience4j（CircuitBreaker + TimeLimiter + Retry + Bulkhead），给所有 `RestTemplate`/跨服务 client 统一加：连接/读超时、重试上限、熔断阈值、fallback。指标接 Micrometer → Prometheus。
- **验收**：admin-center 挂掉时 user-portal 走 fallback 不雪崩；熔断状态在 Grafana 可见。
- **工作量**：M。

#### P1-3　轻量配置/发现治理（不引 Spring Cloud 全家桶）
- **问题**：服务 URL 硬编码 env，spring-cloud 声明了没用。
- **做法**：**不上 Eureka/Config Server**（K8s 已提供 DNS 服务发现 + ConfigMap）。只做：
  - 服务间用 K8s Service DNS（`http://admin-center:8090`）替代硬编码 IP/host。
  - 配置全收口到 ConfigMap/Secret，删 spring-cloud 死声明。
- **理由**：K8s 原生能力已覆盖服务发现与配置，再叠 Spring Cloud 是重复造轮子。
- **工作量**：S。

---

### P2 — 前端去重 + 可观测性

#### P2-1　前端 monorepo + 共享包（不上微前端）
- **问题**：3 个 SPA 复制 `auth.ts` / `request.ts` / 10+ util（自标 ISSUE-095），改 bug 要改 3 处。
- **做法**：pnpm workspace，抽 `packages/core`（auth、api client、sso、httpError、formCreate 运行时等）；3 app 改为 `apps/*` 引用。
- **明确不做微前端**（module federation / qiankun）：只有 3 个同源 SPA，微前端复杂度换不来收益。共享库即够。
- **保留**：per-app token key（`ws_ac_` / `ws_dw_` / `ws_up_`）防 localStorage 会话冲突——抽共享时参数化，别合并 key。
- **验收**：auth 逻辑单一来源；任一 app 独立构建仍 OK。
- **工作量**：M。

#### P2-2　可观测性落地
- **现有**：Kong correlation-id（X-Trace-Id）、Kong Prometheus 插件、Istio。
- **补齐**：
  - Tracing：后端接 Micrometer Tracing / OpenTelemetry，透传 `X-Trace-Id`，接 Jaeger/Tempo。
  - Metrics + 看板：Prometheus + Grafana（服务 QPS/延迟/错误率、熔断状态、JSONB 查询耗时、Kafka lag）。
  - 日志：结构化 + traceId 串联（Loki/ELK）。
- **工作量**：M。

---

## 2.5 执行进度（实际落地记录）

| 项 | 状态 | 实际做法 / 关键决策 |
|---|---|---|
| **P0-1 JSONB 索引** | ✅ 已落地 | Flyway `V214__rt_data_rows_search_indexes.sql`(pg_trgm + `data::text` GIN + `(table_id,id)`)；查询侧在 admin-center / user-portal 预置 `data::text ILIKE` 宽守卫;**双轨同步**到 init-scripts/00-schema/21。零新增测试失败。 |
| **P0-2 多副本/HA** | ✅ 部分落地 | **分差化**：user-portal(无 @Scheduled、JWT 无状态)→`replicas:2`+HPA(2~6,CPU70%)+PDB(minAvailable:1)。**admin-center / workflow-engine 保持 1 副本**——二者有未加分布式锁的 @Scheduled(LDAP/BI 同步、retry/deadletter 执行器,且无 `FOR UPDATE SKIP LOCKED`),直接多副本会双跑。 |
| **P0-2 遗留前置** | ⏳ 转 P1 | 要让 admin-center/workflow-engine 也能多副本,**必须先给 @Scheduled 加分布式锁**(推荐 ShedLock 复用现有 Redis;需新依赖+shedlock 表迁移+双轨同步)。本轮按"零新依赖最安全"原则未做,列入 P1-4。 |
| **Schema 漂移修复** | ✅ 已落地 | 审计全部 Flyway↔init-scripts:补 `rt_pk_sequences`(admin V207,init 缺整表)→init 21;`uk_dw_table_name` per-FU→全局(dw V320)→init 04;`idx_dw_field_definitions_fk_ref` 部分索引(dw V317)→init 04。V323 级联 FK 已在 init 33(无需改)。 |

### 双轨 schema 同步 → 已解决（清退 Flyway）
原"每加迁移要手工同步 init-scripts"的双轨负担，已通过**清退 Flyway、确立 init-scripts 为唯一来源**消除
（2026-06）。后端 `db/migration` 已归档至 `docs/legacy-flyway-migrations/`，Flyway 依赖/配置移除。
详见 `docs/schema-single-source-init-scripts-plan.md`。今后新增/改表只改 `deploy/init-scripts/00-schema/`。

### 已知未处理（有意保留）
- **meeting/participants demo 表列漂移**：V406(Flyway)有 `description`、缺 `task_status`;demo seed `16/06` 反之(有 `task_status`+`display_name`)。二者均 `CREATE TABLE IF NOT EXISTS`,谁先跑谁赢。**纯 demo 范畴、非生产表**,demo seed 自带 `ALTER ADD task_status` 自愈关键列,故不动 demo 脚本,仅此记录。
- **P1-4 定时任务分布式锁**:见上,admin-center/workflow-engine 多副本的前置。

## 2.6 剩余 7 项执行结果（2026-06-27，按风险从低到高）

| 项 | 状态 | 实际做法 |
|---|---|---|
| **P1-3 配置收口** | ✅ 完成 | K8s configmap 已用 service DNS（`<svc>-service.<ns>:8080`）、代码无硬编码 IP；删根 pom 死 spring-cloud BOM（零代码引用） |
| **P1-4 ShedLock** | ✅ 完成 | admin/workflow 共 5 个 `@Scheduled` 加 `@SchedulerLock`（`RedisLockProvider`，复用 Redis、无需表）；heartbeat（每节点各写）与 in-memory session cleanup **不锁**；BUILD SUCCESS |
| **P1-2 Resilience4j** | ✅ 完成 | 删 developer-workstation 未被调用的手写熔断框架（15 类+controller+config+4 测试）；4 服务 RestTemplate 经 `ClientHttpRequestInterceptor` 织入 Resilience4j 熔断（失败抛原异常、不改返回语义）+ Micrometer 指标 |
| **P0-4 Kafka/Redis HA** | ⏳ 跨过 | in-cluster 单实例→真 HA 需 StatefulSet 多节点+客户端重配+**真实故障切换测试**，属基础设施团队活、此环境无法实测，记待办 |
| **P2-2 可观测性** | ✅ 完成 | 4 服务加 `micrometer-tracing-bridge-brave`，traceId/spanId 入 MDC+统一日志格式、跨 RestTemplate 透传、100% 采样；Prometheus 端点+熔断指标本就/已接入。Grafana/Jaeger 落地属 ops |
| **P2-1 前端 monorepo** | ✅ 首切 | 建 `frontend/pnpm-workspace.yaml` + `packages/core`，抽三 app 完全一致的 `languageLabel`；**三 app import 未改**（需 pnpm 构建+截图验证）。auth/httpError/sso 分叉严重（diff 332/180/63 行）留专项，见 `packages/core/README.md` |
| **P1-1 拆 platform-common** | 📋 设计 | 全局爆炸半径最大；出设计文档 `docs/p1-1-split-platform-common-plan.md`（模块划分、保留包名 vs 改名取舍、自底向上拆分顺序、逐步验证）。待评审后单独一轮执行 |

## 3. 分期与里程碑

| 阶段 | 内容 | 目标 | 工作量 |
|---|---|---|---|
| **Sprint 1** | P0-1 JSONB 索引 + 查询下推；P0-2 多副本+PDB+探针 | 解扩展性天花板 + 消除单点停服 | S+S |
| **Sprint 2** | P0-3 HPA；P0-4 Kafka/Redis HA | 弹性 + 有状态组件 HA | S+M |
| **Sprint 3** | P1-1 拆 platform-common；P1-3 配置收口 | 控爆炸半径、独立发版 | M+S |
| **Sprint 4** | P1-2 Resilience4j；P2-2 可观测性 | 韧性 + 盲飞终结 | M+M |
| **Sprint 5** | P2-1 前端 monorepo 共享包 | 消除前端复制 | M |

---

## 4. 明确不做（及理由）

| 不做 | 理由 |
|---|---|
| **迁 schema / 拆库** | 按本次约束保持共享 DB 现状；模块边界先靠代码纪律守 |
| **真微服务化（独立库 + Saga）** | 团队规模未到，复杂度 ROI 为负；当前是"做成模块化单体"而非"更微服务" |
| **网关多厂商 Adapter SPI（文档 Phase 5）** | 假需求——只有一个 Kong，无第二网关；为假想未来抽象 = 过度设计 |
| **微前端（module federation/qiankun）** | 只有 3 个同源 SPA，共享库已足够 |
| **Spring Cloud 全家桶** | K8s 原生 DNS 发现 + ConfigMap 已覆盖，重复造轮子 |

---

## 5. 一句话总结

> 不迁 schema 的前提下，最成熟的做法是把"分布式单体"补成"**生产级的模块化单体**"：
> **加索引解查询天花板、加副本/HA 解停服、拆 platform-common 控爆炸半径、Resilience4j 加韧性、前端共享包去复制、可观测性终结盲飞**。
> 数据所有权拆分留到组织规模真把你逼到拆库那天——而那天到来前，上面每一步都在为它铺路且独立有价值。
