import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { extractFieldsAction } from './lib/actions/extract-fields';

export const contentOrganizer = createPiece({
  displayName: 'Content Organizer',
  description: '文档 OCR / 字段抽取（Content Organizer；iB2B 服务账号凭证取自部署环境）。',
  // No per-flow connection: one service account per environment, injected by the deployment
  // (K8s ConfigMap + Key Vault) — see lib/common/config.ts.
  auth: PieceAuth.None(),
  minimumSupportedRelease: '0.82.0',
  logoUrl: '/ap-cdn/pieces/hermes/content-organizer.svg', // HERMES: self-hosted icon (X-3)
  authors: ['workflow-station'],
  categories: [PieceCategory.CONTENT_AND_FILES],
  actions: [extractFieldsAction],
  triggers: [],
});
