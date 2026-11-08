import { Property } from '@activepieces/pieces-framework';
import { callPortal, portalConfigFromEnv } from '../common/portal-client';

export const identityNotice = Property.MarkDown({
  value: '以工作区运行身份执行。本步骤不能选择同事，也不能填写操作人。',
});

type StepContext = {
  propsValue: Record<string, unknown>;
  project: { externalId: () => Promise<string | undefined> };
  flows: { current: { id: string } };
  run: { id: string };
  step: { name: string };
};

export async function runDataCall(ctx: StepContext, call: {
  method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  path: string;
  body?: Record<string, unknown>;
  write?: boolean;
}): Promise<unknown> {
  const externalProjectId = await ctx.project.externalId();
  const config = portalConfigFromEnv();
  return callPortal({
    ...config,
    externalProjectId: externalProjectId ?? '',
    flowId: ctx.flows.current.id,
    call: {
      method: call.method,
      path: call.path,
      body: call.body,
      idempotencyKey: call.write ? `${ctx.run.id}:${ctx.step.name}` : undefined,
    },
  });
}

export function filtersFromLines(lines: unknown): Array<{ field: string; value: string }> {
  if (!Array.isArray(lines) || lines.length === 0) {
    throw new Error('Query requires at least one filter');
  }
  return lines.map((line) => {
    const text = String(line);
    const split = text.indexOf('=');
    if (split <= 0) {
      throw new Error('Each filter must look like field=value');
    }
    return { field: text.slice(0, split).trim(), value: text.slice(split + 1).trim() };
  });
}
