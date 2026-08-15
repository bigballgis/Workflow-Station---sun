import { Property, createAction } from '@activepieces/pieces-framework';
import { isBusinessDay } from '../common/business-days';

export const isBusinessDayAction = createAction({
  name: 'is_business_day',
  displayName: 'Is Business Day',
  description: '判断给定日期是否工作日。',
  props: {
    date: Property.DateTime({ displayName: 'Date', required: true }),
    holidays: Property.Array({ displayName: 'Holidays', required: false }),
    onFalse: Property.StaticDropdown({
      displayName: 'When Not a Business Day',
      description: '非工作日时的行为。',
      required: false,
      defaultValue: 'return',
      options: {
        options: [
          { label: '正常返回 false', value: 'return' },
          { label: '抛错中断流程', value: 'throw' },
        ],
      },
    }),
  },
  run: async (ctx) => {
    const { date, holidays, onFalse } = ctx.propsValue;
    const result = isBusinessDay(date, (holidays as string[]) ?? []);
    if (!result && onFalse === 'throw') {
      throw new Error(`${date} 不是工作日`);
    }
    return { isBusinessDay: result };
  },
});
