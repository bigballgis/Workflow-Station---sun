import { Property, createAction } from '@activepieces/pieces-framework';
import { identityNotice, runDataCall } from './shared';

const rowProps = {
  notice: identityNotice,
  processInstanceId: Property.ShortText({ displayName: 'Process Instance Id', required: true }),
  bindingId: Property.Number({ displayName: 'Sub-table Binding Id', required: true }),
  lockVersion: Property.Number({ displayName: 'Lock Version', required: true }),
};

export const addSubtableRowAction = createAction({
  name: 'add_subtable_row',
  displayName: 'Add Sub-table Row',
  description: '新增子表行。主键由服务端分配。以工作区运行身份执行。',
  props: {
    ...rowProps,
    fieldsJson: Property.LongText({ displayName: 'Fields JSON', required: true }),
  },
  async run(ctx) {
    const { processInstanceId, bindingId, lockVersion, fieldsJson } = ctx.propsValue;
    return runDataCall(ctx, {
      method: 'POST',
      write: true,
      path: `/internal/automation/records/${encodeURIComponent(processInstanceId)}/sub-tables/${bindingId}/rows`,
      body: { lockVersion, fields: JSON.parse(fieldsJson) },
    });
  },
});

export const updateSubtableRowAction = createAction({
  name: 'update_subtable_row',
  displayName: 'Update Sub-table Row',
  description: '更新子表行。以工作区运行身份执行。',
  props: {
    ...rowProps,
    rowId: Property.ShortText({ displayName: 'Row Id', required: true }),
    fieldsJson: Property.LongText({ displayName: 'Fields JSON', required: true }),
  },
  async run(ctx) {
    const { processInstanceId, bindingId, rowId, lockVersion, fieldsJson } = ctx.propsValue;
    return runDataCall(ctx, {
      method: 'PUT',
      write: true,
      path: `/internal/automation/records/${encodeURIComponent(processInstanceId)}/sub-tables/${bindingId}/rows/${encodeURIComponent(rowId)}`,
      body: { lockVersion, fields: JSON.parse(fieldsJson) },
    });
  },
});

export const deleteSubtableRowAction = createAction({
  name: 'delete_subtable_row',
  displayName: 'Delete Sub-table Row',
  description: '删除子表行。以工作区运行身份执行。',
  props: {
    ...rowProps,
    rowId: Property.ShortText({ displayName: 'Row Id', required: true }),
  },
  async run(ctx) {
    const { processInstanceId, bindingId, rowId, lockVersion } = ctx.propsValue;
    return runDataCall(ctx, {
      method: 'DELETE',
      write: true,
      path: `/internal/automation/records/${encodeURIComponent(processInstanceId)}/sub-tables/${bindingId}/rows/${encodeURIComponent(rowId)}`,
      body: { lockVersion },
    });
  },
});
