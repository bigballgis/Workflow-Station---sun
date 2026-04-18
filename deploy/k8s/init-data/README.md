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
| **`init-flowable/`** | Flowable 引擎：`create` / `upgrade` / `drop`（与引擎版本一致）。 |
| **`init-platform-schema/`** | 业务 **DDL**；**图形客户端用** `all-in-one-for-gui.sql`。 |
| **`init-platform-seed/`** | **种子数据**；**图形客户端用** `all-in-one-for-gui.sql`。 |

## 有 `psql` 时（可选）

可与图形界面相同，按顺序对 **`init-platform-schema/all-in-one-for-gui.sql`**、`init-flowable/create/flowable.postgres.all.create.sql`、**`init-platform-seed/all-in-one-for-gui.sql`** 执行 `psql -f`；或使用 **`deploy/init-scripts/init-database.ps1`** 做完整初始化。

## 与 `deploy/init-scripts` 的关系

**权威来源**为 `deploy/init-scripts/`。`init-platform-schema/all-in-one-for-gui.sql` 由 `00-schema` 中 01–31 脚本合并而来；`init-platform-seed/all-in-one-for-gui.sql` 对应 **`01-admin`** 合并结果，分片仅在 **`deploy/init-scripts/01-admin/`** 维护（见各目录 README）。
