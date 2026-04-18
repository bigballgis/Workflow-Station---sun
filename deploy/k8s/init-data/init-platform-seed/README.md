# `init-platform-seed` — 平台种子数据

## 使用方式

- **`all-in-one-for-gui.sql`** — 唯一入口：含原 **`deploy/init-scripts/01-admin/`** 合并内容（角色、用户、权限、E2E 测试用户等），顶部已含 `SET client_min_messages` / `SET timezone`。图形客户端与 **`psql`** 均直接执行本文件即可，例如：

```text
psql -h <host> -p <port> -U <user> -d <db> -v ON_ERROR_STOP=1 -f all-in-one-for-gui.sql
```

## 维护

修改种子请到 **`deploy/init-scripts/01-admin/`**，再按 6 个脚本顺序重生成本合并文件（并去掉 `\echo` 行）。

## 前置条件

已执行：`../init-platform-schema/all-in-one-for-gui.sql`，以及 `../init-flowable/create/flowable.postgres.all.create.sql`。

## 完整演示数据

使用 **`deploy/init-scripts/init-database.ps1`** 或其它功能单元脚本。
