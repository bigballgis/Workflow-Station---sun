import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { base64DecodeAction } from './lib/actions/base64-decode';
import { base64EncodeAction } from './lib/actions/base64-encode';
import { hashTextAction } from './lib/actions/hash-text';

export const hashHelper = createPiece({
  displayName: 'Hash Helper',
  description: '文本摘要与 Base64 编解码(纯本地,无外网)。',
  auth: PieceAuth.None(),                 // 纯计算件无需鉴权
  minimumSupportedRelease: '0.36.1',      // 必须 ≤ 我们的 0.84.0
  logoUrl: 'https://cdn.activepieces.com/pieces/hash-helper.png', // 气隙里图标会裂,纯外观
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [hashTextAction, base64EncodeAction, base64DecodeAction],
  triggers: [],
});
