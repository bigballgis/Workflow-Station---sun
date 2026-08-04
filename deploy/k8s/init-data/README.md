# `deploy/k8s/init-data` — 离线数据库初始化包

面向**新建 PostgreSQL**（空库）：业务 DDL、种子数据、Flowable 官方脚本集中存放，可拷贝到任意环境执行。

## 没有命令行 / 没有 `psql` 时（图形客户端）

在 **pgAdmin、DBeaver、Navicat、云厂商 SQL 窗口**等工具中，请使用下面两个**合并后的纯 SQL 文件**（无 `\i`、无 `\echo`，仅标准 SQL + 注释）：

| 顺序 | 文件 | 说明 |
|------|------|------|
| 1 | **`init-platform-schema/all-in-one-for-gui.sql`** | 平台表结构（DDL + 增量迁移） |
| 2 | **`init-flowable/create/flowable.postgres.all.create.sql`** | Flowable 引擎表（官方脚本，本身即为纯 SQL） |
| 3 | **`init-platform-seed/all-in-one-for-gui.sql`** | 种子数据**仅 `01-admin`**（角色/用户/权限等）；不含 08/15/16 演示包 |

在查询窗口中**整文件粘贴执行**，或「执行脚本」打开上述文件即可。若客户端对单次脚本大小有限制：**schema** 请改用 **`deploy/init-scripts/00-schema/`** 分片；**seed** 分片仅在 **`deploy/init-scripts/01-admin/`**。完整演示数据请用 **`deploy/init-scripts/init-database.ps1`**。

## 目录说明

| 目录 | 作用 |
|------|------|
| **`init-flowable/`** | Flowable 引擎：`create` / `upgrade` / `drop`（与引擎版本一致）。**升级现有库**见下方「Flowable 版本升级」。 |
| **`init-platform-schema/`** | 业务 **DDL**；**图形客户端用** `all-in-one-for-gui.sql`。 |
| **`init-platform-seed/`** | **种子数据**；**图形客户端用** `all-in-one-for-gui.sql`。 |

## 有 `psql` 时（可选）

可与图形界面相同，按顺序对 **`init-platform-schema/all-in-one-for-gui.sql`**、`init-flowable/create/flowable.postgres.all.create.sql`、**`init-platform-seed/all-in-one-for-gui.sql`** 执行 `psql -f`；或使用 **`deploy/init-scripts/init-database.ps1`** 做完整初始化。

## Flowable 版本升级（**已有库**，非新建库）

新建空库走 `init-flowable/create/`。**已经在跑的库**升级引擎版本，用合并脚本：

| 升级 | 文件 | 起始 → 目标 |
|------|------|------------|
| 7.0.0 → 7.2.0 | **`init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql`** | `schema.version` `7.0.0.0` → `7.2.0.2` |

该文件把三个官方增量脚本（`7.0.0→7.0.1→7.1.0→7.2.0`）按序合并，并加了前置校验（版本不是 `7.0.0.0` 就中止，防重复执行）、后置校验（版本必须为 `7.2.0.2`，且本仓库在 `00-schema/30-`、`31-` 做的 19 个加宽列必须仍是 `varchar(4000)`/`text`/`bytea`）。官方原始分步脚本同目录保留。

**执行前**：整库 `pg_dump` 备份（`act_*` 与 `dw_*`/`sys_*` 同库，且 `sys_function_unit_contents` 里指向 `ACT_RE_*` 的两个指针**无外键约束**，只恢复一部分会产生悬垂引用），并停掉 `workflow-engine`。脚本**单向**，官方无降级脚本，回滚只能整库恢复备份。

**各环境**：2026-08 起 **dev / sit / preprod / uat / prod** 的 `FLOWABLE_SCHEMA_UPDATE` 一律为 `false` —— 引擎只校验版本，**既不建表也不升级**，版本不符直接启动失败。发布新引擎版本前必须：**停引擎 → 整库备份 → 执行迁移脚本 → 再启动**。理由是 Flowable 的 schema 迁移单向不可逆（官方无降级脚本），不该由「重启了一下容器」顺带触发。

唯一例外是**单元测试**（`application-test.yml` 保持 `true`）：每次跑都是全新的内存 H2，没有需要保护的数据。

### dev 的 act_* 从哪来（引擎既然不建表）

由 postgres 首次初始化时的 `deploy/init-scripts/00-init-all.sh` **Step 0** 建好（在业务表之前），全部是官方 SQL，没有手工拼装的部分：

```
[0/7] create/flowable.postgres.all.create.sql   官方建表（7.0.0 基线）
        + upgradestep.7.0.0.to.7.0.1            ┐
        + upgradestep.7.0.1.to.7.1.0            ├ 官方增量，落到 7.2.0.2
        + upgradestep.7.1.0.to.7.2.0            ┘
[1/7] 业务表 00-schema/01-05
[2/7] 增量迁移，含 30-/31-                       ← act_* 已存在，加宽在此天然生效
[3/7] 角色 / 用户
[4x/7] demo Function Unit
```

`deploy/k8s/init-data/init-flowable` 通过 compose 以 `/flowable-sql:ro` 单独挂进 postgres 容器（不复制一份到 `init-scripts/`，避免同一份 SQL 在仓库里存两处、升版本时漏改其中一个）。

**两点背景，改之前先读**：

1. **为什么必须在 Step 2 之前** —— `30-`/`31-` 用的是 `ALTER TABLE IF EXISTS`。在这一步之前，`act_*` 要等引擎启动时才建，于是这两个脚本在全新库上**静默空转**，19 个加宽列从未生效。这个顺序问题在 7.2.0 升级之前就存在，只是那时引擎自己建表（`true`）把它掩盖了。
2. **升 Flowable 版本时要改哪里** —— 把新的官方 `upgradestep` 文件放进 `upgrade/`，并追加到 `00-init-all.sh` 里的 `FLOWABLE_UPGRADE_STEPS`。忘了改也不会静默出错：引擎是 `false`，版本不符会拒绝启动。

> 另注：`99-maintenance/00-wipe-all-function-units.sql` 曾被 init 自动调用（旧 Step 4）。它会 **DROP 掉所有 `act_*`/`flw_*`**，而 initdb 只在数据目录为空时运行、Step 1–3 又不创建任何 Function Unit —— 也就是说它在初始化时无事可清，只是把刚建好的 Flowable 表删掉。现已从 init 移除；该脚本保留在 `99-maintenance/` 供**人工重播种既有库**使用。

> 已验证：`create + 三个 upgradestep` 产出的 schema 与引擎自建的 schema，核心 `act_*`/`flw_*` 表**逐列一致**（差异仅为 content/form 引擎的多余表，以及本仓库刻意加宽的 19 列）。

## 与 `deploy/init-scripts` 的关系

**权威来源**为 `deploy/init-scripts/`。`init-platform-schema/all-in-one-for-gui.sql` 由 `00-schema` 中 01–31 脚本合并而来；`init-platform-seed/all-in-one-for-gui.sql` 对应 **`01-admin`** 合并结果，分片仅在 **`deploy/init-scripts/01-admin/`** 维护（见各目录 README）。
