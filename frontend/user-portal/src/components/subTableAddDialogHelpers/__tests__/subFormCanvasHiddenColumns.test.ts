import { describe, expect, it } from 'vitest'
import { mapSubFormRuleToDialogColumns } from '../subFormCanvasColumns'
import type { SubFormColumnLookupContext } from '../subFormCanvasColumns'

const ctx: SubFormColumnLookupContext = { lookupDbConfigs: {}, relationViewConfigs: {} }

/**
 * ATM Transaction regression: two designer-hidden date fields rendered in the sub-table
 * Add/Edit dialog. The dialog only ever sees the derived column list, so dropping the
 * Hide flag here made the field visible no matter what the designer configured.
 */
describe('mapSubFormRuleToDialogColumns — designer Hide flag', () => {
  it('carries every form-create hidden spelling onto the dialog column', () => {
    const cols = mapSubFormRuleToDialogColumns(
      [
        { type: 'datePicker', field: 'merchant_credit', title: 'Merchant Credit', hidden: true },
        { type: 'datePicker', field: 'temporary_refund', title: 'Temporary Refund', _hidden: true },
        { type: 'input', field: 'by_display', title: 'By display', display: false },
        { type: 'input', field: 'by_props', title: 'By props', props: { hidden: true } },
      ],
      ctx,
    )
    expect(cols.map(c => c.field)).toEqual([
      'merchant_credit', 'temporary_refund', 'by_display', 'by_props',
    ])
    expect(cols.every(c => c.hidden === true)).toBe(true)
  })

  it('leaves the flag off visible columns rather than writing hidden: false', () => {
    const [col] = mapSubFormRuleToDialogColumns(
      [{ type: 'input', field: 'amount', title: 'Amount' }],
      ctx,
    )
    expect(col.hidden).toBeUndefined()
    expect('hidden' in col).toBe(false)
  })

  it('keeps the Hide flag on fields nested inside layout containers', () => {
    const cols = mapSubFormRuleToDialogColumns(
      [
        {
          type: 'el-card',
          children: [
            { type: 'datePicker', field: 'merchant_credit', title: 'Merchant Credit', hidden: true },
            { type: 'input', field: 'amount', title: 'Amount' },
          ],
        },
      ],
      ctx,
    )
    expect(cols.find(c => c.field === 'merchant_credit')?.hidden).toBe(true)
    expect(cols.find(c => c.field === 'amount')?.hidden).toBeUndefined()
  })
})
