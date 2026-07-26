# 从零开发一个 Piece 并投放到 DW（自动化组件开发手册）

> 面向：要给 Workflow Station 的「自动化流程」（DW Function Unit 的 **Automation** 标签页，
> 底层是 vendored Activepieces builder）新增一个自研动作/触发器的开发者。
> 读完能独立走完 **写代码 → 本地跑通 → 产出离线物料 → 烘进镜像 → 投放环境 → 在 DW 里可用** 的全链路。
>
> 关联文档：本目录 `INTEGRATION_DESIGN.md`（九层集成 L1–L9）、`DECISIONS.md`（约束 X-3/X-4 等）、
> `deploy/pieces/README.md`（离线白名单投放，本文是它的「上游开发」补篇）。

---

## 0. 先理解：一个 piece 由「两半」组成，DW 只信白名单

一个 piece（我们项目里叫**自动化组件**）投放到运行环境时，是**两个互相独立、必须同版本**的东西：

| 半边 | 内容 | 给谁用 | 投放载体 |
|---|---|---|---|
| **元数据**（designer half） | actions / triggers / props 的声明 | 设计器 UI（DW Automation builder） | `piece_metadata` 表（`pieces-seed.sql` 导入） |
| **运行时包**（runtime half） | 真正执行的 JS 代码（`run()`） | AP worker 进程 | 预装进 AP 镜像（`deploy/pieces/Dockerfile`） |

两条铁律，先记住：

1. **白名单即真相**。生产用 `AP_PIECES_SYNC_MODE=NONE`，AP **只**暴露 `piece_metadata` 表里有的 piece。
   你的 piece 不写进这张表，DW 里就根本看不到——写多少代码都没用。
2. **两半版本必须逐字一致**。flow 里引用的是 `name@version`，worker 运行时按这个**精确版本**去
   预装目录找代码。元数据写了 `1.0.1`、镜像里预装的是 `1.0.0`，运行时就 `PieceNotFound`。

还有两条**硬约束**贯穿全文（来自 `DECISIONS.md`）：

- **X-3 气隙**：生产集群禁止外网。运行时**绝不**联网拉包（靠镜像预装 + `ready` 标记，见 §6）。
  所有需要联网的动作（build、pnpm install）只在**有网的构建机**上发生。
- **X-4 全环境禁 bun**：本仓库 vendored AP 已 de-bun，piece 安装走 **pnpm（isolated linker）**。
  你自己开发时也**不要**引入依赖 bun 的脚本。

---

## 1. 开发 piece（写代码）

### 1.1 脚手架

自研 piece 放在 `activepieces/packages/pieces/community/<name>/`（core/ 目录留给上游官方核心件，
自研的一律进 community/，避免 rebase vendored 上游时冲突）。

用 CLI 生成骨架（在 `activepieces/` 目录下；X-4：用 npx，不碰 bun）：

```bash
cd activepieces
npm run create-piece
# 交互式提问：piece 显示名、包名 @activepieces/piece-<name>、作者等
# 生成： packages/pieces/community/<name>/
#   ├── src/index.ts          # createPiece(...) 汇总入口
#   ├── src/lib/actions/      # 每个 action 一个文件
#   ├── src/lib/triggers/     # 每个 trigger 一个文件
#   ├── i18n/                 # 多语言（可选）
#   ├── package.json          # deps 已填 @activepieces/pieces-framework 等 workspace:*
#   ├── tsconfig.lib.json
#   └── .babelrc
```

> 命名：包名统一 `@activepieces/piece-<kebab-name>`，与现有件对齐（builder 目录、预装目录都按它推导）。
> **不要**在包名里放公司敏感字样——它会出现在离线 tarball 与镜像层里。

### 1.2 入口 `src/index.ts`

`createPiece(...)` 把所有 action/trigger 汇总起来。真实例子（core/text-helper，已裁剪）：

```ts
import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { concat } from './lib/actions/concat';
import { replace } from './lib/actions/replace';

export const textHelper = createPiece({
  displayName: 'Text Helper',
  description: 'Tools for text processing',
  auth: PieceAuth.None(),                 // 无鉴权；下节讲有鉴权的写法
  minimumSupportedRelease: '0.36.1',      // 兼容下限，务必 ≤ 我们的 0.84.0
  logoUrl: 'https://cdn.activepieces.com/pieces/new-core/text-helper.svg',
  authors: ['your-team'],
  categories: [PieceCategory.CORE],
  actions: [concat, replace],
  triggers: [],
});
```

> `logoUrl`：气隙环境访问不到 `cdn.activepieces.com`，图标会裂——**纯外观问题**，不影响功能。
> 想要离线也有图标，把 svg 内联成 data-URI 或指向 DW 自有静态资源。

### 1.3 写一个 Action

一个 action = 一个 `createAction`，声明 `props`（自动渲染成 builder 里的表单）+ `run()`（真正逻辑）。
真实例子（text-helper 的 replace，已裁剪）：

```ts
import { Property, createAction } from '@activepieces/pieces-framework';

export const replace = createAction({
  name: 'replace',                 // 稳定机器名，进 flow JSON，改名 = breaking
  displayName: 'Replace',
  description: 'Replaces all instances of a word/phrase in text.',
  props: {
    text: Property.ShortText({ displayName: 'Text', required: true }),
    searchValue: Property.ShortText({ displayName: 'Search Value', required: true }),
    replaceValue: Property.ShortText({ displayName: 'Replace Value', required: false }),
  },
  run: async (ctx) => {
    // ctx.propsValue 已按 props 强类型；返回值即该步的输出，供后续步骤引用
    const expr = RegExp(ctx.propsValue.searchValue, 'g');
    return ctx.propsValue.text.replaceAll(expr, ctx.propsValue.replaceValue || '');
  },
});
```

常用 `Property.*`：`ShortText / LongText / Number / Checkbox / StaticDropdown / Dropdown（动态，可用 auth 拉选项）/
Json / Array / Object / File`。`ctx` 上还有 `ctx.store`（持久 KV）、`ctx.files`（大对象落盘）、`ctx.auth`（见下）。

> **X-3/安全边界**：run() 跑在 `SANDBOX_CODE_ONLY` 沙箱里（无 SYS_ADMIN、不提权）。
> 别在 action 里 spawn 子进程、写系统路径、或假设能访问外网——气隙生产会直接失败。
> 需要外呼的动作（调 SaaS API）本质上违反气隙政策，除非目标在内网且已在网关放行。

### 1.4 写一个 Trigger（可选）

触发器决定 flow 怎么被启动。两种模型：

- **`WEBHOOK`**：AP 暴露一个 URL，外部 POST 即触发（我们的 BPMN service-task 同步回包就靠 `piece-webhook`）。
- **`POLLING`**：AP 按 `schedule` 周期性调 `run()` 拉增量。

```ts
import { createTrigger, TriggerStrategy, Property } from '@activepieces/pieces-framework';

export const newRow = createTrigger({
  name: 'new_row',
  displayName: 'New Row',
  description: 'Fires when a new row appears.',
  type: TriggerStrategy.POLLING,
  props: { table: Property.ShortText({ displayName: 'Table', required: true }) },
  sampleData: {},                                   // builder 里给用户看的示例输出
  onEnable: async (ctx) => { /* 注册/存 cursor */ },
  onDisable: async (ctx) => { /* 清理 */ },
  run: async (ctx) => { return []; },               // 返回新条目数组
});
```

### 1.5 鉴权（有需要才写）

```ts
// index.ts 里：
auth: PieceAuth.SecretText({ displayName: 'API Key', required: true }),
// 或 PieceAuth.CustomAuth({ props: { host: ..., token: ... } })
```

action 里通过 `ctx.auth` 拿到；builder 会引导用户在「连接」里填。连接凭据由 AP 加密存库，
**不要**把密钥硬编码进代码或 i18n。

### 1.6 i18n（可选）

`i18n/` 下按语言放 JSON，key 对应 `displayName`/`description`。不做也能跑（回退英文）。

---

## 2. 本地跑通（开发内环，最快反馈）

目的：不碰离线投放那套，先在 dev 把 piece 调对。dev 环境有外网、AP 从源码加载 piece。

```bash
# 1. 编译你的 piece（X-4：npx，不碰 bun）
cd activepieces
npm run build-piece -- <name>          # 产物在 activepieces/dist/packages/pieces/<name>/

# 2. 让 dev AP 加载它（dev 非白名单锁定，可从源码/本地包解析）
cd deploy/environments/dev
docker compose build activepieces && docker compose up -d activepieces
docker restart platform-activepieces-dev        # 刷新进程内 piece 缓存（见 §7 缓存坑）

# 3. 在 DW 里验证：dev 打开某 Function Unit → Automation 标签 → 新建/编辑 flow
#    左侧组件面板应能搜到你的 piece，拖进去、配 props、试运行
```

> 迭代技巧：改代码 → `build-piece` → `docker restart platform-activepieces-dev` → DW 里 **Cmd+Shift+R 硬刷新**。
> builder bundle 缓存很顽固，不硬刷会吃旧 JS。

本地跑通后，再进入 §3 起的「离线投放」正式流程——这才是能进 UAT/生产的路径。

---

## 3. 产出「两半」离线物料（自研件与云端件的关键区别）

`deploy/pieces/fetch-pieces.sh` 是给**官方云端 piece** 用的：它 `npm pack @activepieces/piece-x@ver`
从公网 npm 拉包、`curl cloud.activepieces.com/api/v1/pieces/<name>` 拉元数据。
**你的自研件公网上不存在**，这两步都不适用——必须**本地产出这两半**：

### 3.1 运行时半（tarball / 内网包）

```bash
cd activepieces
npm run build-piece -- <name>                 # → dist/packages/pieces/<name>/
cd dist/packages/pieces/<name>
npm pack                                       # → activepieces-piece-<name>-<ver>.tgz
# 放进白名单物料区留档：
cp *.tgz <repo>/deploy/pieces/tarballs/
```

`tarballs/` 是**审计留档 + 内网发布源**（README 原话）。真正让镜像装上你的包，二选一：

- **推荐 · 内网 Nexus npm**：把这个 tarball `npm publish` 到公司内网 Nexus npm registry。
  之后 §6 的镜像构建（`prewarm-pieces.sh` 跑 `pnpm install`）在**构建机**上就能从 Nexus 解析到它，
  完全复用现有流程、零改脚本。
- **备选 · 本地 tarball 装入**：若暂时没有内网 Nexus，改 `prewarm-pieces.sh` 让对应 piece 从
  `pieces/<name>-<ver>/` 的 `file:` 依赖（指向 tarball）安装。属于脚本改动，需评审——优先走 Nexus。

> 无论哪种，**联网只发生在构建机**。生产运行时永远命中镜像里预装好的 `node_modules`+`ready`，不碰任何 registry（X-3）。

### 3.2 元数据半（metadata JSON）

元数据就是 AP 对你的 piece 序列化后的 actions/triggers 声明。云端件靠 curl 云 API，自研件**指向本地 AP 抓同一个接口**：

```bash
# §2 里 dev AP 已加载你的 piece。直接问它要序列化元数据（等价于 fetch-pieces.sh 的 curl，只是换成本地）：
docker exec platform-activepieces-dev node -e "
  require('http').get('http://127.0.0.1:80/api/v1/pieces/@activepieces/piece-<name>?version=<ver>',
    r => { let d=''; r.on('data',c=>d+=c); r.on('end',()=>process.stdout.write(d)); })
" > <repo>/deploy/pieces/metadata/<name>.json
```

产出的 JSON 顶层字段（generate-metadata-seed.js 会读这些）：
`name, version, displayName, logoUrl, description, minimumSupportedRelease, maximumSupportedRelease,
actions, triggers, auth, categories, i18n`。**`name`/`version` 必须与 pieces.json 里逐字一致**，
否则 §5 的生成器会报错拒绝。

---

## 4. 登记进白名单

改**唯一需要手改的文件** `deploy/pieces/pieces.json`，追加一项：

```json
{ "name": "@activepieces/piece-<name>", "version": "<ver>" }
```

确认此时 `deploy/pieces/` 下已就位（§3 产出的）：
- `metadata/<name>.json`（元数据半）
- `tarballs/activepieces-piece-<name>-<ver>.tgz`（运行时半留档）

---

## 5. 生成 seed SQL

```bash
cd deploy/pieces
node generate-metadata-seed.js        # 读 pieces.json + metadata/<name>.json → metadata/pieces-seed.sql
```

生成器是**幂等**的（按 `name+version` 先 DELETE 后 INSERT，单事务）；`pieceType=OFFICIAL`、id 由
`sha256(name@version)` 定死，重跑不churn。若它报 `metadata/<name>.json is X@Y, expected ...`——
就是两半版本没对齐，回 §3.2/§4 修。

把改动入库（tarball 也一起，都很小）：

```bash
git add deploy/pieces/pieces.json deploy/pieces/metadata/<name>.json \
        deploy/pieces/tarballs/*.tgz deploy/pieces/metadata/pieces-seed.sql
```

---

## 6. 烘进 AP 镜像（在有网/能连 Nexus 的构建机上）

`prewarm-pieces.sh` 在 **docker build 时**按 worker `piece-installer.ts` 的**原样布局**，
用 `pnpm install --config.node-linker=isolated`（X-4：非 bun）把 pieces.json 里每个件装进
`cache/v11/common/pieces/<name>-<ver>/`，并写 `ready` 标记。运行时 installer 一看到 `ready`+`node_modules`
就**直接跳过、任何 registry 都不碰**——这就是气隙成立的机制。

```bash
cd deploy/pieces
docker build -t activepieces:0.84.0-pieces .
# 集群版（基础镜像换 nexus3 mirror，再照常 push nexus3）：
docker build --build-arg BASE_IMAGE=nexus3.<...>/workflow-station2/activepieces:0.84.0 \
  -t nexus3.<...>/workflow-station2/activepieces:0.84.0-pieces .
```

> 若走 §3.1 的 Nexus 方案，构建机的 npm/pnpm registry 要指向内网 Nexus，才能在 install 时解析到你的自研包。
> 升级 AP 版本时复查 `cache/v11` 路径（`LATEST_CACHE_VERSION`，见
> `activepieces/packages/server/worker/src/lib/cache/cache-paths.ts`）是否变。

---

## 7. 投放到目标环境（每个环境的库各做一次）

顺序不能乱（`piece_metadata` 表由 AP 首启的 TypeORM 迁移建，空库直接跑 seed 会报表不存在）：

```bash
# 1. 上镜像：docker load < ...tar.gz  或  docker pull nexus3.../activepieces:0.84.0-pieces
#    把 deploy/k8s/activepieces.yaml 的 image: 换成新 tag（dev compose 换 image:）

# 2. 起一次 AP 建表（首启自动迁移；等 healthcheck 变绿）
#    compose: docker compose up -d activepieces   /   k8s: kubectl apply

# 3. 对该环境共享库跑 seed（文件在仓库里，气隙可用）
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < deploy/pieces/metadata/pieces-seed.sql
#    uat/prod：由 DBA / 发布流程对共享库执行同一份 SQL

# 4. 【不能省】重启 AP —— 否则新 piece 单查 404
#    compose: docker restart platform-activepieces-dev
#    k8s:     kubectl rollout restart deployment/activepieces
```

> **为什么必须重启**：AP 把 piece registry 缓存在**进程内存**（`piece-cache.ts` 的 `cachedRegistry`），
> 只在走 AP 自身 API 装 piece 时经 Redis pubsub 失效；直接 psql 写表**不会**触发。
> 典型症状：列表 `/api/v1/pieces` 里**有**你的件（列表直查 DB）、但单查 `/api/v1/pieces/<name>`
> **404 `piece_metadata_not_found`**（单查走缓存）。重启即消。

---

## 8. 在 DW 里验证「可用」

DW 的 Automation builder 经 Kong `/api/ap` 前缀（L2）访问 AP。逐层确认：

```bash
# a. AP 层：piece 总数应含你的件（应比之前 +1）
docker exec <ap容器> node -e "require('http').get('http://127.0.0.1:80/api/v1/pieces',\
  r=>{let d='';r.on('data',c=>d+=c);r.on('end',()=>console.log(JSON.parse(d).length))})"

# b. 单查（验证缓存已刷新，不该 404）
curl -s http://localhost:3000/api/ap/v1/pieces/@activepieces/piece-<name> | head -c 200
```

- **c. DW UI**：打开任一 Function Unit → **Automation** 标签 → 新建/编辑 flow →
  左侧组件面板搜你的 piece 显示名 → 能拖入、配 props、试运行即**投放成功**。
- 浏览器记得 **Cmd+Shift+R 硬刷新**（吃旧 builder JS 缓存是最常见的「怎么还没出现」假象）。

---

## 9. 约束与常见坑（务必对照）

| 坑 | 现象 | 处置 |
|---|---|---|
| 两半版本不一致 | 运行时 `PieceNotFound` | pieces.json / metadata JSON / 预装目录三处 `version` 逐字对齐 |
| 跑完 seed 没重启 AP | 列表有、单查 404 | §7 第 4 步重启 AP |
| 空库直接跑 seed | `relation "piece_metadata" does not exist` | 先起一次 AP 建表，再 seed |
| 引入依赖 bun 的脚本/piece | 违反 X-4、构建或运行失败 | 只用 pnpm/npm；de-bun 见 vendored `activepieces/Dockerfile` |
| action 里外呼/提权 | 气隙生产失败、沙箱拦截 | `SANDBOX_CODE_ONLY` 下不提权、不外网；外呼件须走内网+网关放行 |
| `logoUrl` 指向 cdn | 离线图标裂 | 纯外观；要离线图标就内联 data-URI |
| 自研件想复用 fetch-pieces.sh | curl 云 API / npm pack 公网都 404 | §3：本地 build+pack 出 tarball、本地 AP 抓 metadata |
| 生产联网拉包 | 违反 X-3 | 运行时永远命中镜像预装；联网只在构建机 |

---

## 附：一页速查（Cheat Sheet）

```
开发        cd activepieces && npm run create-piece            # 骨架 → community/<name>/
            编辑 src/index.ts + src/lib/actions|triggers/*.ts   # createPiece / createAction
构建        npm run build-piece -- <name>                       # → dist/.../<name>/
本地跑通    compose build+up+restart activepieces → DW Automation 调试
出物料      cd dist/.../<name> && npm pack → cp *.tgz deploy/pieces/tarballs/     # 运行时半
            docker exec ap ... /api/v1/pieces/<name> > deploy/pieces/metadata/<name>.json  # 元数据半
登记        编辑 deploy/pieces/pieces.json 追加 {name, version}
生成 seed   cd deploy/pieces && node generate-metadata-seed.js  # → pieces-seed.sql
烘镜像      docker build -t activepieces:0.84.0-pieces .         # 构建机联网/连 Nexus
投放        上镜像 → 起 AP 建表 → psql < pieces-seed.sql → 重启 AP
验证        /api/v1/pieces 数 +1 → DW Automation 面板搜到 → 硬刷新
```
