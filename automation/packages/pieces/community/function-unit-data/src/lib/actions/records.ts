import { Property, createAction } from '@activepieces/pieces-framework';
import { filtersFromLines, identityNotice, runDataCall } from './shared';

export const queryRecordsAction = createAction({
  name: 'query_records',
  displayName: 'Query Records',
  description: '按主表字段找出记录。以工作区运行身份执行。',
  props: {
    notice: identityNotice,
    functionUnitCode: Property.ShortText({ displayName: 'Function Unit Code', required: true }),
    filters: Property.Array({
      displayName: 'Filters',
      description: '至少一条，格式 field=value。字段必须属于该功能单元主表。',
      required: true,
    }),
    size: Property.Number({ displayName: 'Page Size', required: false, defaultValue: 50 }),
  },
  async run(ctx) {
    return runDataCall(ctx, {
      method: 'POST',
      path: '/internal/automation/records/query',
      body: {
        functionUnitCode: ctx.propsValue.functionUnitCode,
        filters: filtersFromLines(ctx.propsValue.filters),
        page: 1,
        size: ctx.propsValue.size ?? 50,
      },
    });
  },
});

export const getRecordAction = createAction({
  name: 'get_record',
  displayName: 'Get Record',
  description: '读取一条流程实例。以工作区运行身份执行。',
  props: {
    notice: identityNotice,
    processInstanceId: Property.ShortText({ displayName: 'Process Instance Id', required: true }),
  },
  async run(ctx) {
    return runDataCall(ctx, {
      method: 'GET',
      path: `/internal/automation/records/${encodeURIComponent(ctx.propsValue.processInstanceId)}`,
    });
  },
});

export const updateFieldsAction = createAction({
  name: 'update_fields',
  displayName: 'Update Fields',
  description: '更新主表字段。以工作区运行身份执行，不能指定操作人。',
  props: {
    notice: identityNotice,
    processInstanceId: Property.ShortText({ displayName: 'Process Instance Id', required: true }),
    lockVersion: Property.Number({ displayName: 'Lock Version', required: true }),
    fieldsJson: Property.LongText({
      displayName: 'Fields JSON',
      description: '主表字段对象，例如 {"status":"OPEN"}。',
      required: true,
    }),
  },
  async run(ctx) {
    return runDataCall(ctx, {
      method: 'PATCH',
      path: `/internal/automation/records/${encodeURIComponent(ctx.propsValue.processInstanceId)}/fields`,
      write: true,
      body: {
        lockVersion: ctx.propsValue.lockVersion,
        fields: JSON.parse(ctx.propsValue.fieldsJson),
      },
    });
  },
});
