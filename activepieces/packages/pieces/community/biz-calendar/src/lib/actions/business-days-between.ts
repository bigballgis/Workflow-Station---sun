import { Property, createAction } from '@activepieces/pieces-framework';
import { businessDaysBetween } from '../common/business-days';

export const businessDaysBetweenAction = createAction({
  name: 'business_days_between',
  displayName: 'Business Days Between',
  description: '计算两个日期之间的工作日数（含结束日、不含起始日）。',
  props: {
    startDate: Property.DateTime({ displayName: 'Start Date', required: true }),
    endDate: Property.DateTime({ displayName: 'End Date', required: true }),
    holidays: Property.Array({ displayName: 'Holidays', required: false }),
  },
  run: async (ctx) => {
    const { startDate, endDate, holidays } = ctx.propsValue;
    const businessDays = businessDaysBetween(startDate, endDate, (holidays as string[]) ?? []);
    return { businessDays };
  },
});
