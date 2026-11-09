# `init-platform-seed` — 平台种子数据

## 使用方式

- **`all-in-one-for-gui.sql`** — 唯一入口：Docker 初始化（`deploy/init-scripts/00-init-all.sh`）Step 3 的 `01-admin` 脚本与 Step 5 的 `90-post-seed` 脚本，顺序相同，去掉 k8s 不要的部分（见下）。顶部已含 `SET client_min_messages` / `SET timezone`。图形客户端与 **`psql`** 均直接执行本文件即可，例如：

```text
psql -h <host> -p <port> -U <user> -d <db> -v ON_ERROR_STOP=1 -f all-in-one-for-gui.sql
```

## 内容与 Docker 初始化的差别

- 不含演示功能单元包（08、15、16、17、18、19、24）。
- 不取 `01-admin/06-hase-organization-seed.sql`（HASE 组织演示数据）、`01-admin/11-rename-hmdc-operator-role.sql`（只改 06 建的角色）、`01-admin/13-digital-lending-action-roles.sql`（数字信贷演示功能单元的 8 个角色与 E2E 账号授权）、`90-post-seed/03-platform-access-model-dev-prepare.sql`（dev 专用：`developer` 账号与 Platform Showcase）。
- `01-admin/05` 去掉建 `developer` 账号的一段（PRD D-22、附录 A-33：k8s 环境没有 `developer`）。
- `90-post-seed/04` 的初始 System Administrator 为 `admin`（dev 为 `developer`）。切换后 `44027893` 不再是管理员。

## 已有库

**已切换的库不得整文件重跑**：前面的种子段会把旧的 SYSTEM 组、TEAM_LEAD 角色和绑定重新建出来，而切换段已执行过，不会再清理它们。已有环境只执行末尾的 `90-post-seed/04`、`05` 两段（PRD §10 第 4 步；04 在已切换的库上直接跳过，05 幂等）。文件头的 `client_min_messages = warning` 会隐藏切换输出的 NOTICE 清单，要看清单请用 `psql` 单独执行这两段。

## 维护

修改种子请在 **`deploy/init-scripts/01-admin/`** 或 **`90-post-seed/`** 新增脚本（只增不改）并登记到 `00-init-all.sh`，然后运行 `node deploy/k8s/init-data/build-all-in-one.mjs` 重新生成本文件。新登记的 `01-admin` / `90-post-seed` 脚本默认进入本文件；k8s 不该要的，在生成器的 `K8S_SEED_EXCLUDED` 里写明原因。不要手改本文件。

## 前置条件

已执行：`../init-flowable/create/flowable.postgres.all.create.sql`、`../init-platform-schema/all-in-one-for-gui.sql`；之后执行 `../init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql`（见 `../README.md`）。

## 完整演示数据

使用 **`deploy/init-scripts/init-database.ps1`**（不带 `-SkipDemoData`，执行与 Docker 初始化相同的脚本）。
