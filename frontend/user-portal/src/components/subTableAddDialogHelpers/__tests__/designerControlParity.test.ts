import { describe, expect, it } from 'vitest'
import { mapSubFormRuleToDialogColumns } from '../subFormCanvasColumns'
import { convertFormCreateRule } from '@/composables/processStart/useProcessStartRuleConverter'

describe('designer control parity', () => {
  it('retains numeric, switch and select presentation and option state', () => {
    const rules = [
      { type: 'inputNumber', field: 'amount', props: { step: 5, controlsPosition: 'right' } },
      { type: 'switch', field: 'enabled', props: { activeText: 'Yes', inactiveText: 'No', activeValue: 'Y', inactiveValue: 'N' } },
      { type: 'select', field: 'choice', props: { filterable: true }, options: [{ label: 'Unavailable', value: 'a', disabled: true }] },
    ]
    const cols = mapSubFormRuleToDialogColumns(rules, { relationViewConfigs: {} })
    expect(cols[0].props).toMatchObject({ step: 5, controlsPosition: 'right' })
    expect(cols[1].props).toMatchObject(rules[1].props)
    expect(cols[2].props).toMatchObject({ filterable: true })
    expect(cols[2].options?.[0]).toMatchObject({ disabled: true })
  })
  it.each(['daterange', 'datetimerange'])('retains the %s cardinality', type => {
    const [col] = mapSubFormRuleToDialogColumns([{ type: 'datePicker', field: 'period', props: { type } }], { relationViewConfigs: {} })
    expect(col.type).toBe(type)
  })
  it('preserves explicit false word count and custom format in Portal fields', () => {
    const field = convertFormCreateRule({ type: 'input', field: 'text', props: { maxlength: 80, showWordLimit: false } })
    expect(field?.showWordLimit).toBe(false)
    const date = convertFormCreateRule({ type: 'datePicker', field: 'period', props: { type: 'daterange', valueFormat: 'YYYY/MM/DD', format: 'DD/MM/YYYY' } })
    expect(date).toMatchObject({ valueFormat: 'YYYY/MM/DD', format: 'DD/MM/YYYY' })
  })
  it('keeps required validation compatible with a switch storing string values', () => {
    const rule = { type: 'switch', field: 'state', props: { activeValue: 'Y', inactiveValue: 'N' }, validate: [{ required: true }] }
    const [column] = mapSubFormRuleToDialogColumns([rule], { relationViewConfigs: {} })
    expect(column.rules).toEqual(expect.arrayContaining([expect.objectContaining({ type: 'string', required: true })]))
    expect(convertFormCreateRule(rule)?.rules).toEqual(expect.arrayContaining([expect.objectContaining({ type: 'string', required: true })]))
  })
})
