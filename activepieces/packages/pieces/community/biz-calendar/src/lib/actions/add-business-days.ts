import { Property, createAction } from '@activepieces/pieces-framework';
import { addBusinessDays } from '../common/business-days';

export const addBusinessDaysAction = createAction({
  name: 'add_business_days',            // 稳定机器名，进 flow JSON，改名=breaking
  displayName: 'Add Business Days',
  description: '从起始日期起加 N 个工作日（跳过周末与节假日），算出到期日。',
  props: {
    startDate: Property.DateTime({
      displayName: 'Start Date',
      description: '起始日期（ISO / yyyy-MM-dd）。',
      required: true,
    }),
    days: Property.Number({
      displayName: 'Business Days',
      description: '要顺延的工作日数；负数表示往前推。',
      required: true,
    }),
    holidays: Property.Array({
      displayName: 'Holidays',
      description: "额外节假日清单，格式 yyyy-MM-dd，如 '2026-10-01'。",
      required: false,
    }),
  },
  run: async (ctx) => {
    const { startDate, days, holidays } = ctx.propsValue;
    const dueDate = addBusinessDays(startDate, days, (holidays as string[]) ?? []);
    // 返回值即该步输出，后续步骤可通过 {{step.dueDate}} 引用
    return { dueDate };
  },
});
