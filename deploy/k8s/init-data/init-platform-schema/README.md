# `init-platform-schema` — 平台表结构（DDL）

本目录只有 **`all-in-one-for-gui.sql`**：`deploy/init-scripts/00-schema/` 中 Docker 初始化（`00-init-all.sh` Step 1、2）执行的**全部**脚本，顺序相同，合并成一个纯 SQL 文件。以平台的数据库账号执行 `psql -v ON_ERROR_STOP=1 -f` 为准（第一段 `00-schema/101` 设置执行账号在本库的默认时区）；图形客户端也能执行，但事务与出错处理各不相同，文件里几段自带 `BEGIN` / `COMMIT`，不是一个整体事务。

- 新建库的执行位置：Flowable 建表之后、seed 之前（见 `../README.md` 的四步顺序）。
- 已有库不要整文件重跑，只执行缺的段；整文件重跑过的，须接着执行 seed 包末尾的 `90-post-seed/05` 段（`00-schema/66` 会重新放宽 `sys_roles.chk_role_type`）。

## 维护

- **改表结构**：在 **`deploy/init-scripts/00-schema/`** 新增编号脚本（只增不改），登记到 `00-init-all.sh`，然后运行 `node deploy/k8s/init-data/build-all-in-one.mjs` 重新生成本文件；`--check` 检查本文件是否最新。不要手改本文件。
- 与 Docker 初始化的对象一致性：`deploy/k8s/init-data/verify-init-parity.sh`。
