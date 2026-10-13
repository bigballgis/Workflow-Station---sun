import { describe, expect, it } from 'vitest'
import { allowsFrozenBatchEdit, extractBatchEditableFields, parseSubTableBatchCapability } from '../subTableBatchCapability'

const capability = {
  batchControlId: 'control-1',
  bindingId: '42',
  capabilities: { allowBatchAdd: false, allowBatchEdit: true, allowBatchDelete: false, allowBatchAssign: false },
  originalCrud: { allowEdit: true },
  batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description' }] },
}

describe('subTableBatchCapability', () => {
  it('accepts only the frozen v1 projection and binding it belongs to', () => {
    const parsed = parseSubTableBatchCapability(capability)
    expect(parsed?.batchControlId).toBe('control-1')
    expect(allowsFrozenBatchEdit(parsed, 42)).toBe(true)
    expect(allowsFrozenBatchEdit(parsed, 43)).toBe(false)
  })

  it('does not coerce string flags or unknown schemas into enabled access', () => {
    expect(parseSubTableBatchCapability({ ...capability, capabilities: { ...capability.capabilities, allowBatchEdit: 'true' } })).toBeNull()
    expect(parseSubTableBatchCapability({ ...capability, batchForm: { ...capability.batchForm, schemaVersion: 2 } })).toBeNull()
  })

  it('rejects a field-bearing unsupported control instead of omitting it', () => {
    const parsed = parseSubTableBatchCapability({
      ...capability,
      batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description' }, { type: 'upload', field: 'receipt' }] },
    })
    expect(extractBatchEditableFields(parsed!)).toBeNull()
  })
})
