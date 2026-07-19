# Schema 单一事实来源方案（init-scripts 唯一来源，清退 Flyway）

> **✅ 已执行完毕（2026-06）**：本方案四个步骤均已落地——pom 无 Flyway 依赖、
> `application.yml` 固化 `flyway.enabled: false`、历史迁移归档 `docs/legacy-flyway-migrations/`、
> 方向 ① 脚手架（`deploy/flyway/` 等）已回退。本文作为历史决策与执行记录保留，
> 现行说明见 [schema-and-migration.md](../schema-and-migration.md)。

> 方向 ②：承认现实——所有环境都关着 Flyway、schema 一直由 init-scripts 建——
> 把 **`deploy/init-scripts` 正式确立为唯一 schema 来源**，清退名存实亡的后端 Flyway 迁移。
> 比"扶正 Flyway"工作量小得多、风险低得多，同样消灭双轨。

---

## 1. 为什么是这个方向（实测依据）

本会话实测确认的事实：

| 事实 | 证据 |
|---|---|
| **所有环境 Flyway 都关** | dev `docker-compose.dev.yml` 3 处 `SPRING_FLYWAY_ENABLED:false`；preprod/uat configmap 同样 `false` |
| **live 库无 Flyway 痕迹** | dev 库 179 张表、**0 张 flyway_schema_history** |
| **后端迁移从未在部署中跑过** | workflow-engine-core 有 `V500__init_schema.sql` 却**无 flyway 依赖** → 纯文档 |
| **Flyway 仅是依赖，无代码引用** | 3 个服务 pom 有 flyway-core，`backend/*/src/main/java` 零 `org.flywaydb` 引用 |
| **init-scripts 已是完整来源** | 本会话审计 + 补齐 drift（rt_pk_sequences / uk_dw_table_name / fk_ref 索引），init = 后端迁移的完整快照 + 种子 |

**结论**：后端 50 个 `V*.sql` 迁移是**冗余历史**，init-scripts 才是真来源。维护两套纯属负担与漂移源。

---

## 2. 目标

- `deploy/init-scripts` = **唯一** schema 来源，写入约定文档。
- 后端 Flyway 迁移**归档**（保留 git 历史，移出运行路径），移除 Flyway 依赖与自动配置。
- 消灭"每加一个迁移要手工同步 init-scripts"的双轨负担。

---

## 3. 改动清单（分步、可独立验证、可回滚）

### Step 1　固化"关闭 Flyway"为基础配置（去掉对 env 开关的隐式依赖）
当前 `flyway.enabled=true` 在 `application.yml`，靠各环境 env `SPRING_FLYWAY_ENABLED=false` 才关。
改为在**基础 `application.yml` 里直接 `flyway.enabled: false`**，不再依赖 env 覆盖。
- 影响文件：admin-center / user-portal / developer-workstation 的 `application.yml`
- 验证：本地起服务不再尝试连 flyway。

### Step 2　移除 Flyway Maven 依赖与插件
- 删 3 个服务 pom 的 `flyway-core` + `flyway-database-postgresql` 依赖。
- 删根 `pom.xml` 的 `flyway.version`、dependencyManagement 的 flyway-core、build 的 flyway-maven-plugin。
- 验证：`mvn -pl ... -am compile` 仍 BUILD SUCCESS（已确认无代码引用 flyway）。

### Step 3　归档后端迁移文件（不删，保留历史）
把 4 个服务的 `src/main/resources/db/migration/` 整体移到仓库归档区，例如
`docs/legacy-flyway-migrations/<svc>/`，并在该目录放 README 说明：
"这些是历史 Flyway 迁移，已于 2026-06 清退；schema 真来源见 deploy/init-scripts/00-schema。"
- 为什么归档而非物理删：它们记录了 schema 演进史，对追溯有价值；git 虽留历史，但归档目录更易查阅。
- 验证：jar 里不再含 `db/migration`（Flyway 即便误启用也无脚本可跑）。

### Step 4　回退 ① 方向残留 + 文档化单一来源
- 删 ① 方向脚手架：`deploy/flyway/`、`deploy/environments/dev/docker-compose.flyway.yml`、
  `00-init-all.sh` 里的 `USE_FLYWAY`/`FLYWAY_SEED_PHASE` 守卫（恢复原状）。
- 更新 `PROJECT_ARCHITECTURE.md` / `deploy/CLAUDE.md` / `BUILD_GUIDE.md`：明确
  **"schema 唯一来源 = deploy/init-scripts/00-schema；新增/改表只改这里；不再有 Flyway"**。
- 更新 [architecture-optimization-plan.md](../architecture/architecture-optimization-plan.md)：把"双轨同步 P1"标记为"已通过清退 Flyway 解决"。

---

## 4. 保留的本会话成果（与本方向无冲突，继续有效）

- **P0-1 JSONB 索引**：`V214__rt_data_rows_search_indexes.sql` —— ⚠️ 注意，这是个 Flyway 文件！
  Step 3 归档后它不再被执行。但它的索引**已同步进 init-scripts/00-schema/21**（本会话已做），
  所以归档它**不丢能力**。归档时确认 21 里 trgm + (table_id,id) 索引在即可。
- **drift 修复**（rt_pk_sequences / uk_dw_table_name / fk_ref）：都在 init-scripts，保留。
- **P0-2 user-portal 多副本/HPA/PDB**：与本方向无关，保留。

---

## 5. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 某环境其实悄悄开着 Flyway | 已查 dev/preprod/uat 全 false；Step 1 固化为基础 false 后更稳 |
| 删依赖导致编译失败 | 已确认零代码引用；Step 2 后 `mvn compile` 验证 |
| 归档后丢失某迁移里 init 没覆盖的变更 | 本会话已审计 drift 并补齐；Step 3 前可再跑一次"逐表比对"确认 init 完整 |
| 将来想恢复 Flyway | 归档目录 + git 历史可完整恢复；本方向不删除任何 SQL 内容 |

**回滚**：每步独立。Step 2/3 若有问题，git revert 即可恢复依赖与迁移目录。

---

## 6. 与方向 ①（扶正 Flyway）的对比

| | ① 扶正 Flyway | ② init-scripts 单一来源（本方案） |
|---|---|---|
| 工作量 | 大（写 5 个 baseline + 修重复版本号 + 双世界 history） | 小（删依赖 + 归档 + 文档） |
| 风险 | 中高（改建库流程、跨服务顺序） | 低（无代码引用、所有环境本就不用 flyway） |
| 是否消灭双轨 | 是 | 是 |
| 是否贴合现实 | 否（强行启用从未用过的机制） | **是（现实就是 init-scripts 在建库）** |

---

## 7. 一句话总结

> 既然 Flyway 在 dev/preprod/uat **全部关闭**、schema 一直由 init-scripts 建、后端迁移从未在部署中执行，
> 就把现实正式化：**init-scripts 设为唯一 schema 来源，归档后端 Flyway 迁移、移除 Flyway 依赖**——
> 用最小改动、最低风险消灭双轨漂移。
