import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { startProcessAction } from './lib/actions/start';

export const functionUnitStart = createPiece({
  displayName: 'Function Unit Start',
  description: '发起一条功能单元流程。以工作区运行身份执行，步骤里不能选择发起人或办理人。',
  auth: PieceAuth.None(),
  minimumSupportedRelease: '0.82.0',
  logoUrl: '/ap-cdn/pieces/hermes/function-unit-start.svg',
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [startProcessAction],
  triggers: [],
});
