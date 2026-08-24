import { describe, expect, it } from 'vitest'
import {
  applyActionFormCanvasToPreview,
  overlayActionBindingRulesOnSubForms,
  resolveActionFormCanvasRule,
  selectPreviewCanvasTableBinding,
} from '../actionFormCanvasRule'

const ACTION_FIELDS = [
  { type: 'input', field: 'id', title: 'Id' },
  { type: 'input', field: 'remark_type', title: 'Remark Type' },
  { type: 'input', field: 'remark_content', title: 'Remark Content' },
]

describe('resolveActionFormCanvasRule', () => {
  it('leaves TASK/PROCESS forms on the top-level rule', () => {
    const top = [{ type: 'input', field: 'I', title: 'Meeting Name' }]
    const result = resolveActionFormCanvasRule({
      formType: 'TASK',
      tableBindings: [{ id: 50538, bindingType: 'PRIMARY' }],
      topLevelRule: top,
      subForms: { '50539': { rule: ACTION_FIELDS } },
    })
    expect(result.usedActionCanvas).toBe(false)
    expect(result.actionBindingId).toBeNull()
    expect(result.rule).toEqual(top)
  })

  it('uses ACTION subForms canvas when top-level rule is empty (Meeting Remark)', () => {
    const result = resolveActionFormCanvasRule({
      formType: 'ACTION',
      tableBindings: [
        { id: 50629, bindingType: 'ACTION' },
        { id: 50630, bindingType: 'RELATED' },
      ],
      topLevelRule: [],
      subForms: { '50629': { rule: ACTION_FIELDS } },
    })
    expect(result.usedActionCanvas).toBe(true)
    expect(result.actionBindingId).toBe(50629)
    expect(result.rule).toEqual(ACTION_FIELDS)
  })

  it('reads subForms keyed by numeric binding id', () => {
    const result = resolveActionFormCanvasRule({
      formType: 'ACTION',
      tableBindings: [{ id: 50629, bindingType: 'ACTION' }],
      topLevelRule: [],
      subForms: { 50629: { rule: ACTION_FIELDS } } as unknown as Record<string, { rule?: unknown }>,
    })
    expect(result.usedActionCanvas).toBe(true)
    expect(result.rule).toHaveLength(3)
  })

  it('accepts Portal-style bindingId when id is absent', () => {
    const result = resolveActionFormCanvasRule({
      formType: 'ACTION',
      tableBindings: [{ bindingId: 50629, bindingType: 'ACTION' }],
      topLevelRule: [],
      subForms: { '50629': { rule: ACTION_FIELDS } },
    })
    expect(result.usedActionCanvas).toBe(true)
    expect(result.actionBindingId).toBe(50629)
  })

  it('falls back to top-level rule when ACTION canvas is empty (legacy)', () => {
    const top = [{ type: 'input', field: 'legacy', title: 'Legacy' }]
    const result = resolveActionFormCanvasRule({
      formType: 'ACTION',
      tableBindings: [{ id: 50629, bindingType: 'ACTION' }],
      topLevelRule: top,
      subForms: { '50629': { rule: [] } },
    })
    expect(result.usedActionCanvas).toBe(false)
    expect(result.rule).toEqual(top)
  })

  it('prefers ACTION canvas over a non-empty top-level rule (Portal parity)', () => {
    const top = [{ type: 'input', field: 'I', title: 'Meeting Name' }]
    const result = resolveActionFormCanvasRule({
      formType: 'ACTION',
      tableBindings: [{ id: 50629, bindingType: 'ACTION' }],
      topLevelRule: top,
      subForms: { '50629': { rule: ACTION_FIELDS } },
    })
    expect(result.usedActionCanvas).toBe(true)
    expect(result.rule).toEqual(ACTION_FIELDS)
  })

  it('stays empty when ACTION form has no canvas and no top-level rule', () => {
    const result = resolveActionFormCanvasRule({
      formType: 'ACTION',
      tableBindings: [{ id: 50629, bindingType: 'ACTION' }],
      topLevelRule: [],
      subForms: {},
    })
    expect(result.usedActionCanvas).toBe(false)
    expect(result.rule).toEqual([])
  })
})

describe('overlayActionBindingRulesOnSubForms', () => {
  it('overlays live ACTION designer rule onto saved subForms', () => {
    const live = [{ type: 'input', field: 'remark_type', title: 'Live Remark Type' }]
    const map = new Map<number, { bindingType?: string; rule?: unknown[] }>([
      [50629, { bindingType: 'ACTION', rule: live }],
      [50630, { bindingType: 'RELATED', rule: [{ type: 'input', field: 'x' }] }],
    ])
    const out = overlayActionBindingRulesOnSubForms(
      { '50629': { rule: ACTION_FIELDS } },
      map,
    )
    expect(out['50629']?.rule).toEqual(live)
  })

  it('does not overlay a live empty ACTION canvas over saved fields', () => {
    const map = new Map<number, { bindingType?: string; rule?: unknown[] }>([
      [50629, { bindingType: 'ACTION', rule: [] }],
    ])
    const out = overlayActionBindingRulesOnSubForms(
      { '50629': { rule: ACTION_FIELDS } },
      map,
    )
    expect(out['50629']?.rule).toEqual(ACTION_FIELDS)
  })

  it('leaves saved ACTION fields when live rule is missing', () => {
    const map = new Map<number, { bindingType?: string; rule?: unknown[] }>([
      [50629, { bindingType: 'ACTION' }],
    ])
    const out = overlayActionBindingRulesOnSubForms(
      { '50629': { rule: ACTION_FIELDS } },
      map,
    )
    expect(out['50629']?.rule).toEqual(ACTION_FIELDS)
  })
})

describe('selectPreviewCanvasTableBinding', () => {
  const primary = { id: 1, bindingType: 'PRIMARY', tableId: 100 }
  const action = { id: 2, bindingType: 'ACTION', tableId: 200 }

  it('uses the ACTION table when the ACTION canvas is active even if PRIMARY exists', () => {
    const selected = selectPreviewCanvasTableBinding({
      tableBindings: [primary, action],
      usedActionCanvas: true,
      actionBindingId: 2,
    })
    expect(selected?.tableId).toBe(200)
  })

  it('uses PRIMARY when ACTION canvas is not active', () => {
    const selected = selectPreviewCanvasTableBinding({
      tableBindings: [primary, action],
      usedActionCanvas: false,
      actionBindingId: 2,
    })
    expect(selected?.tableId).toBe(100)
  })
})

describe('applyActionFormCanvasToPreview', () => {
  it('prefers ACTION canvas fields over a dirty top-level rule', () => {
    const applied = applyActionFormCanvasToPreview({
      formType: 'ACTION',
      tableBindings: [{ id: 50629, bindingType: 'ACTION', tableId: 50334 }],
      topLevelRule: [{ type: 'input', field: 'I', title: 'Meeting Name' }],
      savedSubForms: { '50629': { rule: ACTION_FIELDS } },
      bindingMap: new Map(),
      primaryFieldDefs: [{ fieldName: 'I' }],
    })
    expect(applied.usedActionCanvas).toBe(true)
    expect(applied.rule).toEqual(ACTION_FIELDS)
  })
})
