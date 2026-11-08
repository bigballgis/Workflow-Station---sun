import { Property, createAction } from '@activepieces/pieces-framework';
import { callPortal, portalConfigFromEnv } from '../../../../function-unit-data/src/lib/common/portal-client';
import { startRequestBody } from '../start-body';

const identityNotice = Property.MarkDown({
  value: '以工作区运行身份发起。本步骤不能选择发起人或办理人。',
});

async function runStart(step: StepContext): Promise<unknown> {
  const externalProjectId = await step.project.externalId();
  return callPortal({
    ...portalConfigFromEnv(),
    externalProjectId: externalProjectId ?? '',
    flowId: step.flows.current.id,
    call: {
      method: 'POST',
      path: '/internal/automation/processes/start',
      body: startRequestBody(step.propsValue.functionUnitCode, step.propsValue.fieldsJson),
      idempotencyKey: `${step.run.id}:${step.step.name}`,
    },
  });
}

type StepContext = {
  propsValue: Record<string, unknown>;
  project: { externalId: () => Promise<string | undefined> };
  flows: { current: { id: string } };
  run: { id: string };
  step: { name: string };
};

export const startProcessAction = createAction({
  name: 'start_process',
  displayName: 'Start Process',
  description: '为发布范围里的功能单元发起一条流程。以工作区运行身份执行，不能指定发起人。',
  props: {
    notice: identityNotice,
    functionUnitCode: Property.ShortText({ displayName: 'Function Unit Code', required: true }),
    fieldsJson: Property.LongText({
      displayName: 'Fields JSON',
      description: '主表字段对象，例如 {"status":"OPEN"}。不接受子表。',
      required: false,
    }),
  },
  async run(ctx) {
    return runStart(ctx);
  },
});
