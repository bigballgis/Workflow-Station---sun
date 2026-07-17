import { describe, it, expect } from 'vitest'
import {
  flattenSubFormRuleLayoutContainers,
  resolveSubFormDialogColumnsForBinding,
  resolveSubFormRuleForBinding,
} from '../subTableAddDialogHelpers'

describe('resolveSubFormDialogColumnsForBinding', () => {
  const ctx = { lookupDbConfigs: {}, relationViewConfigs: {} }

  it('returns canvas fields only — excludes list-view-only audit columns', () => {
    const binding = { bindingId: 42 }
    const subForms = {
      42: {
        rule: [
          { type: 'input', field: 'id', title: 'id' },
          { type: 'input', field: 'main_id', title: 'main_id' },
          { type: 'input', field: 'testinfo', title: 'testinfo' },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['id', 'main_id', 'testinfo'])
    expect(dialogCols.some(c => c.field === 'created_at' || c.field === 'updated_at')).toBe(false)
  })

  it('prefers subFormConfig.rule on binding when present', () => {
    const binding = {
      bindingId: 7,
      subFormConfig: {
        rule: [{ type: 'input', field: 'only_canvas', title: 'Only Canvas' }],
      },
    }
    const subForms = {
      7: { rule: [{ type: 'input', field: 'from_config', title: 'From Config' }] },
    }
    expect(resolveSubFormRuleForBinding(binding, subForms)?.[0]).toMatchObject({ field: 'only_canvas' })
  })

  it('returns empty when no sub-form rule exists', () => {
    expect(resolveSubFormDialogColumnsForBinding({ bindingId: 1 }, {}, ctx)).toEqual([])
  })

  it('skips nested subTable / linkForm placeholders and fieldless layout rules', () => {
    const binding = { bindingId: 50113 }
    const subForms = {
      50113: {
        rule: [
          { type: 'input', field: 'shipment_name', title: 'Shipment Name' },
          { type: 'input', field: 'carrier', title: 'Carrier' },
          // Nested sub-table widget — no `field`, title used to leak into the dialog as a text input
          { type: 'subTable', title: 'Sub-Table', _bindingId: 50114, props: {} },
          { type: 'linkForm', title: 'Link Form', props: {} },
          { type: 'elCard', props: {}, children: [] },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['shipment_name', 'carrier'])
    expect(dialogCols.some(c => c.label === 'Sub-Table')).toBe(false)
  })

  it('keeps unknown field-bearing types (SubTableAddDialog passthrough contract)', () => {
    const subForms = {
      9: { rule: [{ type: 'someCustomWidget', field: 'custom_field', title: 'Custom' }] },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding({ bindingId: 9 }, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['custom_field'])
  })

  it('includes fields nested inside a Card layout container (FU 50013 regression)', () => {
    const binding = { bindingId: 50113 }
    const subForms = {
      50113: {
        rule: [
          { type: 'subTable', title: 'Sub-Table', _bindingId: 50114, props: {} },
          {
            type: 'elCard',
            props: { header: 'Shipment Info' },
            children: [
              { type: 'input', field: 'shipment_name', title: 'Shipment Name' },
              { type: 'input', field: 'carrier', title: 'Carrier' },
            ],
          },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['shipment_name', 'carrier'])
    expect(dialogCols.map(c => c.label)).toEqual(['Shipment Name', 'Carrier'])
  })
})

describe('flattenSubFormRuleLayoutContainers', () => {
  it('expands card/row/col containers recursively, preserving document order', () => {
    const rule = [
      { type: 'input', field: 'a', title: 'A' },
      {
        type: 'el-row',
        children: [
          { type: 'el-col', children: [{ type: 'input', field: 'b', title: 'B' }] },
          {
            type: 'elCard',
            children: [{ type: 'select', field: 'c', title: 'C' }],
          },
        ],
      },
      { type: 'input', field: 'd', title: 'D' },
    ]
    const flat = flattenSubFormRuleLayoutContainers(rule) as Array<{ field?: string }>
    expect(flat.map(r => r.field)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('reads children from props.children as well', () => {
    const rule = [
      { type: 'card', props: { children: [{ type: 'input', field: 'x', title: 'X' }] } },
    ]
    const flat = flattenSubFormRuleLayoutContainers(rule) as Array<{ field?: string }>
    expect(flat.map(r => r.field)).toEqual(['x'])
  })

  it('leaves placeholders and field-bearing rules untouched', () => {
    const subTable = { type: 'subTable', title: 'Sub-Table', props: {} }
    const fieldGroup = { type: 'group', field: 'g', title: 'Repeat Group' }
    const flat = flattenSubFormRuleLayoutContainers([subTable, fieldGroup])
    expect(flat[0]).toBe(subTable)
    expect(flat[1]).toBe(fieldGroup)
  })

  it('returns [] for non-array input', () => {
    expect(flattenSubFormRuleLayoutContainers(undefined)).toEqual([])
    expect(flattenSubFormRuleLayoutContainers(null)).toEqual([])
  })
})
