# Flyway 单一事实来源改造方案（全新环境也走 Flyway）

> 目标：消灭"双轨制"——让后端 4 个服务的 Flyway 迁移成为 schema 的**唯一事实来源**，
> 删除 `deploy/init-scripts/00-schema/` 这套手工镜像，全新环境也用 Flyway 建表。
> 种子/demo 数据（角色、5 个 demo FU、post-seed 对齐）**留在 init-scripts**，各司其职。
>
> ⚠️ 本方案改的是**全新环境建库流程**，风险等级高于前面所有 P0。**需评审通过后再执行。**

---

## 1. 为什么要做（问题根因）

当前 schema 有两套来源，必须手工保持一致：

| 轨道 | 位置 | 何时跑 |
|---|---|---|
| Flyway（真来源） | `backend/*/src/main/resources/db/migration/` | 服务启动时各自增量跑 |
| init-scripts 镜像（手工抄） | `deploy/init-scripts/00-schema/01~44.sql` | 全新建库时 DBA 手动跑 |

**问题**：每加一个 Flyway 迁移都要手工同步到 init-scripts，漏一次就漂移。本次审计就发现了 3 处真实漂移（rt_pk_sequences 整表缺失、uk_dw_table_name 作用域不一致、fk_ref 部分索引缺失）。这是**结构性维护负担**，迟早再次漂移。

---

## 2. 关键认知：init-scripts ≠ schema

`00-init-all.sh` 干的远不止建表，只有一部分该归 Flyway：

| 步骤 | 内容 | 归属 |
|---|---|---|
| Step 0 | 建 `n8n_dev` 独立数据库 | ❌ 留 init（跨库，Flyway 管不了） |
| **Step 1-2** | **建 schema（01~44 DDL）** | ✅ **改走 Flyway** |
| Step 3 | 角色/组/admin 用户 | ❌ 留 init（数据） |
| Step 4-5 | wipe + 灌 5 个 demo FU + post-seed | ❌ 留 init（demo 种子，wipe-reseed 语义与 Flyway"只前进不重复"根本冲突） |

**结论：只把 Step 1-2 替换为 `flyway migrate`，其余原样保留。**

---

## 3. 必须先解决的硬问题：跨服务版本号冲突

4 个服务现在**各自有独立的 `flyway_schema_history` 表**，版本号只在各自命名空间唯一。合并成"单次 migrate 建全库"时，版本号会全局排序、撞车：

| 服务 | 版本段 | 问题 |
|---|---|---|
| developer-workstation | V1, V2, V3, V301~V323 | **V1/V2/V3 是历史遗留**（consolidated 重复内容，与 V301+ 重叠） |
| user-portal | V110, V400~V406 | 段独立，OK |
| admin-center | V201~V214 | **V206 重复两次**（audit / rename） |
| platform-security | V210 | 🔴 **与 admin-center V210 撞号** |
| workflow-engine | V500 | 段独立，OK |

### 已存在的潜伏问题
admin-center 的 flyway `locations` 同时含 `admin-center` + `platform-security` 两目录、同一 history 表，而**两边都有 V210**。Flyway 要求同一 history 内版本号唯一 → 现在要么其一被忽略、要么启动报错。**这是现状就脆的点，合并前必须先确认 platform-security V210 当前到底有没有真正应用。**

### 冲突解法（两选一）
- **方案 A：保持每服务独立 history 表（推荐）**
  统一的 Flyway 不用单一 history，而是**按服务分多次 migrate**，每次指定独立的 `flyway.table`（如 `flyway_history_admin`、`flyway_history_dw`…）和对应 location。这样版本号冲突天然消失（各表各命名空间），且与后端运行时行为**完全一致**（后端本来就是各服务各自一个 history）。
  → `00-init-all.sh` 里 Step 1-2 变成 5 次 `flyway migrate`（platform-security、admin-center、dw、user-portal、workflow-engine），顺序见下。
- **方案 B：全局重新编号成单一序列**
  把所有迁移重排成无冲突的单一版本序列。**不推荐**——要改后端现有迁移文件，破坏已部署环境的 history，爆炸半径极大。

**采用方案 A。**

---

## 4. 目标目录布局

```
deploy/
  flyway/                          ← 新增：唯一的 Flyway 运行配置（不复制 SQL）
    flyway.conf                    ← 基础连接配置（URL/user 由环境变量注入）
    migrate-all.sh                 ← 按服务顺序跑 5 次 migrate，各自独立 history 表
    README.md                      ← 说明：SQL 真来源在 backend/*/db/migration，此处只是运行器
  init-scripts/
    00-init-all.sh                 ← 改：Step 1-2 调 deploy/flyway/migrate-all.sh；其余不动
    00-schema/                     ← 删除（或保留一版打 tag 后移除）
    01-admin/ 08-* 15-* 16-* …     ← 种子/demo，原样保留
```

**SQL 不复制**：`flyway.conf` 的 `locations` 用 `filesystem:` 指向后端各服务的 `db/migration` 目录（容器内挂载或镜像打包），后端 `src` 仍是唯一 SQL 来源。

### Flyway 怎么读到后端 SQL（容器）
- 本地/docker-compose：把 `backend/*/src/main/resources/db/migration` 挂载进 flyway 容器。
- K8s/生产：构建一个含全部迁移 SQL 的 flyway initContainer 镜像，或用现有后端镜像里已打包的 `classpath:db/migration`（后端 jar 里本就有）。

---

## 5. 执行顺序（跨服务外键依赖）

按服务跑，顺序很重要（被依赖者先建）：

```
1. platform-security   (sys_users / sys_roles 基础表，被所有服务引用)
2. admin-center        (sys_*, rt_*, bi_* —— 大量被引用)
3. developer-workstation (dw_*)
4. user-portal         (up_*, portal_* —— 引用 sys_user_business_unit_roles 等)
5. workflow-engine     (wf_*, act_* —— Flowable 自管，独立)
```

每步独立 history 表、`baseline-on-migrate=true`（兼容已有库）、`validate-on-migrate=false`（沿用现状）。

---

## 6. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 全新建库流程改坏 → 起不来 | 先在**一次性 docker volume**上端到端验证（`docker compose down -v` 全新起），对比改造前后 `\dt` 表清单一致 |
| 跨服务外键错序 | 按 §5 顺序；若某服务迁移引用了后建服务的表 → 暴露真实耦合，单独处理 |
| platform-security V210 撞号 | 改造前先确认其当前应用状态；方案 A 的独立 history 表天然隔离 |
| 已部署的老库 | 老库已有各自 history 表，方案 A 的多 history 与之**完全兼容**，baseline-on-migrate 接管，不破坏 |
| demo/E2E 依赖 00-schema 的表 | §2 已确认 demo 只依赖表存在；Flyway 建完表后 demo 脚本照跑 |

**回滚预案**：本改造是"换建库步骤"，不删后端迁移。若 flyway 路径出问题，`00-init-all.sh` 回退到调用旧 `00-schema/*.sql`（保留一份打 tag）即可立即恢复。

---

## 7. 分步落地（每步可独立验证）

1. **不删任何东西**，先新增 `deploy/flyway/`（conf + migrate-all.sh + README）。
2. 改 `00-init-all.sh`：Step 1-2 **并行保留**——加一个 `USE_FLYWAY=true` 开关，true 走 flyway、false 走旧 00-schema。默认 false。
3. 在全新 docker volume 上以 `USE_FLYWAY=true` 验证：建库成功 + 表清单与旧路径逐表一致 + demo 全部灌入成功 + 应用起得来。
4. 验证通过后，翻默认开关为 true，观察一个迭代。
5. 稳定后，删除 `00-schema/`（打 tag 存档），完成单一事实来源。

---

## 8. 与现有规则的契合

- `deploy/CLAUDE.md`：改环境/部署链路要同会话同步 K8s ConfigMap + 更新 BUILD_GUIDE/deploy 文档 → 本方案完成后需更新。
- 本方案完成后，[architecture-optimization-plan.md](../architecture/architecture-optimization-plan.md) 里"双轨同步"P1 项可标记关闭。

---

## 9. 一句话总结

> 把 schema 的唯一来源固定在后端 4 个服务的 Flyway 迁移；全新环境用一个 `deploy/flyway/` 运行器**按服务顺序、各自独立 history 表**跑 migrate（绕开跨服务版本号冲突）；种子/demo 留在 init-scripts。
> 用 `USE_FLYWAY` 开关灰度、可逐表比对、可秒级回滚——**消灭 00-schema 手工镜像这个漂移根源。**
