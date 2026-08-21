# 从零开发一个 Piece 并投放到 DW（自动化组件开发手册）

> ⚠️ **2026-08-14 更新到 0.88 基线。** 本文原先针对 `activepieces/`（0.84），有三处在 0.88 上会直接卡住：
> ① `packages/cli` 已随裁剪删除，**`npm run create-piece` / `npm run build-piece` 都不存在了**（§1.1、§2、§3.1 已改写）；
> ② 上游 piece 打包体例改为 **esbuild 自包含 bundle**，沿用 0.84 的 tsc + workspace 依赖会让构建期离线烘焙 404 失败（§3.1）；
> ③ 运行时 piece 缓存版本 `v11` → **`v13`**，且依赖文件名 `archive.tgz` → **`bundle.tgz`**（§2 手工预装）。
> 目录一律 `activepieces/` → **`automation/`**。判定依据见 [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md) §6.2。
>
> 另有一条**环境约束**：公司内网 Nexus 上的 `@activepieces/pieces-framework` 只有 **0.32**，
> 而本仓库工作区是 **0.36.0**。结论是「不影响，但必须在 monorepo 内开发」——
> 理由与实测清单见 §1.1 与 §3.1。


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
| **运行时包**（runtime half） | 真正执行的 JS 代码（`run()`） | AP worker 进程 | 预装进 AP 镜像（`automation/Dockerfile` 末尾的 prewarm 步骤） |

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

自研 piece 放在 `automation/packages/pieces/community/<name>/`（core/ 目录留给上游官方核心件，
自研的一律进 community/，避免 rebase vendored 上游时冲突）。

**0.88 没有脚手架 CLI**：`packages/cli` 已随裁剪删除（它只服务上游发包流程，且是 725 个
community piece 的入口），`npm run create-piece` / `npm run build-piece` 都不存在了。
自研件改为**照抄现成件**——仓库里两个自研件就是模板，结构已按 0.88 体例验证过：

```bash
cd automation
cp -r packages/pieces/community/hash-helper packages/pieces/community/<name>
rm -rf packages/pieces/community/<name>/{dist,node_modules}
# 然后逐个改：package.json 的 name/version、src/index.ts 的 createPiece({...})、
# 删掉 src/lib/ 里抄来的 action/trigger，换成自己的。

# 【不能省】回 monorepo 根链接 workspace 包，否则编译报 TS2307：
pnpm install
```

目录结构（`hash-helper` 实例）：

```
packages/pieces/community/<name>/
├── src/index.ts          # createPiece(...) 入口
├── src/lib/              # action/trigger 每个一个文件
├── src/i18n/             # 多语言（可选）。注意在 src/ 下——顶层 i18n/ 不会被打包
├── package.json          # CommonJS，workspace:* deps（仅供本地 tsc/IDE，见 §3.1 打包说明）
└── tsconfig.json / tsconfig.lib.json
```

> **package.json 保留 `main`/`types`/`tslib`，不要加 `"type": "module"`**（所有真实件都是 CommonJS）。
> `dependencies` 里的 `@activepieces/*` 用 `workspace:*` 只是让本地类型检查与 IDE 跳转能用——
> **打包时会被丢弃**（§3.1：0.88 把这些内联进 bundle）。
> 另按 `packages/pieces/CLAUDE.md` 约定，在 `tsconfig.base.json` 加 path 映射：
> `"@activepieces/piece-<name>": ["packages/pieces/community/<name>/src/index.ts"]`
> （不加 esbuild 也能过，但 builder/web 侧解析需要它，照约定加上）。
>
> ⛔ **必须在 `automation/` 这个 monorepo 里开发，不要另起工程 `npm i @activepieces/pieces-framework`。**
> 公司内网 Nexus 上的 `@activepieces/pieces-framework` **只有 0.32**，而本仓库工作区用的是 **0.36.0**
> （`packages/pieces/framework`，从源码编译，不发包）。在 monorepo 外装依赖只会拿到 0.32，
> 于是你的件按 0.32 的 API 编译、却要跑在 0.36 的引擎上——方向上属「旧件跑新引擎」，
> framework 的 `backwardCompatabilityContextUtils` 有 V1/undefined 兼容分支，理论上能跑，
> **但我们没有验证过这条路**。在仓库里开发则完全不涉及 registry：framework 是 workspace 包，
> §3.1 的 esbuild 会把它**内联**进 bundle。
>
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
  minimumSupportedRelease: '0.82.0',      // 兼容下限，务必 ≤ 我们的 0.88.0；
                                          // 0.88 的 context V2 要求 ≥0.82.0，见 framework 的
                                          // MINIMUM_SUPPORTED_RELEASE_AFTER_LATEST_CONTEXT_VERSION
  logoUrl: '/ap-cdn/pieces/hermes/text-helper.svg',   // 自研件：本地路径，见下方说明
  authors: ['your-team'],
  categories: [PieceCategory.CORE],
  actions: [concat, replace],
  triggers: [],
});
```

> **`logoUrl` 对自研件是必做项，不是"纯外观"。** 生产完全断网（X-2/X-3），所有 CDN 图标
> 都由 `deploy/pieces/mirror-ap-cdn.mjs` 镜像到 `automation/packages/web/public/ap-cdn/`
> （保留原路径），构建期由 vite 插件 `ap-cdn-rewrite` 把源码里的
> `https://cdn.activepieces.com` 改写成同源的 `/ap-cdn`。**云端件因此不用管**。
>
> 但**自研件上游 CDN 上根本没有你的图**——写 `cdn.activepieces.com/pieces/<你的件>.svg`
> 联网也是 404（biz-calendar / hash-helper 早期就踩过，两处 404 直到 2026-07-26 才发现）。
> 所以自研件必须自带图标，两步：
>
> 1. 把 svg 放进 `automation/packages/web/public/ap-cdn/pieces/hermes/<name>.svg`
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

**dev 和生产走的是同一套白名单机制**：目录只来自 `piece_metadata` 表，且 0.88 起
`AP_PIECES_SYNC_MODE` 的**代码默认值就是 `NONE`**（HERMES-PATCH-019 把上游的 `OFFICIAL_AUTO`
翻成 fail-closed，dev/k8s 另有显式重申）；源码树里也只剩白名单那 13 个件——
0.88 的树是**物理裁剪**过的（`packages/pieces/core/` 9 个 + `community/` 4 个），
不再像 0.84 那样靠 Dockerfile 在构建期删件。
所以**不存在**「dev 从源码自动加载」这条捷径——`docker compose build` 完 DW 里照样看不到。
dev 内环 = 把 §3–§5、§7 那套走一遍，只是全部命中本地：

```bash
# 1. 打包（§3.1 的 esbuild 三步；CLI 已随 packages/cli 删除）
cd automation
#    esbuild bundle → 写自包含 package.json → npm pack → 落 hermes/tarballs/

# 2. 本地序列化元数据半（脚本详见 §3.2；文件名自动落成 metadata/piece-<name>.json）
cd ../deploy/pieces
node serialize-piece-metadata.js <name>

# 3. 登记白名单 + 生成 seed（同 §4/§5）
#    编辑 automation/hermes/pieces.json 追加 { "name": "@activepieces/piece-<name>", "version": "<ver>" }
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
docker cp automation/hermes/tarballs/activepieces-piece-<name>-<ver>.tgz \
  platform-activepieces-dev:/tmp/piece.tgz
docker exec platform-activepieces-dev sh -c '
  P=/usr/src/app/cache/v13/common/pieces/@activepieces/piece-<name>-<ver>
  mkdir -p "$P" && cp /tmp/piece.tgz "$P/bundle.tgz"
  # 0.88 的 createPiecePackageJson()：文件名是 bundle.tgz，依赖值是它的【绝对路径】、
  # 不带 file: 前缀（pnpm 把裸 .tgz 路径当本地 tarball 解析）。写歪了运行时会重装或 PieceNotFound。
  printf "{\"name\":\"@activepieces/piece-<name>-<ver>\",\"version\":\"<ver>\",\"dependencies\":{\"@activepieces/piece-<name>\":\"$P/bundle.tgz\"}}" > "$P/package.json"
  cd "$P" && pnpm install --config.node-linker=isolated --ignore-workspace && echo true > ready'
# 之后在 DW 的 flow 里试运行该 piece 的 action 即可真实执行
```

> 迭代技巧：改代码 → 重跑 §3.1 的 esbuild 打包 → 重跑上面 2–4 步 → DW 里 **Cmd+Shift+R 硬刷新**。
> builder bundle 缓存很顽固，不硬刷会吃旧 JS。改了 `run()` 逻辑还要重做手工预装
> （先删容器里的 piece 目录再装，或直接升 version 走新目录）。

dev 跑通后，§3 产出的两半物料已经就位，直接进 §6 烘镜像 → §7 投放 UAT/生产。

---

## 3. 产出「两半」离线物料（自研件与云端件的关键区别）

`deploy/pieces/fetch-pieces.sh` 是给**官方云端 piece** 用的：它 `npm pack @activepieces/piece-x@ver`
从公网 npm 拉包、`curl cloud.activepieces.com/api/v1/pieces/<name>` 拉元数据。
**你的自研件公网上不存在**，这两步都不适用——必须**本地产出这两半**：

### 3.1 运行时半（tarball / 内网包）

**0.88 换了打包体例**（2026-08-14 实测 `piece-text-helper@0.6.4` 的官方 tarball）：上游件现在是
**esbuild 打的自包含 bundle**——`main: "./src/index.js"`，单文件，`@activepieces/*` 全部内联，
`dependencies` 只留真实外部依赖（如 `jsdom`）。

⚠️ **不能沿用 0.84 的 tsc 体例**（`dist/src/**` + 把 `@activepieces/{framework,common,shared}`
pin 成具体版本）：0.88 工作区的这几个包版本（framework 0.36.0 / common 0.12.8 / shared 0.129.0）
**在 npm 上根本不存在**（实测 404）。按老体例打出来的 tarball 会让构建期的
`seed-offline-store.mjs` 去 npm 解析这些版本，直接 404 炸掉整个镜像构建。

CLI 没了，直接用 esbuild（就是 sandbox 里 `pkg-runner.build()` 用的同一个工具）：

```bash
cd automation
P=packages/pieces/community/<name>
rm -rf $P/dist && mkdir -p $P/dist/src
npx esbuild $P/src/index.ts --bundle --platform=node --format=cjs --outfile=$P/dist/src/index.js

# tarball 的 package.json：自包含，零 @activepieces 依赖
node -e '
  const fs=require("fs"), j=require("./'"$P"'/package.json");
  fs.writeFileSync("'"$P"'/dist/package.json", JSON.stringify({
    name: j.name, version: j.version, main: "./src/index.js",
    dependencies: {},                      // 有真实外部依赖就写在这里
    files: ["src/index.js", "package.json"]
  }, null, 2) + "\n");
'
(cd $P/dist && npm pack --silent) && mv $P/dist/activepieces-piece-<name>-<ver>.tgz hermes/tarballs/

# 冒烟：能 require 出来就说明 bundle 是完整的
node -e 'console.log(Object.keys(require("./'"$P"'/dist/src/index.js")))'
```

> **连带好处**：自包含 ⇒ 闭包为空 ⇒ `seed-offline-store.mjs` 对自研件成为空操作，
> 构建期一次 npm 取件都不需要。这比 0.84（还要从 npm 取 framework/shared/tslib）的气隙姿态更强。
> 若你的件确有外部依赖（比如 `jsdom`），把它写进 `dependencies` 并**从 bundle 里 external 掉**
> （`--external:jsdom`），此时 `seed-offline-store.mjs` 会把它烘进离线 store，构建机需联网一次。

`hermes/tarballs/` 既是**审计留档**，也是自研件的**安装源**：在 §4 的白名单条目里加一个
`"tarball": "activepieces-piece-<name>-<ver>.tgz"` 字段，`prewarm-pieces.sh` 就把它拷进
`pieces/<name>-<ver>/` 并以本地路径依赖安装（与 installer 的 ARCHIVE 分支同构），
构建机**不需要**能解析这个包名。声明了却找不到文件即 fail-loud。

若公司内网有 Nexus npm registry，也可以把 tarball `npm publish` 上去、白名单条目不写
`tarball` 字段，让构建机按版本号解析——两条路都只在**构建机**联网。

> 无论哪种，**联网只发生在构建机**。生产运行时永远命中镜像里预装好的 `node_modules`+`ready`，不碰任何 registry（X-3）。

> **构建机只能连 Nexus 时，需要 Nexus 里有什么**（2026-08-14 实测清单）：
>
> | 需要 | 不需要 |
> |---|---|
> | 13 个白名单件的 tarball（`prewarm-pieces.sh` 按 `registry.npmjs.org/<name>/-/<basename>-<ver>.tgz` 取） | ❌ **`@activepieces/pieces-framework`** |
> | 上游件的 4 个真实外部依赖：`@zip.js/zip.js`(file-helper)、`unpdf`(pdf)、`pg-format`(postgres)、`jsdom`(text-helper) | ❌ 任何 `@activepieces/*` 包 |
> | 根 `pnpm install --frozen-lockfile` 解析的全部第三方依赖 | ❌ 自研件的依赖（自包含，闭包为空） |
>
> **为什么 framework 不在需要之列**（三处实测）：① 锁文件里 `@activepieces/*` 全部是 `link:`（workspace），
> 零 registry 解析；② 13 个件的 tarball **没有一个**声明 `pieces-framework` 依赖——0.88 的件都是自包含 bundle；
> ③ 运行镜像里不存在 `node_modules/.pnpm/*pieces-framework*`，预装件的 `node_modules` 里只有它自己。
> 所以「内网只有 0.32」这条约束**碰不到我们的构建与运行链**，前提是坚持在 monorepo 内开发（见 §1.1）。



### 3.2 元数据半（metadata JSON）

元数据就是 AP 对你的 piece 序列化后的 actions/triggers 声明。云端件靠 curl 云 API。
自研新件**不能**问本地 AP 要——目录 DB-only，piece 还没 seed 进库，单查必 404（鸡生蛋）。
正确做法是**本地序列化**（与引擎加载方式等价：加载 `dist/src/index.js`、找到 Piece 导出、
调 `.metadata()`），仓库里已有现成脚本：

```bash
cd <repo>/deploy/pieces
node serialize-piece-metadata.js <name>     # 前置：§3.1 的 esbuild 打包已跑过（它读 dist/）
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
cp <你的图>.svg <repo>/automation/packages/web/public/ap-cdn/pieces/hermes/<name>.svg
```

`src/index.ts` 里 `logoUrl` 写同源路径 `/ap-cdn/pieces/hermes/<name>.svg`（§1.2），
metadata JSON 会带着它，生成器原样透传。漏这步的症状：**任何环境**该件都是碎图。

---

## 4. 登记进白名单

改**唯一需要手改的文件** `automation/hermes/pieces.json`（它跟着「烘进镜像」那一半走，
Docker 构建上下文只能是 `automation/`），追加一项：

```json
{ "name": "@activepieces/piece-<name>", "version": "<ver>" }
```

确认此时已就位（§3 产出的）：
- `deploy/pieces/metadata/piece-<name>.json`（元数据半，注意 `piece-` 前缀）
- `automation/hermes/tarballs/activepieces-piece-<name>-<ver>.tgz`（运行时半：留档 + 自研件安装源）
- `automation/packages/web/public/ap-cdn/pieces/hermes/<name>.svg`（图标半，§3.3）

---

## 5. 生成 seed SQL

```bash
cd deploy/pieces
node generate-metadata-seed.js        # 读 ../../automation/hermes/pieces.json + metadata/piece-<name>.json → metadata/pieces-seed.sql
```

生成器是**幂等**的（按 `name+version` 先 DELETE 后 INSERT，单事务）；`pieceType=OFFICIAL`、id 由
`sha256(name@version)` 定死，重跑不churn。若它报 `metadata/piece-<name>.json is X@Y, expected ...`——
就是两半版本没对齐，回 §3.2/§4 修。

把改动入库（tarball 也一起，都很小）：

```bash
git add automation/hermes/pieces.json deploy/pieces/metadata/piece-<name>.json \
        automation/hermes/tarballs/*.tgz deploy/pieces/metadata/pieces-seed.sql
```

---

## 6. 烘进 AP 镜像（在有网/能连 Nexus 的构建机上）

`prewarm-pieces.sh` 在 **docker build 时**按 worker `piece-installer.ts` 的**原样布局**，
用 `pnpm install --config.node-linker=isolated`（X-4：非 bun）把 `hermes/pieces.json` 里每个件装进
`cache/v13/common/pieces/<name>-<ver>/`，并写 `ready` 标记。运行时 installer 一看到 `ready`+`node_modules`
就**直接跳过、任何 registry 都不碰**——这就是气隙成立的机制。

预装是镜像的**最后一层**（`automation/Dockerfile`），所以只改白名单时，本机有构建缓存的话很快。

```bash
cd automation
docker build -t activepieces:0.88.0-ee-removed .
# 集群版（基础镜像换 nexus3 mirror，再照常 push nexus3）：
docker build --build-arg NODE_IMAGE=nexus3.<...>/workflow-station2/node:24.14.0-bullseye-slim \
  -t nexus3.<...>/workflow-station2/activepieces:0.88.0-ee-removed .
```

> 若走 §3.1 的 Nexus 方案，构建机的 npm/pnpm registry 要指向内网 Nexus，才能在 install 时解析到你的自研包。
> 升级 AP 版本时复查 `cache/v13` 路径（`LATEST_CACHE_VERSION`，见
> `automation/packages/server/worker/src/lib/cache/cache-paths.ts`）是否变。

---

## 7. 投放到目标环境（每个环境的库各做一次）

顺序不能乱（`piece_metadata` 表由 AP 首启的 TypeORM 迁移建，空库直接跑 seed 会报表不存在）：

```bash
# 1. 上镜像：docker load < ...tar.gz  或  docker pull nexus3.../activepieces:0.88.0-ee-removed
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
| 复制模板件后没跑 `pnpm install` | 编译报 TS2307 找不到 `@activepieces/pieces-framework` | monorepo 根 `pnpm install`（§1.1） |
| **在 monorepo 外建工程写 piece** | `npm i @activepieces/pieces-framework` 从内网只能拿到 **0.32**（工作区是 0.36.0），件按旧 API 编译后跑在新引擎上——兼容分支存在但**未经验证** | 一律在 `automation/` 里开发，framework 走 workspace 并由 esbuild 内联（§1.1、§3.1） |
| 以为构建机的 Nexus 需要备 `pieces-framework` | 白等镜像同步；实际它从不被解析 | 见 §3.1 的「Nexus 里需要什么」清单——要备的是 13 个件的 tarball 与 4 个外部依赖 |
| 沿用 0.84 的 tsc 体例打包（pin `@activepieces/*` 版本） | 构建期 `seed-offline-store.mjs` 去 npm 解析 → **404 炸镜像构建** | 用 §3.1 的 esbuild 自包含 bundle，`dependencies` 不留 `@activepieces/*` |
| 期望 dev「从源码加载」 | `compose build` 后 DW 仍看不到 piece | dev 同样白名单锁定（DB-only），走 §2 的 seed 内环 |
| metadata 文件存成 `<name>.json` | 生成器 ENOENT | 必须 `piece-<name>.json`；用 serialize-piece-metadata.js 自动命名 |
| i18n 放在 piece 顶层 `i18n/` | 打包后 tarball 里没有 i18n | 放 `src/i18n/`（§1.6） |
| 引入依赖 bun 的脚本/piece | 违反 X-4、构建或运行失败 | 只用 pnpm/npm；de-bun 见 vendored `automation/Dockerfile` |
| action 里外呼/提权 | 气隙生产失败、沙箱拦截 | `SANDBOX_CODE_ONLY` 下不提权、不外网；外呼件须走内网+网关放行 |
| 自研件 `logoUrl` 指向 cdn | **任何环境**都碎图（上游 CDN 没有你的件，联网也 404） | 图标放 `public/ap-cdn/pieces/hermes/`，`logoUrl` 写该同源路径（§1.2 / §3.3） |
| 自研件想复用 fetch-pieces.sh | curl 云 API / npm pack 公网都 404；问本地 AP 也 404（鸡生蛋） | §3：esbuild 出 tarball、serialize-piece-metadata.js 出 metadata |
| 生产联网拉包 | 违反 X-3 | 运行时永远命中镜像预装；联网只在构建机 |
| 想抄某个上游件的写法，却找不到目录 | `packages/pieces/community/` 只剩 4 个件 | HERMES-PATCH-013 裁掉了 690 个；源码在基线 commit 里，见 §10 |
| 取回上游件后忘了加进 `KEEP` | CI 的 vendor-trim-check 报 `FAIL: N 个未收敛的 community piece` | 按 §10 的 B 方案四步补齐，别关 job |

---

## 10. 需要读或改**上游** piece 的源码时

0.88 的树按 [D-3](REQUIREMENTS_0.88.md#d-3) 物理裁剪到 **13 个白名单件**
（`packages/pieces/core/` 9 个 + `community/` 的 `json` `postgres` + 两个自研件），
上游的 725 个 community 件与 core/ 中未列入者都不在树里。**先说不受影响的**，免得误判：

| 事情 | 受影响？ |
|---|---|
| 开发自研件（本文 §1–§8 全流程） | ❌ 不受影响。`community/<name>/` 目录还在，两个自研件都在保留清单里 |
| 把某个上游件加进白名单 | ❌ 不受影响。白名单按**版本号从 registry 解析**（`prewarm-pieces.sh` 构建期下 tarball），不需要本地源码 |
| 照着某个上游件抄写法 | ✅ 需要先取回源码 |
| 对某个白名单件打**源码级**补丁 | ✅ 需要先取回源码，且要长期保留 |

### 怎么取回被裁掉的上游源码

⚠️ **0.88 的树目前不在 git 里**（`git ls-files automation` 为 0），所以**没有**「0.88 冻结基线
commit」可供 checkout。两条路：

**A. 读 0.84 的同名件**（多数件的写法在两版之间没变，够用来抄结构）：

```bash
# 冻结基线 commit de4f6469（vendor(ap): pristine Activepieces 0.84.0 source baseline，692 个件）
# 注意路径是历史上的 activepieces/，不是 automation/
git show de4f6469:activepieces/packages/pieces/community/<name>/src/index.ts
git checkout de4f6469 -- activepieces/packages/pieces/community/<name>   # 取到 0.84 树下，只作参考
```

**B. 要 0.88 的确切实现** —— 从该件发布的 npm tarball 里读（与运行时装的是同一份）：

```bash
npm pack @activepieces/piece-<name>@<ver> && tar xzf activepieces-piece-<name>-<ver>.tgz
# 注意：0.88 的官方 tarball 是 esbuild bundle（单文件 src/index.js），能读逻辑但不是原始 TS。
# 需要原始 TS 就去上游仓库对应 tag 取（气隙外的机器上）。
```

### 取回之后必须二选一

**A. 只是参考** —— 看完把目录删掉即可，不需要动任何配置。

**B. 要长期保留**（打源码补丁、或它成了自研件的基底）——**两步**：

1. `pnpm install --lockfile-only` 重生成锁文件 —— 该件的依赖要重新进入解析。
   **锁文件必须一起提交**：镜像两个 stage 都跑 `pnpm install --frozen-lockfile`，漏了会让构建失败。
2. 若在 `tsconfig.base.json` 加了 path 映射，`node hermes/check-tsconfig-paths.mjs` 自检。

> 从前这里还要求去 `trim-vendor-pieces.mjs` 的 `KEEP` 里登记 —— 那个脚本已于 2026-08-07 随
> [D13](DECISIONS.md#d13) 删除；0.88 更是直接物理裁剪，连构建期删件那一步都没有了。
> 加自研件不再需要先向任何清单报备。

> **第 2 步不能省**：`pnpm install --frozen-lockfile` 在镜像构建阶段会因为工作区与锁文件对不上
> 而失败，而那要等到构建才暴露。CI 的第二步（`--frozen-lockfile --lockfile-only`）就是为它准备的。

---

## 附：一页速查（Cheat Sheet）

```
开发        cd automation && cp -r packages/pieces/community/hash-helper \
                                 packages/pieces/community/<name>   # 照抄模板（CLI 已删除）
            rm -rf packages/pieces/community/<name>/{dist,node_modules}
            pnpm install                                        # 【不能省】链接 workspace 依赖
            ⛔ 只在 monorepo 内开发：内网 Nexus 的 pieces-framework 只有 0.32（工作区 0.36.0）
            编辑 src/index.ts + src/lib/*.ts                    # createPiece / createAction
打包        npx esbuild … --bundle --outfile=dist/src/index.js && npm pack   # §3.1（CLI 已删除）
出物料      cp packages/.../<name>/dist/*.tgz hermes/tarballs/                    # 运行时半
            cd ../deploy/pieces && node serialize-piece-metadata.js <name>        # 元数据半
            cp <图>.svg automation/packages/web/public/ap-cdn/pieces/hermes/<name>.svg  # 图标半(自研必做)
登记        编辑 automation/hermes/pieces.json 追加 {name, version, tarball}
生成 seed   node generate-metadata-seed.js                      # → metadata/pieces-seed.sql
本地跑通    psql < pieces-seed.sql → docker restart AP → DW 搜到（试运行需 §2 手工预装）
烘镜像      cd activepieces && docker build -t activepieces:0.88.0-ee-removed .  # 构建机联网/连 Nexus
投放        上镜像 → 起 AP 建表 → psql < pieces-seed.sql → 重启 AP
验证        /api/v1/pieces 数 +1 → DW Automation 面板搜到 → 硬刷新
```
