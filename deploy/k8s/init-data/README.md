# `deploy/k8s/init-data` — 离线数据库初始化包

面向**新建 PostgreSQL**（空库）：业务 DDL、种子数据、Flowable 官方脚本集中存放，可拷贝到任意环境执行。

## 新建库的执行顺序（图形客户端与 `psql` 相同）

以平台的数据库账号（各服务连接用的账号）对下面四个纯 SQL 文件依次执行 `psql -v ON_ERROR_STOP=1 -f`，这是基准做法（逐条执行、遇错即停；实测与一致性校验都用它）。文件里没有 `\i`、`\echo` 等 psql 元命令，也可以在 **pgAdmin、DBeaver、Navicat、云厂商 SQL 窗口**等工具中整文件执行，但各客户端的事务与出错处理不同：有的把整段包进一个事务，有的出错后继续往下执行；第 2 步里有几段自带 `BEGIN` / `COMMIT`，文件不是一个整体事务。中途出错时先确认执行到了哪一段，不要整文件重跑。**顺序不能换**：

| 顺序 | 文件 | 说明 |
|------|------|------|
| 1 | **`init-flowable/create/flowable.postgres.all.create.sql`** | Flowable 引擎表（官方脚本），此时版本为 `7.0.0.0` |
| 2 | **`init-platform-schema/all-in-one-for-gui.sql`** | 平台表结构：`deploy/init-scripts/00-schema` 中 Docker 初始化执行的全部脚本，顺序相同。**必须在第 1 步之后**：其中 `71-` 给 `act_hi_taskinst` 建索引、`30-`/`31-` 加宽 `act_*` 列（`ALTER TABLE IF EXISTS`，表不在时会静默跳过）。第一段 `00-schema/101` 把**执行账号在本库**的默认时区设为 `Asia/Hong_Kong`（`ALTER ROLE CURRENT_USER IN DATABASE <本库>`，普通账号即可，不需要库 owner 或超级用户；只影响该账号在本库的新会话，同库的 Activepieces 等其它账号、该账号在同实例其它库的会话都不受影响），所以必须用平台账号执行 |
| 3 | **`init-platform-seed/all-in-one-for-gui.sql`** | 种子：`01-admin` 与 `90-post-seed`（含平台访问模型切换 04、05），无演示功能单元，无 `developer` 账号，初始 System Administrator 为 `admin` |
| 4 | **`init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql`** | Flowable `7.0.0.0` → `7.2.0.2`。**新建库也必须执行**：各环境 `FLOWABLE_SCHEMA_UPDATE=false`，7.2.0 的引擎遇到 7.0.0.0 的库直接启动失败（`FlowableWrongDbException: version mismatch`）。它的后置校验要求第 2 步的加宽列已存在，所以排在第 2、3 步之后 |

执行完成后再启动 `workflow-engine`。上述顺序已在一次性库上实测：四步退出码均为 0，引擎（`FLOWABLE_SCHEMA_UPDATE=false`）正常启动、`/actuator/health` 为 UP；只执行前三步时引擎报 `version mismatch` 拒绝启动。

`deploy/init-scripts/init-database.ps1 -SkipDemoData` 用 `psql` 按同样四步执行（IKP 部署脚本 `-InitializeDatabase` 默认即此模式）；不带 `-SkipDemoData` 时它执行与 Docker 初始化相同的脚本（含演示功能单元）。

**时区**：平台默认香港时间（UTC+8，用户 2026-11-06 定）。各服务的 JVM、Hibernate `jdbc.time_zone`、Jackson 都是 `Asia/Hong_Kong`，JDBC 会话自带该时区；平台数据库账号在本库的默认时区由 `00-schema/101` 设为同一时区（账号 + 本库级，用户 2026-11-07 定；不改库级默认，同库的 Activepieces、其它 schema 的系统保持各自的设置，平台账号在同实例其它库也不受影响），让用平台账号在 psql / 图形客户端里手写的 SQL（`DEFAULT CURRENT_TIMESTAMP` 写进 `timestamp without time zone` 列）与服务写入的时间一致。**其它账号**（DBA 手工修数、报表作业等）直接写平台表时，先执行 `SET TIME ZONE 'Asia/Hong_Kong'`。已有库以平台账号执行一次 101（该账号在本库的新会话生效）和 102（新用户偏好的默认时区）。

**已有库**不得整文件重跑第 2、3 步：只执行缺的段（升级时由上线清单列出）。第 2 步的 `00-schema/66` 会把 `sys_roles.chk_role_type` 重新放宽到含 `BU_UNBOUNDED`，若整文件重跑过第 2 步，须接着再执行第 3 步末尾的 `90-post-seed/05` 段；第 3 步前面的种子段会把旧的 SYSTEM 组、TEAM_LEAD 等重建出来，已切换的库只能重跑 04、05 两段（04 会直接跳过，05 幂等）。

## 两个 all-in-one 文件由脚本生成

`init-platform-schema/all-in-one-for-gui.sql` 与 `init-platform-seed/all-in-one-for-gui.sql` **不要手改**，由 `build-all-in-one.mjs` 按 `deploy/init-scripts/00-init-all.sh` 的执行顺序从源脚本拼出：

```bash
node deploy/k8s/init-data/build-all-in-one.mjs           # 重新生成两个文件
node deploy/k8s/init-data/build-all-in-one.mjs --check   # 文件不是最新时退出码 1，并指出第一处不同
node deploy/k8s/init-data/build-all-in-one.mjs --list    # 列出 Docker 初始化的执行顺序和 k8s 对每个脚本的处理
```

- 改了 `deploy/init-scripts` 下的脚本（只增不改）或 `00-init-all.sh` 之后，重新生成并提交这两个文件。CI（`.github/workflows/init-scripts-check.yml`）执行 `--check`，没登记的 `00-schema` 脚本或没重新生成的包都会让它失败。
- 生成器同时检查：每个编号的 `00-schema` 脚本都必须被 `00-init-all.sh` 执行（曾漏掉 `93-*`）。
- 去掉 `\echo` / `\set` 行；`CREATE INDEX CONCURRENTLY` 改为 `CREATE INDEX`（有的客户端把整段脚本包进一个事务块，CONCURRENTLY 在事务块里会被拒绝；空库上没有要避开的锁）；源脚本自带的 `BEGIN` / `COMMIT` 原样保留，所以文件不是一个整体事务；源脚本最后一条语句没有分号时补上（`psql` 在文件末尾会执行它，拼接后则会接到下一段）。
- seed 不取的脚本与原因写在生成器的 `K8S_SEED_EXCLUDED` 和文件头：`01-admin/06`（HASE 组织演示数据）、`01-admin/11`（只改 06 建的角色）、`01-admin/13`（数字信贷演示功能单元的角色与 E2E 账号授权）、`90-post-seed/03`（dev 专用：`developer` 账号与 Platform Showcase）；演示功能单元包一律不取。k8s 变体两处：`01-admin/05` 去掉 `developer` 账号一段，`90-post-seed/04` 的初始 System Administrator 为 `admin`。

## 与 Docker 初始化的一致性校验

```bash
deploy/k8s/init-data/verify-init-parity.sh [--keep]     # Git Bash 或 Linux；需要 docker 与 node
```

起两个一次性 postgres 容器：一个按 dev 的方式挂载 `deploy/init-scripts` 跑 `00-init-all.sh`，一个按上表四步执行；用 `catalog-snapshot.sql` 比较两库的列、约束、索引、函数、触发器、视图、序列、扩展、注释、Flowable 版本，以及账号 / 库级设置（`pg_db_role_setting`，101 的效果）；只允许 Docker 一侧多出演示功能单元建的物理表（`meeting`、`participants`）。再用 `seed-snapshot.sql` 比较种子数据（角色、BU、BU 角色、组及成员、用户与用户角色、权限、系统配置、系统菜单等的业务键与内容）：Docker 一侧多出的只能是 k8s 有意不取的部分（`developer` 账号、HASE 组织演示数据、数字信贷演示角色、演示功能单元及其数据、Platform Showcase），初始 System Administrator 一行按 dev / k8s 各自的值核对。其余任何差异都判失败。最后把第 2 步与 04、05 段再执行一遍，确认对象不变。

## 目录说明

| 目录 / 文件 | 作用 |
|------|------|
| **`init-flowable/`** | Flowable 引擎：`create` / `upgrade` / `drop`（与引擎版本一致）。 |
| **`init-platform-schema/`** | 业务 **DDL**（生成的 `all-in-one-for-gui.sql`）。 |
| **`init-platform-seed/`** | **种子数据**（生成的 `all-in-one-for-gui.sql`）。 |
| `build-all-in-one.mjs` | 生成 / 检查两个 all-in-one 文件。 |
| `verify-init-parity.sh`、`catalog-snapshot.sql`、`seed-snapshot.sql` | Docker 初始化与 k8s 初始化的对象与种子数据比对。 |

## Flowable 版本升级（**已有库**）

**已经在跑的库**升级引擎版本，用合并脚本：

| 升级 | 文件 | 起始 → 目标 |
|------|------|------------|
| 7.0.0 → 7.2.0 | **`init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql`** | `schema.version` `7.0.0.0` → `7.2.0.2` |

该文件把三个官方增量脚本（`7.0.0→7.0.1→7.1.0→7.2.0`）按序合并，并加了前置校验（版本不是 `7.0.0.0` 就中止，防重复执行）、后置校验（版本必须为 `7.2.0.2`，且本仓库在 `00-schema/30-`、`31-` 做的 19 个加宽列必须仍是 `varchar(4000)`/`text`/`bytea`）。官方原始分步脚本同目录保留。新建库按上文第 4 步同样执行它。

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

`deploy/k8s/init-data/init-flowable` 通过 compose 以 `/flowable-sql:ro` 单独挂进 postgres 容器（不复制一份到 `init-scripts/`，避免同一份 SQL 在仓库里存两处、升版本时漏改其中一个）。k8s 新建库的 Flowable 先建表、最后执行 MIGRATE（上文第 1、4 步），与 Docker 的“建表 + 三个官方增量在前”结果一致（一致性校验覆盖了 `act_*` 表）。

**两点背景，改之前先读**：

1. **为什么必须在 Step 2 之前** —— `30-`/`31-` 用的是 `ALTER TABLE IF EXISTS`。在这一步之前，`act_*` 要等引擎启动时才建，于是这两个脚本在全新库上**静默空转**，19 个加宽列从未生效。这个顺序问题在 7.2.0 升级之前就存在，只是那时引擎自己建表（`true`）把它掩盖了。
2. **升 Flowable 版本时要改哪里** —— 把新的官方 `upgradestep` 文件放进 `upgrade/`，并追加到 `00-init-all.sh` 里的 `FLOWABLE_UPGRADE_STEPS`（`init-database.ps1` 运行时读取同一个列表）；k8s 另需新的 MIGRATE 合并脚本。忘了改也不会静默出错：引擎是 `false`，版本不符会拒绝启动。

> 另注：`99-maintenance/00-wipe-all-function-units.sql` 曾被 init 自动调用（旧 Step 4）。它会 **DROP 掉所有 `act_*`/`flw_*`**，而 initdb 只在数据目录为空时运行、Step 1–3 又不创建任何 Function Unit —— 也就是说它在初始化时无事可清，只是把刚建好的 Flowable 表删掉。现已从 init 移除；该脚本保留在 `99-maintenance/` 供**人工重播种既有库**使用。

> 已验证：`create + 三个 upgradestep` 产出的 schema 与引擎自建的 schema，核心 `act_*`/`flw_*` 表**逐列一致**（差异仅为 content/form 引擎的多余表，以及本仓库刻意加宽的 19 列）。

## 与 `deploy/init-scripts` 的关系

**权威来源**为 `deploy/init-scripts/`（只增不改）与它的执行顺序 `00-init-all.sh`；本目录两个 all-in-one 文件是生成物。`deploy/init-scripts/00-schema/00-init-all-schemas*.sql` 两个 `\i` 驱动文件只列到 `51-`，已不再维护，不要用它们初始化。
