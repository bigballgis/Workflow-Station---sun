# Piece 开发示例：业务日历（`piece-biz-calendar`）

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

自研件一律放 `activepieces/packages/pieces/community/`（core/ 留给上游官方件，避免 rebase 冲突）：

```
activepieces/packages/pieces/community/biz-calendar/
├── package.json
├── tsconfig.lib.json          # create-piece 生成，通常不用改
├── .babelrc                   # 同上
├── src/
│   ├── index.ts               # createPiece(...) 入口
│   └── lib/
│       ├── common/
│       │   └── business-days.ts     # 纯函数工具，好写单测
│       ├── actions/
│       │   ├── add-business-days.ts
│       │   ├── business-days-between.ts
│       │   └── is-business-day.ts
│       └── triggers/
│           └── sla-due-soon.ts       # 进阶章节
└── i18n/
    └── zh.json
```

用脚手架生成骨架（X-4：用 npx，不碰 bun）：

```bash
cd activepieces
npm run create-piece
# 包名填 @activepieces/piece-biz-calendar，显示名 Business Calendar，类型选 community
```

---

## 2. `package.json`

`create-piece` 会生成，核对成这样（依赖都是 `workspace:*`，不引外部包 → 气隙友好）：

```json
{
  "name": "@activepieces/piece-biz-calendar",
  "version": "1.0.0",
  "type": "module",
  "dependencies": {
    "@activepieces/pieces-common": "workspace:*",
    "@activepieces/pieces-framework": "workspace:*",
    "@activepieces/shared": "workspace:*"
  }
}
```

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
  minimumSupportedRelease: '0.36.1',      // 必须 ≤ 我们的 0.84.0
  logoUrl: 'https://cdn.activepieces.com/pieces/calendar.svg', // 气隙里图标会裂，纯外观
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [addBusinessDaysAction, businessDaysBetweenAction, isBusinessDayAction],
  triggers: [slaDueSoonTrigger],          // 若不做触发器，这里给 []
});
```

---

## 6. i18n（可选）`i18n/zh.json`

key 对应各 `displayName`/`description`，不做也能跑（回退英文）：

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

---

## 7. 本地构建 & 试运行（开发内环）

```bash
cd activepieces
npm run build-piece -- biz-calendar          # → dist/packages/pieces/biz-calendar/

# 让 dev AP 从源码加载（dev 非白名单锁定）
cd deploy/environments/dev
docker compose build activepieces && docker compose up -d activepieces
docker restart platform-activepieces-dev     # 刷进程内 piece 缓存（见 how-to §7 缓存坑）
```

到 DW：打开任一 Function Unit → **Automation** 标签 → 新建/编辑 flow → 左侧搜 “Business Calendar”
→ 拖入 `Add Business Days` → 填 Start Date / Business Days → **试运行**看输出 `{ dueDate }`。

props → builder 表单的映射一目了然：`DateTime`→日期选择器、`Number`→数字框、`Array`→可增删的列表、
`StaticDropdown`→下拉。**改代码后 Cmd+Shift+R 硬刷新**，否则吃旧 builder JS 缓存。

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
# §3.1 运行时半：build → pack → 留档（并 publish 到内网 Nexus 供构建机解析）
cd activepieces && npm run build-piece -- biz-calendar
cd dist/packages/pieces/biz-calendar && npm pack
cp *.tgz <repo>/deploy/pieces/tarballs/

# §3.2 元数据半：从本地 AP 抓序列化元数据
docker exec platform-activepieces-dev node -e "
  require('http').get('http://127.0.0.1:80/api/v1/pieces/@activepieces/piece-biz-calendar?version=1.0.0',
    r=>{let d='';r.on('data',c=>d+=c);r.on('end',()=>process.stdout.write(d));})
" > <repo>/deploy/pieces/metadata/biz-calendar.json

# §4 白名单：deploy/pieces/pieces.json 追加
#   { "name": "@activepieces/piece-biz-calendar", "version": "1.0.0" }

# §5 生成 seed
cd <repo>/deploy/pieces && node generate-metadata-seed.js

# §6 烘镜像 → §7 投放（起 AP 建表 → psql < pieces-seed.sql → 重启 AP）→ §8 在 DW 验证
```

---

## 10. 单元测试（推荐，纯函数好测）

`common/business-days.ts` 是纯函数，测起来零依赖：

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
- ✅ **无外部依赖**：`package.json` 只有 `workspace:*` → 气隙镜像预装零障碍。
- ⚠️ **触发器一旦外呼**：目标必须内网 + 网关放行（X-3），否则生产失败。
- ⚠️ **两半版本一致**：`package.json` / `pieces.json` / 元数据 JSON 三处 `1.0.0` 逐字对齐。
- ⚠️ **跑完 seed 必须重启 AP**：否则列表有、单查 404（进程内 `cachedRegistry`，见 how-to §7）。
```
