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

**各环境**：`dev`/`sit`/`preprod` 的 `FLOWABLE_SCHEMA_UPDATE=true`，引擎启动时自动迁移，一般无需手工执行；`uat`/`prod` 为 `false`，引擎不会自动迁移且版本不符会启动失败，**必须**先执行本文件再发布新镜像。

> 已验证：本机 dev 库由引擎自动迁移；同一份备份恢复到临时库后用本脚本手工迁移，两者产出的 `act_*`/`flw_*` schema **841 列逐字节一致**。

## 与 `deploy/init-scripts` 的关系

**权威来源**为 `deploy/init-scripts/`。`init-platform-schema/all-in-one-for-gui.sql` 由 `00-schema` 中 01–31 脚本合并而来；`init-platform-seed/all-in-one-for-gui.sql` 对应 **`01-admin`** 合并结果，分片仅在 **`deploy/init-scripts/01-admin/`** 维护（见各目录 README）。
