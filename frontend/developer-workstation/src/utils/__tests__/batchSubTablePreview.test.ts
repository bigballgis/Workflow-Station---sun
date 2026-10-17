import { describe, expect, it } from 'vitest'
import { buildBatchPreviewDefinition } from '../batchSubTablePreview'

const form = { schemaVersion: 1, rule: [{ type: 'inputNumber', field: 'quantity', title: 'Quantity', props: { min: 0 } }, { type: 'miAssignment' }] }
const assignment = { allowUser: true, allowRole: true, assigneeField: 'handler', roleField: 'pool', buField: 'unit' }
const flags = ['allowBatchAdd', 'allowBatchEdit', 'allowBatchDelete', 'allowBatchAssign']
function control(props: Record<string, unknown> = {}, id = 'grid') {
  return { type: 'subTable', batchControlId: id, props: { _bindingId: 21, ...props } }
}

describe('Batch Preview projection', () => {
  it.each(Array.from({ length: 16 }, (_, index) => index))('projects independent capability combination %i', combination => {
    const props = Object.fromEntries(flags.map((key, index) => [key, !!(combination & (1 << index))]))
    const definition = buildBatchPreviewDefinition(control(props), form, assignment)
    if (!combination) { expect(definition).toBeUndefined(); return }
    expect(definition?.invalid).toBe(false)
    expect(Object.values(definition!.flags)).toEqual(Object.values(props))
  })
  it('legacy absent or string switches do not enable preview', () => {
    expect(buildBatchPreviewDefinition(control(), undefined)).toBeUndefined()
    expect(buildBatchPreviewDefinition(control({ allowBatchEdit: 'true' }), form)).toBeUndefined()
  })
  it('does not widen original CRUD or another same-binding control', () => {
    const one = buildBatchPreviewDefinition(control({ allowBatchEdit: true, allowEdit: false }, 'one'), form)
    const two = buildBatchPreviewDefinition(control({ allowBatchAssign: true }, 'two'), form, assignment)
    expect(one?.flags).toEqual({ add: false, edit: false, delete: false, assign: false })
    expect(two?.flags).toEqual({ add: false, edit: false, delete: false, assign: true })
    expect(two?.batchControlId).toBe('two')
  })
  it('rejects missing definition, unknown version, identity or unsupported controls instead of using subForms', () => {
    for (const config of [undefined, { ...form, schemaVersion: 2 }, { ...form, rule: [{ type: 'upload', field: 'photo' }] }]) {
      expect(buildBatchPreviewDefinition(control({ allowBatchEdit: true }), config)?.invalid).toBe(true)
    }
    expect(buildBatchPreviewDefinition(control({ allowBatchEdit: true }, ''), form)?.invalid).toBe(true)
  })
  it('requires assignment contract and its dedicated container, accepting layout-wrapped assignment-only schemas', () => {
    const placed = control({ allowBatchAssign: true })
    expect(buildBatchPreviewDefinition(placed, form)?.invalid).toBe(true)
    expect(buildBatchPreviewDefinition(placed, { schemaVersion: 1, rule: [{ type: 'input', field: 'caption' }] }, assignment)?.invalid).toBe(true)
    expect(buildBatchPreviewDefinition(placed, { schemaVersion: 1, rule: [{ type: 'row', children: [{ type: 'miAssignment' }] }] }, assignment)?.invalid).toBe(false)
  })
  it('projects scoped permissions only, matching the published sub-table permission contract', () => {
    const definition = buildBatchPreviewDefinition(control({ allowBatchEdit: true }), form, assignment,
      { '21:quantity': 'READONLY', 'other:quantity': 'EDITABLE' })
    expect(definition?.fieldWriteModes.quantity).toBe('READONLY')
    expect(buildBatchPreviewDefinition(control({ allowBatchEdit: true }), form, assignment,
      { quantity: 'READONLY', '21:quantity': 'EDITABLE' })?.fieldWriteModes.quantity).toBe('EDITABLE')
  })
})
