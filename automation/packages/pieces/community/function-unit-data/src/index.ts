import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { getRecordAction, queryRecordsAction, updateFieldsAction } from './lib/actions/records';
import { addSubtableRowAction, deleteSubtableRowAction, updateSubtableRowAction } from './lib/actions/subtable';

export const functionUnitData = createPiece({
  displayName: 'Function Unit Data',
  description: '读写功能单元数据。以工作区运行身份执行，步骤里不能选择操作人。',
  auth: PieceAuth.None(),
  minimumSupportedRelease: '0.82.0',
  logoUrl: '/ap-cdn/pieces/hermes/function-unit-data.svg',
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [
    queryRecordsAction,
    getRecordAction,
    updateFieldsAction,
    addSubtableRowAction,
    updateSubtableRowAction,
    deleteSubtableRowAction,
  ],
  triggers: [],
});
