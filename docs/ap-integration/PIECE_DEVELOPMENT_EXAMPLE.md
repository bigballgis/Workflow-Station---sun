# Piece 开发示例：业务日历（`piece-biz-calendar`）

> ⚠️ **2026-08-14 更新到 0.88 基线。** 本文原先针对 `activepieces/`（0.84），照 0.84 抄会在三处卡住：
> ① `packages/cli` 已删除，**`npm run create-piece` / `npm run build-piece` 都不存在了**——脚手架改为
> 「照抄现成件」，打包改为直接用 esbuild（§1、§7）；
> ② 打包体例改成 **esbuild 自包含 bundle**（`main: "./src/index.js"`、`dependencies: {}`），
> 沿用 0.84 的 tsc 体例（pin `@activepieces/*` 版本）会让构建期 `seed-offline-store.mjs` 去 npm
> 解析根本不存在的版本 → **404 炸掉整个镜像构建**（§2、§7）；
> ③ `minimumSupportedRelease` 下限抬到 **`0.82.0`**（0.88 的 context V2 要求，§5）。
> 另：目录一律 `activepieces/` → **`automation/`**；运行时缓存 `cache/v11` → **`cache/v13`**、
> 手工预装的依赖文件名 `archive.tgz` → **`bundle.tgz`**（§7）。
> 判定依据见 [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md) §6.2；流程真源见 how-to。

> 配套 [`PIECE_DEVELOPMENT_HOWTO.md`](./PIECE_DEVELOPMENT_HOWTO.md)（讲**流程**）。本文是一个**能直接抄**的
> 完整自研 piece：三个动作 + 一个轮询触发器，每个文件给全码。
>
> **为什么选这个例子**：业务日历（算工作日 / SLA 到期日）是 BPMN 流程平台的高频刚需，且**纯本地计算、
> 零外网、零第三方依赖**——完美落在 `SANDBOX_CODE_ONLY` 沙箱与 X-3 气隙约束内（见 `DECISIONS.md`）。
> 学会它，再把「run() 里换成调内网 API」就能推广到大多数自研件。

---

## 0. 这个 piece 做什么

| 类型 | 名称 | 作用 |
|---|---|---|
| Action | `add_business_days` | 给起始日期 + N 个工作日，跳过周末（可选跳节假日），算出到期日（SLA deadline 常用） |
| Action | `business_days_between` | 算两个日期之间的工作日数（考核时效常用） |
| Action | `is_business_day` | 判断某天是否工作日 |
| Trigger | `sla_due_soon`（进阶） | 轮询内网待办 API，发现 N 小时内到期的单据就触发流程 |

---

## 1. 目录结构

自研件一律放 `automation/packages/pieces/community/`（core/ 留给上游官方件，避免 rebase 冲突）：

```
automation/packages/pieces/community/biz-calendar/
├── package.json
├── tsconfig.json / tsconfig.lib.json / .eslintrc.json   # 从 hash-helper 抄来，不用改
└── src/
    ├── index.ts               # createPiece(...) 入口
    ├── i18n/
    │   └── zh.json            # 注意在 src/ 下——顶层 i18n/ 不会被打包
    └── lib/
        ├── common/
        │   └── business-days.ts     # 纯函数工具，好写单测
        ├── actions/
        │   ├── add-business-days.ts
        │   ├── business-days-between.ts
        │   └── is-business-day.ts
        └── triggers/
            └── sla-due-soon.ts       # 进阶章节
```

**0.88 没有脚手架 CLI**（`packages/cli` 已随裁剪删除，`npm run create-piece` 不存在），
骨架靠**照抄现成件**（how-to §1.1）。本例就是从 `hash-helper` 抄出来的：

```bash
cd automation
cp -r packages/pieces/community/hash-helper packages/pieces/community/biz-calendar
rm -rf packages/pieces/community/biz-calendar/{dist,node_modules}
# 然后：改 package.json 的 name（→ @activepieces/piece-biz-calendar）、
#       清空 src/lib/ 抄来的 action、按 §3–§5 写自己的文件。

# 【不能省】回 monorepo 根链接 workspace 包，否则编译报 TS2307：
pnpm install
```

---

## 2. `package.json`

抄自 hash-helper，只改 `name`；真实件全是 CommonJS，**不要**加 `"type": "module"`。
仓库现状逐字如下（`automation/packages/pieces/community/biz-calendar/package.json`）：

```json
{
  "name": "@activepieces/piece-biz-calendar",
  "version": "1.0.0",
  "main": "./dist/src/index.js",
  "types": "./dist/src/index.d.ts",
  "dependencies": {
    "@activepieces/pieces-common": "workspace:*",
    "@activepieces/pieces-framework": "workspace:*",
    "@activepieces/shared": "workspace:*",
    "tslib": "2.6.2"
  },
  "scripts": {
    "build": "tsc -p tsconfig.lib.json && cp package.json dist/",
    "lint": "eslint 'src/**/*.ts'"
  }
}
```

关于这份文件，0.88 有三点要清楚：

- **`workspace:*` 只服务本地类型检查 / IDE 跳转**。0.88 打包时它们**被整个丢弃**——esbuild 把
  `@activepieces/*` 内联进 bundle，tarball 的 `dependencies` 是 `{}`（§7）。
  0.84 那句「build-piece 会把 workspace 依赖 pin 成具体版本」**已经不成立**，
  照旧 pin 反而会炸镜像构建（§7 的 ⚠️）。
- **`scripts.build` 是抄来的遗留**，0.88 打包不走它（CLI/turbo 都没了），留着无害、也方便本地 tsc 自查。
- **`main`/`types` 指向 `dist/src/`** 是给 monorepo 内解析用的；**tarball 里的 `package.json`
  是另写的一份**（`main: "./src/index.js"`），两者不冲突，见 §7。

> hash-helper 的 package.json 多一行显式 `"type": "commonjs"`，本件没写——两者都按 CommonJS 打包，
> 效果一致；新件照抄 hash-helper（带上这行）更保险。
> **`version` 会一路流到白名单**（pieces.json / 元数据 / 预装目录三处必须逐字一致，见 how-to §0）。
> 改逻辑就升这个版本号，别原地覆盖。

---

## 3. 共享工具：`src/lib/common/business-days.ts`

把日期逻辑抽成**纯函数**，与 AP 框架解耦——好读、好测、run() 里只管接线。

```ts
/** 业务日历纯函数：全部按 UTC 的「年月日」计算，规避时区把日期算偏。 */

/** 把任意可解析的日期输入归一成 UTC 零点，只保留年月日。 */
function toUtcDate(input: string | Date): Date {
  const d = new Date(input);
  if (Number.isNaN(d.getTime())) {
    throw new Error(`无法解析日期：${String(input)}`);
  }
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
}

/** 归一成 YYYY-MM-DD，用于和节假日清单比对、以及输出。 */
function toYmd(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function isWeekend(d: Date): boolean {
  const day = d.getUTCDay(); // 0=周日 6=周六
  return day === 0 || day === 6;
}

/** 是否工作日：非周末且不在节假日清单里。holidays 传 ['2026-10-01', ...]。 */
export function isBusinessDay(input: string | Date, holidays: string[] = []): boolean {
  const d = toUtcDate(input);
  return !isWeekend(d) && !holidays.includes(toYmd(d));
}

/** 从 start 起加 N 个工作日（N 可为负，向前推）。返回 YYYY-MM-DD。 */
export function addBusinessDays(
  start: string | Date,
  days: number,
  holidays: string[] = [],
): string {
  if (!Number.isInteger(days)) {
    throw new Error(`工作日数必须是整数，收到：${days}`);
  }
  const step = days >= 0 ? 1 : -1;
  let remaining = Math.abs(days);
  const cur = toUtcDate(start);
  while (remaining > 0) {
    cur.setUTCDate(cur.getUTCDate() + step);
    if (isBusinessDay(cur, holidays)) {
      remaining--;
    }
  }
  return toYmd(cur);
}

/**
 * start 与 end 之间的工作日数（含 end、不含 start；end 早于 start 返回负数）。
 */
export function businessDaysBetween(
  start: string | Date,
  end: string | Date,
  holidays: string[] = [],
): number {
  const a = toUtcDate(start);
  const b = toUtcDate(end);
  const step = b >= a ? 1 : -1;
  let count = 0;
  const cur = new Date(a);
  while (toYmd(cur) !== toYmd(b)) {
    cur.setUTCDate(cur.getUTCDate() + step);
    if (isBusinessDay(cur, holidays)) {
      count += step;
    }
  }
  return count;
}
```

---

## 4. 三个 Action

### 4.1 `src/lib/actions/add-business-days.ts`

```ts
import { Property, createAction } from '@activepieces/pieces-framework';
import { addBusinessDays } from '../common/business-days';

export const addBusinessDaysAction = createAction({
  name: 'add_business_days',            // 稳定机器名，进 flow JSON，改名=breaking
  displayName: 'Add Business Days',
  description: '从起始日期起加 N 个工作日（跳过周末与节假日），算出到期日。',
  props: {
    startDate: Property.DateTime({
      displayName: 'Start Date',
      description: '起始日期（ISO / yyyy-MM-dd）。',
      required: true,
    }),
    days: Property.Number({
      displayName: 'Business Days',
      description: '要顺延的工作日数；负数表示往前推。',
      required: true,
    }),
    holidays: Property.Array({
      displayName: 'Holidays',
      description: "额外节假日清单，格式 yyyy-MM-dd，如 '2026-10-01'。",
      required: false,
    }),
  },
  run: async (ctx) => {
    const { startDate, days, holidays } = ctx.propsValue;
    const dueDate = addBusinessDays(startDate, days, (holidays as string[]) ?? []);
    // 返回值即该步输出，后续步骤可通过 {{step.dueDate}} 引用
    return { dueDate };
  },
});
```

### 4.2 `src/lib/actions/business-days-between.ts`

```ts
import { Property, createAction } from '@activepieces/pieces-framework';
import { businessDaysBetween } from '../common/business-days';

export const businessDaysBetweenAction = createAction({
  name: 'business_days_between',
  displayName: 'Business Days Between',
  description: '计算两个日期之间的工作日数（含结束日、不含起始日）。',
  props: {
    startDate: Property.DateTime({ displayName: 'Start Date', required: true }),
    endDate: Property.DateTime({ displayName: 'End Date', required: true }),
    holidays: Property.Array({ displayName: 'Holidays', required: false }),
  },
  run: async (ctx) => {
    const { startDate, endDate, holidays } = ctx.propsValue;
    const businessDays = businessDaysBetween(startDate, endDate, (holidays as string[]) ?? []);
    return { businessDays };
  },
});
```

### 4.3 `src/lib/actions/is-business-day.ts`

用 `StaticDropdown` 演示「口径」这类枚举参数（注意 `options.options` 的嵌套形状）：

```ts
import { Property, createAction } from '@activepieces/pieces-framework';
import { isBusinessDay } from '../common/business-days';

export const isBusinessDayAction = createAction({
  name: 'is_business_day',
  displayName: 'Is Business Day',
  description: '判断给定日期是否工作日。',
  props: {
    date: Property.DateTime({ displayName: 'Date', required: true }),
    holidays: Property.Array({ displayName: 'Holidays', required: false }),
    onFalse: Property.StaticDropdown({
      displayName: 'When Not a Business Day',
      description: '非工作日时的行为。',
      required: false,
      defaultValue: 'return',
      options: {
        options: [
          { label: '正常返回 false', value: 'return' },
          { label: '抛错中断流程', value: 'throw' },
        ],
      },
    }),
  },
  run: async (ctx) => {
    const { date, holidays, onFalse } = ctx.propsValue;
    const result = isBusinessDay(date, (holidays as string[]) ?? []);
    if (!result && onFalse === 'throw') {
      throw new Error(`${date} 不是工作日`);
    }
    return { isBusinessDay: result };
  },
});
```

---

## 5. 入口 `src/index.ts`

```ts
import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { addBusinessDaysAction } from './lib/actions/add-business-days';
import { businessDaysBetweenAction } from './lib/actions/business-days-between';
import { isBusinessDayAction } from './lib/actions/is-business-day';
import { slaDueSoonTrigger } from './lib/triggers/sla-due-soon';

export const bizCalendar = createPiece({
  displayName: 'Business Calendar',
  description: '工作日 / SLA 到期日计算（纯本地，无外网）。',
  auth: PieceAuth.None(),                 // 纯计算件无需鉴权
  minimumSupportedRelease: '0.82.0',      // 0.88 的下限：context V2 要求 ≥0.82.0，见下注
  logoUrl: '/ap-cdn/pieces/hermes/biz-calendar.svg', // HERMES: 气隙自托管图标(X-3)，见下注
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [addBusinessDaysAction, businessDaysBetweenAction, isBusinessDayAction],
  triggers: [slaDueSoonTrigger],          // 若不做触发器，这里给 []
});
```

> **`minimumSupportedRelease` 在 0.88 有硬下限 `0.82.0`。** 0.88 的 framework 引入了 context V2，
> 常量 `MINIMUM_SUPPORTED_RELEASE_AFTER_LATEST_CONTEXT_VERSION = '0.82.0'`（见
> `automation/packages/pieces/framework/src/lib/context/versioning.ts`）。`createPiece()` 会把
> 低于它（或不合法）的值**静默抬到 `0.82.0`**（`framework/src/lib/piece.ts`）——所以 0.84 时代写的
> `'0.36.1'` 不会报错，但序列化出来的元数据一定是 `0.82.0`。新件直接写 `'0.82.0'`，别再抄旧值。
>
> ⚠️ 仓库里 `src/index.ts` 目前仍留着 0.84 时期的 `'0.36.1' // 必须 ≤ 我们的 0.84.0`，
> 而 `deploy/pieces/metadata/piece-biz-calendar.json` 里已经是 `"minimumSupportedRelease": "0.82.0"`
> ——就是被上面这条 clamp 抬上去的。以本文写法为准。

> **图标别指向上游 CDN。** 本件早期写的是 `https://cdn.activepieces.com/pieces/calendar.svg`，
> 但上游根本没有这个文件——**联网也是 404**，任何环境都碎图（2026-07-26 排查气隙资产时才
> 发现，hash-helper 同病）。自研件把 svg 放进
> `automation/packages/web/public/ap-cdn/pieces/hermes/biz-calendar.svg`（本件的图已在位），
> `logoUrl` 写该同源路径即可。云端件不用管：CDN 资产已整体镜像 + 构建期重写
> （见 HOWTO §1.2 / §3.3）。

---

## 6. i18n（可选）`src/i18n/zh.json`

key 对应各 `displayName`/`description`，不做也能跑（回退英文）。**必须放 `src/i18n/`**——
放顶层 `i18n/` 一定不会进包（how-to §1.6）：

```json
{
  "Add Business Days": "顺延工作日",
  "Business Days Between": "工作日间隔",
  "Is Business Day": "是否工作日",
  "Start Date": "起始日期",
  "End Date": "结束日期",
  "Business Days": "工作日数",
  "Holidays": "节假日清单"
}
```

> ⚠️ **0.88 的 esbuild 打包不会自动带上 i18n**：bundle 只产出 `dist/src/index.js`，
> 本件当前的 tarball 里**只有** `src/index.js` + `package.json`，`zh.json` 没进去
> （元数据 JSON 里 `"i18n": null` 也是同一个原因）。上游件（如 text-helper）的做法是把
> `src/i18n` 一并拷进 `dist/` 并写进 tarball `package.json` 的 `files`：
>
> ```bash
> cp -r $P/src/i18n $P/dist/src/i18n            # $P 见 §7
> # 并在 §7 生成 dist/package.json 时把 files 写成
> #   ["src/index.js", "package.json", "src/i18n"]
> ```
>
> 不做也不影响功能（builder 显示英文原文）。

---

## 7. 本地构建 & 试运行（开发内环）

dev 与生产是同一套白名单机制（目录 DB-only，且 `AP_PIECES_SYNC_MODE` 的**代码默认值就是 `NONE`**，
**没有**「从源码加载」捷径，见 how-to §2）。
内环 = 打包 → 出两半 → seed dev 库 → 重启 AP。

**打包**（0.88 无 CLI，直接用 esbuild；配方与 how-to §3.1 一致，这里替换成本件的具体路径）：

```bash
cd automation
P=packages/pieces/community/biz-calendar
rm -rf $P/dist && mkdir -p $P/dist/src
npx esbuild $P/src/index.ts --bundle --platform=node --format=cjs --outfile=$P/dist/src/index.js

# tarball 的 package.json：自包含，零 @activepieces 依赖（与源码那份是两回事，见 §2）
node -e '
  const fs=require("fs"), d="packages/pieces/community/biz-calendar";
  const j=require("./"+d+"/package.json");
  fs.writeFileSync(d+"/dist/package.json", JSON.stringify({
    name: j.name, version: j.version, main: "./src/index.js",
    dependencies: {},
    files: ["src/index.js", "package.json"]
  }, null, 2) + "\n");
'
(cd $P/dist && npm pack --silent) \
  && mv $P/dist/activepieces-piece-biz-calendar-1.0.0.tgz hermes/tarballs/

# 冒烟：能 require 出来就说明 bundle 完整
node -e 'console.log(Object.keys(require("./packages/pieces/community/biz-calendar/dist/src/index.js")))'
# → [ 'bizCalendar' ]
```

⚠️ **别用 0.84 的 tsc 体例**（`tsc -p tsconfig.lib.json` + 把 `@activepieces/{framework,common,shared}`
pin 成具体版本）：0.88 工作区这几个包的版本在 npm 上不存在，构建期 `seed-offline-store.mjs`
解析它们会 404，**整个镜像构建挂掉**（how-to §3.1）。本件产出的实际长相：
`hermes/tarballs/activepieces-piece-biz-calendar-1.0.0.tgz`，**35.5 KB**，包内只有
`src/index.js` + `package.json`，`"dependencies": {}` ⇒ 离线烘焙对它是空操作，构建期零联网。

**出元数据半 + seed dev 库**：

```bash
cd ../deploy/pieces
node serialize-piece-metadata.js biz-calendar       # → metadata/piece-biz-calendar.json
#   （bundle 自包含，不再需要 dist/node_modules 就能 require 出 metadata）
# pieces.json（automation/hermes/pieces.json）追加：
#   { "name": "@activepieces/piece-biz-calendar", "version": "1.0.0",
#     "tarball": "activepieces-piece-biz-calendar-1.0.0.tgz" }
node generate-metadata-seed.js
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev \
  < metadata/pieces-seed.sql
docker restart platform-activepieces-dev            # 不重启则「列表有、单查 404」
```

到 DW：打开任一 Function Unit →（Process Design 里需有一个类型为 Automation 的 Service Task）
→ **Automation** 标签 → 创建/编辑 flow → 左侧搜 “Business Calendar”
→ 拖入 `Add Business Days` → 填 Start Date / Business Days。

props → builder 表单的映射一目了然：`DateTime`→日期选择器、`Number`→数字框、`Array`→可增删的列表、
`StaticDropdown`→下拉。**改代码后 Cmd+Shift+R 硬刷新**，否则吃旧 builder JS 缓存。

**试运行**（真实执行 `run()`）还需运行时半进 worker 预装目录——正式路径是烘镜像（how-to §6），
dev 快路径是手工预装（布局与 prewarm 一致，容器重建即失效；机制与坑见 how-to §2）：

```bash
cd <repo>
docker cp automation/hermes/tarballs/activepieces-piece-biz-calendar-1.0.0.tgz \
  platform-activepieces-dev:/tmp/piece.tgz
docker exec platform-activepieces-dev sh -c '
  P=/usr/src/app/cache/v13/common/pieces/@activepieces/piece-biz-calendar-1.0.0
  mkdir -p "$P" && cp /tmp/piece.tgz "$P/bundle.tgz"
  printf "{\"name\":\"@activepieces/piece-biz-calendar-1.0.0\",\"version\":\"1.0.0\",\"dependencies\":{\"@activepieces/piece-biz-calendar\":\"$P/bundle.tgz\"}}" > "$P/package.json"
  cd "$P" && pnpm install --config.node-linker=isolated --ignore-workspace && echo true > ready'
```

> 0.88 的两处易错点：缓存版本是 **`v13`**（0.84 是 `v11`），依赖文件名是 **`bundle.tgz`**
> （0.84 是 `archive.tgz`），且依赖值是它的**绝对路径、不带 `file:` 前缀**。写歪了运行时会重装或
> `PieceNotFound`。

装好后触发 flow 即可看到输出 `{ "dueDate": "2026-07-27" }`
（2026-07-24 周五 +1 工作日 = 下周一，实测）。

---

## 8. 进阶：加一个轮询触发器 `src/lib/triggers/sla-due-soon.ts`

演示 `createTrigger` + `TriggerStrategy.POLLING` + `context.store` 存游标。
这里把 run() 换成**调内网 API**（经网关放行，非公网），示范触发器如何拉增量。

```ts
import { httpClient, HttpMethod } from '@activepieces/pieces-common';
import { Property, TriggerStrategy, createTrigger } from '@activepieces/pieces-framework';

export const slaDueSoonTrigger = createTrigger({
  name: 'sla_due_soon',
  displayName: 'SLA Due Soon',
  description: '轮询内网待办 API，发现 N 小时内到期的单据即触发。',
  type: TriggerStrategy.POLLING,
  props: {
    apiBaseUrl: Property.ShortText({
      displayName: 'Internal API Base URL',
      description: '内网服务地址（须已在网关放行；勿填公网地址）。',
      required: true,
    }),
    withinHours: Property.Number({
      displayName: 'Within Hours',
      description: '到期阈值（小时）。',
      required: true,
      defaultValue: 24,
    }),
  },
  sampleData: { id: 'TODO-1001', dueAt: '2026-07-26T09:00:00Z', title: '示例待办' },

  // 启用时记初始游标；停用时清理。context.store 是每 flow 隔离的持久 KV。
  async onEnable(context) {
    await context.store.put('lastSeenId', '');
  },
  async onDisable(context) {
    await context.store.delete('lastSeenId');
  },

  // 引擎按调度周期调用；返回「新条目数组」，每个元素触发一次 flow。
  async run(context) {
    const { apiBaseUrl, withinHours } = context.propsValue;
    const lastSeenId = (await context.store.get<string>('lastSeenId')) ?? '';

    const res = await httpClient.sendRequest<{ items: Array<{ id: string; dueAt: string }> }>({
      method: HttpMethod.GET,
      url: `${apiBaseUrl}/todos/due-soon`,
      queryParams: { withinHours: String(withinHours), afterId: lastSeenId },
    });

    const items = res.body.items ?? [];
    if (items.length > 0) {
      await context.store.put('lastSeenId', items[items.length - 1].id);
    }
    return items; // 空数组=本轮无新条目，不触发
  },
});
```

> **注意**：触发器里一旦 `httpClient` 外呼，就不再是纯本地件——目标必须是**内网且已在网关放行**的地址，
> 否则气隙生产会失败（X-3）。演示到期阈值/游标的机制是本节重点；真部署时按你环境的 API 契约改。

---

## 9. 接入投放流水线（复用 how-to，不重复）

本地跑通后，按 [`PIECE_DEVELOPMENT_HOWTO.md`](./PIECE_DEVELOPMENT_HOWTO.md) §3 起把它投到 UAT/生产。
这个例子对应的具体命令：

```bash
# §3.1 运行时半：esbuild bundle → npm pack → 落 hermes/tarballs/（本文 §7 的第一块，逐字可抄）
#      自包含 tarball 已是安装源，构建机不需要能解析这个包名；也可另行 publish 到内网 Nexus

# §3.2 元数据半：本地序列化（不能问本地 AP 要——新件不在 DB，单查 404）
cd deploy/pieces
node serialize-piece-metadata.js biz-calendar   # → metadata/piece-biz-calendar.json

# §3.3 图标半：automation/packages/web/public/ap-cdn/pieces/hermes/biz-calendar.svg（本件已在位）

# §4 白名单：automation/hermes/pieces.json 追加（本件已登记）
#   { "name": "@activepieces/piece-biz-calendar", "version": "1.0.0",
#     "tarball": "activepieces-piece-biz-calendar-1.0.0.tgz" }

# §5 生成 seed
node generate-metadata-seed.js

# §6 烘镜像（cd automation && docker build -t activepieces:0.88.0-ee-removed .）
# → §7 投放（起 AP 建表 → psql < pieces-seed.sql → 重启 AP）→ §8 在 DW 验证
```

---

## 10. 单元测试（推荐，纯函数好测）

`common/business-days.ts` 是纯函数，测起来零依赖（仓库里目前**没有**落这个测试文件——
0.88 裁剪后 piece 侧不带测试脚手架，要跑就自己接测试框架）：

```ts
import { addBusinessDays, businessDaysBetween, isBusinessDay } from '../src/lib/common/business-days';

test('周五 +1 工作日 = 下周一', () => {
  expect(addBusinessDays('2026-07-24', 1)).toBe('2026-07-27'); // 24 是周五
});
test('跳过节假日', () => {
  expect(addBusinessDays('2026-09-30', 1, ['2026-10-01'])).toBe('2026-10-02');
});
test('is_business_day 认周末', () => {
  expect(isBusinessDay('2026-07-25')).toBe(false); // 周六
});
test('工作日间隔', () => {
  expect(businessDaysBetween('2026-07-24', '2026-07-27')).toBe(1);
});
```

---

## 11. 对照约束自查（照抄前过一遍）

- ✅ **纯本地**：三个 action 无外网、无子进程、无系统路径 → 落在 `SANDBOX_CODE_ONLY` 内。
- ✅ **无 bun**：全程 npx/pnpm（X-4）。
- ✅ **无外部依赖**：源码 deps 只有 `workspace:*`（+tslib），打包后 `dependencies: {}` → 气隙零障碍。
- ⛔ **`create-piece` / `build-piece` 在 0.88 已不存在**（`packages/cli` 被删）：脚手架靠 `cp -r hash-helper`（§1），打包靠 esbuild（§7）。
- ⛔ **别沿用 0.84 的 tsc 体例打包**（pin `@activepieces/*` 版本）：npm 上没有那些版本，构建期 `seed-offline-store.mjs` **404 炸镜像构建**（§7）。
- ⚠️ **抄完骨架先 `pnpm install`**：否则编译报 TS2307（找不到 `@activepieces/pieces-framework`）。
- ⚠️ **package.json 别加 `"type": "module"`**：真实件全是 CommonJS（§2）。
- ⚠️ **`minimumSupportedRelease` 写 `'0.82.0'`**：低于它会被 framework 静默抬上去（§5）。
- ⚠️ **i18n 放 `src/i18n/`，且 esbuild 体例下要显式带进 `files`**：否则 tarball 里没有（§6）。
- ⚠️ **元数据文件名 `piece-biz-calendar.json`**：存成 `biz-calendar.json` 生成器 ENOENT（脚本自动命名）。
- ⚠️ **触发器一旦外呼**：目标必须内网 + 网关放行（X-3），否则生产失败。
- ⚠️ **两半版本一致**：`package.json` / `pieces.json` / 元数据 JSON 三处 `1.0.0` 逐字对齐。
- ⚠️ **手工预装认 `cache/v13` + `bundle.tgz`**：沿用 0.84 的 `v11`/`archive.tgz` 会 `PieceNotFound`（§7）。
- ⚠️ **跑完 seed 必须重启 AP**：否则列表有、单查 404（进程内 `cachedRegistry`，见 how-to §7）。
