import { PieceAuth, createPiece } from '@activepieces/pieces-framework';
import { PieceCategory } from '@activepieces/shared';
import { addBusinessDaysAction } from './lib/actions/add-business-days';
import { businessDaysBetweenAction } from './lib/actions/business-days-between';
import { isBusinessDayAction } from './lib/actions/is-business-day';
import { slaDueSoonTrigger } from './lib/triggers/sla-due-soon';

export const bizCalendar = createPiece({
  displayName: 'Business Calendar',
  description: '工作日 / SLA 到期日计算（纯本地，无外网）。',
  auth: PieceAuth.None(),                 // 纯计算件无需鉴权
  // 0.88 的 context V2 下限（framework 的 MINIMUM_SUPPORTED_RELEASE_AFTER_LATEST_CONTEXT_VERSION）。
  // 写低于它的值不会报错，只会被 Piece 构造器静默抬到这个数——写实值免得日后误判兼容范围。
  minimumSupportedRelease: '0.82.0',
  logoUrl: '/ap-cdn/pieces/hermes/biz-calendar.svg', // HERMES: 气隙自托管图标(X-3)
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [addBusinessDaysAction, businessDaysBetweenAction, isBusinessDayAction],
  triggers: [slaDueSoonTrigger],          // 若不做触发器，这里给 []
});
