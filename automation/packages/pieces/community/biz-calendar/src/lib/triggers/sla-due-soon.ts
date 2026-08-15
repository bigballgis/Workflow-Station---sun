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
