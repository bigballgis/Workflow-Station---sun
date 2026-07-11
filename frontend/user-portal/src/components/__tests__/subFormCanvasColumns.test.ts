import { describe, it, expect } from 'vitest'
import {
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
})
