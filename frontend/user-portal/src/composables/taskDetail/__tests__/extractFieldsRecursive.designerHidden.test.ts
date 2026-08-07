import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailFieldExtraction } from '../useTaskDetailFieldExtraction'
import { buildDialogLayoutGroups } from '@/components/subTableAddDialogHelpers/dialogFormLayout'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'

/**
 * Financial adjustment / ATM Transaction regression: designer-hidden fields inside
 * an elCard must remain in the FormField tree (hidden: true) so SubTableAddDialog
 * card grouping can place them back inside the card when scripts reveal them.
 * Dropping them at extract time stranded revealed dates in the dialog `rest` group.
 */
describe('extractFieldsRecursive — designer Hide stays in layout tree', () => {
  function extract(rules: unknown[]) {
    const { extractFieldsRecursive } = createTaskDetailFieldExtraction({
      lookupDbConfigs: ref({}),
      relationViewConfigs: ref({}),
      taskForm: {
        formFields: ref([]),
        formTabs: ref([]),
        formFieldsAfterTabs: ref([]),
        formFormOptions: ref({}),
        formReadOnly: ref(false),
      },
    } as never)
    return extractFieldsRecursive(rules)
  }

  const financialAdjustmentRules = [
    {
      type: 'elCard',
      props: { header: 'Financial adjustment' },
      children: [
        { type: 'select', field: 'merchant_credit', title: 'Merchant Credit' },
        { type: 'datePicker', field: 'merchant_credit_date', title: 'Merchant Credit Date', hidden: true },
        { type: 'select', field: 'temporary_refund', title: 'Temporary Refund' },
        { type: 'datePicker', field: 'temporary_refund_date', title: 'Temporary Refund Date', _hidden: true },
        { type: 'datePicker', field: 'rebilled_date', title: 'Rebilled Date' },
      ],
    },
  ]

  it('keeps Hide fields as card children with hidden flag', () => {
    const fields = extract(financialAdjustmentRules)
    expect(fields).toHaveLength(1)
    expect(fields[0].type).toBe('card')
    const keys = (fields[0].children || []).map(c => c.key)
    expect(keys).toEqual([
      'merchant_credit',
      'merchant_credit_date',
      'temporary_refund',
      'temporary_refund_date',
      'rebilled_date',
    ])
    expect(fields[0].children?.find(c => c.key === 'merchant_credit_date')?.hidden).toBe(true)
    expect(fields[0].children?.find(c => c.key === 'temporary_refund_date')?.hidden).toBe(true)
    expect(fields[0].children?.find(c => c.key === 'rebilled_date')?.hidden).toBeUndefined()
  })

  it('places revealed Hide fields inside the card — not the dialog rest group', () => {
    const formFields = extract(financialAdjustmentRules)
    const visible: DialogColumn[] = [
      'merchant_credit',
      'merchant_credit_date',
      'temporary_refund',
      'temporary_refund_date',
      'rebilled_date',
    ].map(field => ({ field, label: field, type: 'text' }))
    const groups = buildDialogLayoutGroups(formFields, visible)
    expect(groups).toHaveLength(1)
    expect(groups[0].title).toBe('Financial adjustment')
    expect(groups[0].items.map(i => i.key)).toEqual([
      'merchant_credit',
      'merchant_credit_date',
      'temporary_refund',
      'temporary_refund_date',
      'rebilled_date',
    ])
    expect(groups.some(g => g.key === 'rest')).toBe(false)
  })
})
