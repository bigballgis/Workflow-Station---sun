import { describe, expect, it } from 'vitest'
import { allowsFrozenBatchAssign, allowsFrozenBatchEdit, extractBatchEditableFields, parseSubTableBatchCapability } from '../subTableBatchCapability'

const capability = {
  batchControlId: 'control-1',
  bindingId: '42',
  capabilities: { allowBatchAdd: false, allowBatchEdit: true, allowBatchDelete: false, allowBatchAssign: false },
  originalCrud: { allowEdit: true },
  batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description' }] },
}

describe('subTableBatchCapability', () => {
  it('keeps ordinary columns available when the workspace also contains dedicated Assignment', () => {
    const parsed = parseSubTableBatchCapability({ ...capability,
      batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description' }, { type: 'miAssignment', field: 'assignment_control' }] },
    })
    expect(extractBatchEditableFields(parsed!)?.map(field => field.field)).toEqual(['description'])
  })
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

  it('fails closed when Batch Assign lacks the frozen BPMN contract', () => {
    const assignOnly = {
      ...capability,
      capabilities: { allowBatchAdd: false, allowBatchEdit: false, allowBatchDelete: false, allowBatchAssign: true },
    }
    expect(parseSubTableBatchCapability(assignOnly)).toBeNull()

    const parsed = parseSubTableBatchCapability({
      ...assignOnly,
      assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'assigned_to' },
    })
    expect(allowsFrozenBatchAssign(parsed, 42)).toBe(true)
    expect(allowsFrozenBatchEdit(parsed, 42)).toBe(false)
  })
})
