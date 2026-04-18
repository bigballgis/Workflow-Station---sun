# `init-platform-schema` — 平台表结构（DDL）

本目录仅保留 **`all-in-one-for-gui.sql`**：内容为 `deploy/init-scripts/00-schema/` 中 **`01-platform-security-schema.sql`** 起至 **`31-widen-flowable-act-hi-comment-columns.sql`** 止（与 `init-database.ps1` 中 Step 1 + Step 2 顺序一致）的**合并**，供图形客户端一次执行。

## 维护

- **改表结构**：在 **`deploy/init-scripts/00-schema/`** 修改对应文件，再**重新生成**本合并文件（按上述文件名顺序拼接为纯 SQL，保留文件头 `SET` 与分段注释；勿引入 `\i` / `\echo`）。
- 分片 **不再** 在本目录重复存放；需要单文件执行时在仓库中使用 **`deploy/init-scripts/00-schema/*.sql`**。
