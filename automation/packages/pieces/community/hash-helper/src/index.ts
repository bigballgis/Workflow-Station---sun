import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { base64DecodeAction } from './lib/actions/base64-decode';
import { base64EncodeAction } from './lib/actions/base64-encode';
import { hashTextAction } from './lib/actions/hash-text';

export const hashHelper = createPiece({
  displayName: 'Hash Helper',
  description: '文本摘要与 Base64 编解码(纯本地,无外网)。',
  auth: PieceAuth.None(),                 // 纯计算件无需鉴权
  // 0.88 的 context V2 下限（framework 的 MINIMUM_SUPPORTED_RELEASE_AFTER_LATEST_CONTEXT_VERSION）。
  // 写低于它的值不会报错，只会被 Piece 构造器静默抬到这个数——写实值免得日后误判兼容范围。
  minimumSupportedRelease: '0.82.0',
  logoUrl: '/ap-cdn/pieces/hermes/hash-helper.svg', // HERMES: 气隙自托管图标(X-3)
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [hashTextAction, base64EncodeAction, base64DecodeAction],
  triggers: [],
});
