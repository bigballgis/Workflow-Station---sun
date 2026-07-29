# 从零开发一个 Piece 并投放到 DW（自动化组件开发手册）

> 面向：要给 Workflow Station 的「自动化流程」（DW Function Unit 的 **Automation** 标签页，
> 底层是 vendored Activepieces builder）新增一个自研动作/触发器的开发者。
> 读完能独立走完 **写代码 → 本地跑通 → 产出离线物料 → 烘进镜像 → 投放环境 → 在 DW 里可用** 的全链路。
>
> 关联文档：本目录 `INTEGRATION_DESIGN.md`（九层集成 L1–L9）、`DECISIONS.md`（约束 X-3/X-4 等）、
> `deploy/pieces/README.md`（离线白名单投放，本文是它的「上游开发」补篇）。
> **要一份能直接抄的完整例子** → [`PIECE_DEVELOPMENT_EXAMPLE.md`](./PIECE_DEVELOPMENT_EXAMPLE.md)（业务日历 piece，三动作+一触发器全码）。

---

## 0. 先理解：一个 piece 由「两半」组成，DW 只信白名单

一个 piece（我们项目里叫**自动化组件**）投放到运行环境时，是**两个互相独立、必须同版本**的东西：

| 半边 | 内容 | 给谁用 | 投放载体 |
|---|---|---|---|
| **元数据**（designer half） | actions / triggers / props 的声明 | 设计器 UI（DW Automation builder） | `piece_metadata` 表（`pieces-seed.sql` 导入） |
| **运行时包**（runtime half） | 真正执行的 JS 代码（`run()`） | AP worker 进程 | 预装进 AP 镜像（`activepieces/Dockerfile` 末尾的 prewarm 步骤） |

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
# 交互式提问：piece 名（kebab）、包名 @activepieces/piece-<name>、类型选 community
# 实际生成（2026-07 实测）： packages/pieces/community/<name>/
#   ├── src/index.ts          # createPiece(...) 骨架（actions/triggers 为空）
#   ├── src/lib/              # 空目录；action/trigger 文件自己建（每个一个文件）
#   ├── src/i18n/             # 多语言（可选）。注意在 src/ 下——顶层 i18n/ 不会被打包
#   ├── package.json          # version 0.0.1、CommonJS、workspace:* deps、含 build script
#   └── tsconfig.json / tsconfig.lib.json / .eslintrc.json    #（没有 .babelrc）

# 【不能省】脚手架不装依赖——回 monorepo 根链接 workspace 包，否则编译报 TS2307：
pnpm install
```

> **package.json 只改 `version`，别删字段**。`build-piece` 走 turbo 调 package.json 的
> `build` script，删掉它构建直接报 `no dist output`；`main`/`types`/`tslib` 同样要保留，
> 也不要加 `"type": "module"`（所有真实件都是 CommonJS）。
> 另按 `packages/pieces/CLAUDE.md` 约定，在 `tsconfig.base.json` 加 path 映射：
> `"@activepieces/piece-<name>": ["packages/pieces/community/<name>/src/index.ts"]`
> （`build-piece` 不加也能过，但 builder/web 侧解析需要它，照约定加上）。

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
  logoUrl: '/ap-cdn/pieces/hermes/text-helper.svg',   // 自研件：本地路径，见下方说明
  authors: ['your-team'],
  categories: [PieceCategory.CORE],
  actions: [concat, replace],
  triggers: [],
});
```

> **`logoUrl` 对自研件是必做项，不是"纯外观"。** 生产完全断网（X-2/X-3），所有 CDN 图标
> 都由 `deploy/pieces/mirror-ap-cdn.mjs` 镜像到 `activepieces/packages/web/public/ap-cdn/`
> （保留原路径），构建期由 vite 插件 `ap-cdn-rewrite` 把源码里的
> `https://cdn.activepieces.com` 改写成同源的 `/ap-cdn`。**云端件因此不用管**。
>
> 但**自研件上游 CDN 上根本没有你的图**——写 `cdn.activepieces.com/pieces/<你的件>.svg`
> 联网也是 404（biz-calendar / hash-helper 早期就踩过，两处 404 直到 2026-07-26 才发现）。
> 所以自研件必须自带图标，两步：
>
> 1. 把 svg 放进 `activepieces/packages/web/public/ap-cdn/pieces/hermes/<name>.svg`
> 2. `logoUrl` 直接写这个同源路径 `/ap-cdn/pieces/hermes/<name>.svg`
>
> 生成器（§5）只改写 `http(s)://cdn.activepieces.com/` 开头的值，本地路径原样透传。

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

`src/i18n/` 下按语言放 JSON（如 `zh.json`；英文基准是 `translation.json`），key 对应
`displayName`/`description`。**必须放 `src/` 下**——打包只拷贝 `src/i18n/`，放顶层 `i18n/`
会被静默忽略（tarball 里不会有）。不做也能跑（回退英文）。

---

## 2. 本地跑通（开发内环，最快反馈）

**dev 和生产走的是同一套白名单机制**：dev compose 同样 `AP_PIECES_SYNC_MODE=NONE`，
目录只来自 `piece_metadata` 表；而且 vendored 镜像构建时会**删除**绝大多数 community
piece 源码（`activepieces/Dockerfile` 只保留 4 个 api 直接 import 的上游件）。
所以**不存在**「dev 从源码自动加载」这条捷径——`docker compose build` 完 DW 里照样看不到。
dev 内环 = 把 §3–§5、§7 那套走一遍，只是全部命中本地：

```bash
# 1. 编译（X-4：npx，不碰 bun）。产物在 piece 目录内的 dist/，CLI 已自动 npm pack
cd activepieces
npm run build-piece -- <name>     # → packages/pieces/community/<name>/dist/

# 2. 本地序列化元数据半（脚本详见 §3.2；文件名自动落成 metadata/piece-<name>.json）
cd ../deploy/pieces
node serialize-piece-metadata.js <name>

# 3. 登记白名单 + 生成 seed（同 §4/§5）
#    编辑 activepieces/hermes/pieces.json 追加 { "name": "@activepieces/piece-<name>", "version": "<ver>" }
node generate-metadata-seed.js

# 4. 灌 dev 库 + 让 registry 缓存失效（同 §7；不失效则「列表有、单查 404」）
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < metadata/pieces-seed.sql
docker restart platform-activepieces-dev
#    （更轻的做法：往 Redis 频道 piece-registry-invalidation publish 一条消息即可，
#      不用重启——build-and-deploy.ps1 的 Invoke-ApProvisioning 走的就是这条，
#      见 deploy/pieces/README.md「导入后必须让 registry 缓存失效」）

# 5. 在 DW 里验证：打开某 Function Unit → Automation 标签 → 新建/编辑 flow
#    左侧组件面板应能搜到你的 piece，拖进去、配 props
```

到这一步 piece 在**设计器里**已可用。要在 dev **试运行**（运行时半），worker 还需要拿到包。
正式路径是 §6 烘镜像；开发期的快路径是把 tarball 手工预装进运行中的容器
（布局与 §6 prewarm 完全一致，容器重建后失效，仅 dev 调试用）：

```bash
cd <repo>
docker cp activepieces/hermes/tarballs/activepieces-piece-<name>-<ver>.tgz \
  platform-activepieces-dev:/tmp/piece.tgz
docker exec platform-activepieces-dev sh -c '
  P=/usr/src/app/cache/v11/common/pieces/@activepieces/piece-<name>-<ver>
  mkdir -p "$P" && cp /tmp/piece.tgz "$P/archive.tgz"
  printf "{\"name\":\"@activepieces/piece-<name>-<ver>\",\"version\":\"<ver>\",\"dependencies\":{\"@activepieces/piece-<name>\":\"file:./archive.tgz\"}}" > "$P/package.json"
  cd "$P" && pnpm install --config.node-linker=isolated --ignore-workspace && echo true > ready'
# 之后在 DW 的 flow 里试运行该 piece 的 action 即可真实执行
```

> 迭代技巧：改代码 → `build-piece` → 重跑上面 2–4 步 → DW 里 **Cmd+Shift+R 硬刷新**。
> builder bundle 缓存很顽固，不硬刷会吃旧 JS。改了 `run()` 逻辑还要重做手工预装
> （先删容器里的 piece 目录再装，或直接升 version 走新目录）。

dev 跑通后，§3 产出的两半物料已经就位，直接进 §6 烘镜像 → §7 投放 UAT/生产。

---

## 3. 产出「两半」离线物料（自研件与云端件的关键区别）

`deploy/pieces/fetch-pieces.sh` 是给**官方云端 piece** 用的：它 `npm pack @activepieces/piece-x@ver`
从公网 npm 拉包、`curl cloud.activepieces.com/api/v1/pieces/<name>` 拉元数据。
**你的自研件公网上不存在**，这两步都不适用——必须**本地产出这两半**：

### 3.1 运行时半（tarball / 内网包）

```bash
cd activepieces
npm run build-piece -- <name>
# 产物在 piece 目录内（不是仓库根的 dist/）：packages/pieces/community/<name>/dist/
# CLI 已在 dist/ 里自动跑过 npm pack（workspace:* 依赖也已 pin 成具体版本），直接留档：
cp packages/pieces/community/<name>/dist/activepieces-piece-<name>-<ver>.tgz \
   hermes/tarballs/
```

`hermes/tarballs/` 既是**审计留档**，也是自研件的**安装源**：在 §4 的白名单条目里加一个
`"tarball": "activepieces-piece-<name>-<ver>.tgz"` 字段，`prewarm-pieces.sh` 就把它拷进
`pieces/<name>-<ver>/` 并以本地路径依赖安装（与 installer 的 ARCHIVE 分支同构），
构建机**不需要**能解析这个包名。声明了却找不到文件即 fail-loud。

若公司内网有 Nexus npm registry，也可以把 tarball `npm publish` 上去、白名单条目不写
`tarball` 字段，让构建机按版本号解析——两条路都只在**构建机**联网。

> 无论哪种，**联网只发生在构建机**。生产运行时永远命中镜像里预装好的 `node_modules`+`ready`，不碰任何 registry（X-3）。

### 3.2 元数据半（metadata JSON）

元数据就是 AP 对你的 piece 序列化后的 actions/triggers 声明。云端件靠 curl 云 API。
自研新件**不能**问本地 AP 要——目录 DB-only，piece 还没 seed 进库，单查必 404（鸡生蛋）。
正确做法是**本地序列化**（与引擎加载方式等价：加载 `dist/src/index.js`、找到 Piece 导出、
调 `.metadata()`），仓库里已有现成脚本：

```bash
cd <repo>/deploy/pieces
node serialize-piece-metadata.js <name>     # 前置：§3.1 的 build-piece 已跑过
# → 写出 metadata/piece-<name>.json，并打印可直接抄进 pieces.json 的条目
```

> **文件名必须是 `piece-<name>.json`**（生成器按包名 `split('/')[1]` 找文件），
> 手工另存为 `<name>.json` 会 ENOENT。脚本已自动按此命名。

产出的 JSON 顶层字段（generate-metadata-seed.js 会读这些）：
`name, version, displayName, logoUrl, description, minimumSupportedRelease, maximumSupportedRelease,
actions, triggers, auth, categories, authors, i18n`。**`name`/`version` 必须与 pieces.json 里逐字一致**，
否则 §5 的生成器会报错拒绝（脚本直接从 dist/package.json 取，天然一致）。

### 3.3 图标半（自研件必做）

`logoUrl` 若指向 `cdn.activepieces.com`，§5 生成器会改写成 `/ap-cdn/<原路径>`，而该文件由
`mirror-ap-cdn.mjs` 从上游镜像而来——**上游没有自研件的图，镜像必然抓不到**。所以自研件
自己把图标放进镜像目录：

```bash
cp <你的图>.svg <repo>/activepieces/packages/web/public/ap-cdn/pieces/hermes/<name>.svg
```

`src/index.ts` 里 `logoUrl` 写同源路径 `/ap-cdn/pieces/hermes/<name>.svg`（§1.2），
metadata JSON 会带着它，生成器原样透传。漏这步的症状：**任何环境**该件都是碎图。

---

## 4. 登记进白名单

改**唯一需要手改的文件** `activepieces/hermes/pieces.json`（它跟着「烘进镜像」那一半走，
Docker 构建上下文只能是 `activepieces/`），追加一项：

```json
{ "name": "@activepieces/piece-<name>", "version": "<ver>" }
```

确认此时已就位（§3 产出的）：
- `deploy/pieces/metadata/piece-<name>.json`（元数据半，注意 `piece-` 前缀）
- `activepieces/hermes/tarballs/activepieces-piece-<name>-<ver>.tgz`（运行时半：留档 + 自研件安装源）
- `activepieces/packages/web/public/ap-cdn/pieces/hermes/<name>.svg`（图标半，§3.3）

---

## 5. 生成 seed SQL

```bash
cd deploy/pieces
node generate-metadata-seed.js        # 读 ../../activepieces/hermes/pieces.json + metadata/piece-<name>.json → metadata/pieces-seed.sql
```

生成器是**幂等**的（按 `name+version` 先 DELETE 后 INSERT，单事务）；`pieceType=OFFICIAL`、id 由
`sha256(name@version)` 定死，重跑不churn。若它报 `metadata/piece-<name>.json is X@Y, expected ...`——
就是两半版本没对齐，回 §3.2/§4 修。

把改动入库（tarball 也一起，都很小）：

```bash
git add activepieces/hermes/pieces.json deploy/pieces/metadata/piece-<name>.json \
        activepieces/hermes/tarballs/*.tgz deploy/pieces/metadata/pieces-seed.sql
```

---

## 6. 烘进 AP 镜像（在有网/能连 Nexus 的构建机上）

`prewarm-pieces.sh` 在 **docker build 时**按 worker `piece-installer.ts` 的**原样布局**，
用 `pnpm install --config.node-linker=isolated`（X-4：非 bun）把 `hermes/pieces.json` 里每个件装进
`cache/v11/common/pieces/<name>-<ver>/`，并写 `ready` 标记。运行时 installer 一看到 `ready`+`node_modules`
就**直接跳过、任何 registry 都不碰**——这就是气隙成立的机制。

预装是镜像的**最后一层**（`activepieces/Dockerfile`），所以只改白名单时，本机有构建缓存的话很快。

```bash
cd activepieces
docker build -t activepieces:0.84.0-ee-removed .
# 集群版（基础镜像换 nexus3 mirror，再照常 push nexus3）：
docker build --build-arg NODE_IMAGE=nexus3.<...>/workflow-station2/node:24.14.0-bullseye-slim \
  -t nexus3.<...>/workflow-station2/activepieces:0.84.0-ee-removed .
```

> 若走 §3.1 的 Nexus 方案，构建机的 npm/pnpm registry 要指向内网 Nexus，才能在 install 时解析到你的自研包。
> 升级 AP 版本时复查 `cache/v11` 路径（`LATEST_CACHE_VERSION`，见
> `activepieces/packages/server/worker/src/lib/cache/cache-paths.ts`）是否变。

---

## 7. 投放到目标环境（每个环境的库各做一次）

顺序不能乱（`piece_metadata` 表由 AP 首启的 TypeORM 迁移建，空库直接跑 seed 会报表不存在）：

```bash
# 1. 上镜像：docker load < ...tar.gz  或  docker pull nexus3.../activepieces:0.84.0-ee-removed
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
| 两半版本不一致 | 运行时 `PieceNotFound` | hermes/pieces.json / metadata JSON / 预装目录三处 `version` 逐字对齐 |
| 跑完 seed 没重启 AP | 列表有、单查 404 | §7 第 4 步重启 AP |
| 空库直接跑 seed | `relation "piece_metadata" does not exist` | 先起一次 AP 建表，再 seed |
| create-piece 后没跑 `pnpm install` | 编译报 TS2307 找不到 `@activepieces/pieces-framework` | monorepo 根 `pnpm install`（§1.1） |
| 删/精简了 package.json 的 `build` script 等字段 | `build-piece` 报 `no dist output` | 保留脚手架生成的全部字段，只改 `version`（§1.1） |
| 期望 dev「从源码加载」 | `compose build` 后 DW 仍看不到 piece | dev 同样白名单锁定（DB-only），走 §2 的 seed 内环 |
| metadata 文件存成 `<name>.json` | 生成器 ENOENT | 必须 `piece-<name>.json`；用 serialize-piece-metadata.js 自动命名 |
| i18n 放在 piece 顶层 `i18n/` | 打包后 tarball 里没有 i18n | 放 `src/i18n/`（§1.6） |
| 引入依赖 bun 的脚本/piece | 违反 X-4、构建或运行失败 | 只用 pnpm/npm；de-bun 见 vendored `activepieces/Dockerfile` |
| action 里外呼/提权 | 气隙生产失败、沙箱拦截 | `SANDBOX_CODE_ONLY` 下不提权、不外网；外呼件须走内网+网关放行 |
| 自研件 `logoUrl` 指向 cdn | **任何环境**都碎图（上游 CDN 没有你的件，联网也 404） | 图标放 `public/ap-cdn/pieces/hermes/`，`logoUrl` 写该同源路径（§1.2 / §3.3） |
| 自研件想复用 fetch-pieces.sh | curl 云 API / npm pack 公网都 404；问本地 AP 也 404（鸡生蛋） | §3：build-piece 出 tarball、serialize-piece-metadata.js 出 metadata |
| 生产联网拉包 | 违反 X-3 | 运行时永远命中镜像预装；联网只在构建机 |
| 想抄某个上游件的写法，却找不到目录 | `packages/pieces/community/` 只剩 4 个件 | HERMES-PATCH-013 裁掉了 690 个；源码在基线 commit 里，见 §10 |
| 取回上游件后忘了加进 `KEEP` | CI 的 vendor-trim-check 报 `FAIL: N 个未收敛的 community piece` | 按 §10 的 B 方案四步补齐，别关 job |

---

## 10. 需要读或改**上游** piece 的源码时

[HERMES-PATCH-013](HERMES_PATCHES.md#清单) 把 `packages/pieces/community/` 从 694 个件收敛到 4 个
（`biz-calendar` / `hash-helper` / `json` / `postgres`）。**先说不受影响的**，免得误判：

| 事情 | 受影响？ |
|---|---|
| 开发自研件（本文 §1–§8 全流程） | ❌ 不受影响。`community/<name>/` 目录还在，样例件 `biz-calendar` 在保留清单里 |
| 把某个上游件加进白名单 | ❌ 不受影响。白名单按**版本号从 registry 解析**，不需要本地源码 |
| 照着某个上游件抄写法 | ✅ 需要先取回源码 |
| 对某个白名单件打**源码级**补丁 | ✅ 需要先取回源码，且要长期保留 |

**源码没有丢，都在 git 里。** 冻结基线 commit `de4f6469`（`vendor(ap): pristine Activepieces
0.84.0 source baseline`）保有全部 692 个件：

```bash
# 只想读一眼（不落盘）
git show de4f6469:activepieces/packages/pieces/community/<name>/src/index.ts

# 取回整个件到工作区
git checkout de4f6469 -- activepieces/packages/pieces/community/<name>
```

### 取回之后必须二选一

**A. 只是参考** —— 看完把目录删掉即可，不需要动任何配置。

**B. 要长期保留**（打源码补丁、或它成了自研件的基底）——**四步缺一不可**：

1. 在 [`activepieces/hermes/trim-vendor-pieces.mjs`](../../activepieces/hermes/trim-vendor-pieces.mjs)
   的 `KEEP` 里加一条，**写明理由**（那个对象里每条都有理由，没理由的不许加）；
2. `pnpm install --lockfile-only` 重生成锁文件 —— 该件的依赖要重新进入解析；
3. 若做了源码修改，按 [HERMES_PATCHES.md](HERMES_PATCHES.md) 的规矩取下一个补丁号并登记；
4. `node hermes/trim-vendor-pieces.mjs --check` 自检。

> **忘了第 1 步会被 CI 挡下**，这是设计好的：`.github/workflows/vendor-trim-check.yml` 在每次
> `activepieces/**` 改动上跑 `--check`，未登记的件会报
> `FAIL: N 个未收敛的 community piece`。别去关掉这个 job —— 它的作用正是防止 690 个件
> 连同它们的第三方 SDK 悄悄回潮（这是最初 `pnpm install` 在公司内网装不下来的根因）。

> **第 2 步不能省**：`pnpm install --frozen-lockfile` 在镜像构建阶段会因为工作区与锁文件对不上
> 而失败，而那要等到构建才暴露。CI 的第二步（`--frozen-lockfile --lockfile-only`）就是为它准备的。

---

## 附：一页速查（Cheat Sheet）

```
开发        cd activepieces && npm run create-piece            # 骨架 → community/<name>/
            pnpm install                                        # 【不能省】链接 workspace 依赖
            编辑 src/index.ts + src/lib/*.ts                    # createPiece / createAction
构建        npm run build-piece -- <name>          # → packages/pieces/community/<name>/dist/（已自动 pack）
出物料      cp packages/.../<name>/dist/*.tgz hermes/tarballs/                    # 运行时半
            cd ../deploy/pieces && node serialize-piece-metadata.js <name>        # 元数据半
            cp <图>.svg activepieces/packages/web/public/ap-cdn/pieces/hermes/<name>.svg  # 图标半(自研必做)
登记        编辑 activepieces/hermes/pieces.json 追加 {name, version, tarball}
生成 seed   node generate-metadata-seed.js                      # → metadata/pieces-seed.sql
本地跑通    psql < pieces-seed.sql → docker restart AP → DW 搜到（试运行需 §2 手工预装）
烘镜像      cd activepieces && docker build -t activepieces:0.84.0-ee-removed .  # 构建机联网/连 Nexus
投放        上镜像 → 起 AP 建表 → psql < pieces-seed.sql → 重启 AP
验证        /api/v1/pieces 数 +1 → DW Automation 面板搜到 → 硬刷新
```
