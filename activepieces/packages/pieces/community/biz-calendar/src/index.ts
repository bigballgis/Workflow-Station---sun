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
  minimumSupportedRelease: '0.36.1',      // 必须 ≤ 我们的 0.84.0
  logoUrl: 'https://cdn.activepieces.com/pieces/calendar.svg', // 气隙里图标会裂，纯外观
  authors: ['workflow-station'],
  categories: [PieceCategory.CORE],
  actions: [addBusinessDaysAction, businessDaysBetweenAction, isBusinessDayAction],
  triggers: [slaDueSoonTrigger],          // 若不做触发器，这里给 []
});
