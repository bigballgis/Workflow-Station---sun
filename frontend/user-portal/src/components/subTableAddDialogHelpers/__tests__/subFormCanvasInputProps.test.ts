import { describe, expect, it } from 'vitest'
import { mapSubFormRuleToDialogColumns } from '../subFormCanvasColumns'

describe('designer input presentation', () => {
  it('distinguishes an absent placeholder from an explicitly empty one', () => {
    const columns = mapSubFormRuleToDialogColumns([
      { type: 'select', field: 'currency', title: 'Currency', props: {} },
      { type: 'inputNumber', field: 'amount', title: 'Amount', props: { placeholder: '' } },
    ], { relationViewConfigs: {} })
    expect(columns.map(column => column.placeholder)).toEqual([undefined, ''])
  })
  it('keeps the configured placeholder and word count for row dialogs', () => {
    const [column] = mapSubFormRuleToDialogColumns([
      { type: 'input', field: 'reference', title: 'Reference', props: {
        placeholder: 'Enter the reference', maxlength: 80, showWordLimit: true,
      } },
    ], { relationViewConfigs: {} })
    expect(column.placeholder).toBe('Enter the reference')
    expect(column.props?.showWordLimit).toBe(true)
    expect(column.props?.maxlength).toBe(80)
  })
})
