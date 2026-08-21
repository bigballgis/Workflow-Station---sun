import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { useSubTableDialogForm } from '../useSubTableDialogForm'
import type { DialogColumn } from '@/components/subTableAddDialogHelpers'

/**
 * isColDisabled must gate Add/Edit dialog columns on task-node field permissions the same way
 * SubTableField's Link Form dialog paths already do — this was a real gap: the dialog's own
 * top-level columns never consulted fieldPermissions at all (only col.readonly / calculated
 * columns / audit fields), despite the prop's own doc comment claiming otherwise.
 */
function setupForm(overrides: {
  columns?: DialogColumn[]
  bindingId?: number | null
  fieldPermissions?: Record<string, string> | null
} = {}) {
  const noop = vi.fn()
  const props = {
    visible: true,
    columns: overrides.columns ?? [
      { field: 'name', label: 'Name', type: 'text' as const },
      { field: 'notes', label: 'Notes', type: 'text' as const },
    ],
    mode: 'add' as const,
    bindingId: overrides.bindingId,
    fieldPermissions: overrides.fieldPermissions,
  }
  const emit = vi.fn()
  const t = (key: string) => key
  const deps = {
    formData: ref<Record<string, any>>({}),
    resetUploadNames: noop,
    backfillUploadNames: noop,
    resetLookupState: noop,
    destroyEditors: noop,
    fetchDepartmentTree: noop,
  }
  return useSubTableDialogForm(props as any, emit as any, t, deps as any)
}

describe('useSubTableDialogForm — field-permission gating of dialog columns', () => {
  it('marks a composite-keyed READONLY field disabled', () => {
    const { isColDisabled } = setupForm({
      bindingId: 99,
      fieldPermissions: { '99:name': 'READONLY' },
    })
    expect(isColDisabled({ field: 'name', label: 'Name', type: 'text' })).toBe(true)
  })

  it('leaves a field without an explicit entry enabled (backward compatible default)', () => {
    const { isColDisabled } = setupForm({
      bindingId: 99,
      fieldPermissions: { '99:name': 'READONLY' },
    })
    expect(isColDisabled({ field: 'notes', label: 'Notes', type: 'text' })).toBe(false)
  })

  it('no fieldPermissions at all leaves every field enabled (no FU ever configured any)', () => {
    const { isColDisabled } = setupForm({ bindingId: 99, fieldPermissions: null })
    expect(isColDisabled({ field: 'name', label: 'Name', type: 'text' })).toBe(false)
  })

  it('no bindingId (unresolvable) never marks fields readonly, even if fieldPermissions has entries', () => {
    const { isColDisabled } = setupForm({
      bindingId: null,
      fieldPermissions: { '99:name': 'READONLY' },
    })
    expect(isColDisabled({ field: 'name', label: 'Name', type: 'text' })).toBe(false)
  })

  it('does not apply a different bindingId\'s composite key to this binding\'s field', () => {
    const { isColDisabled } = setupForm({
      bindingId: 50,
      fieldPermissions: { '99:name': 'READONLY' },
    })
    expect(isColDisabled({ field: 'name', label: 'Name', type: 'text' })).toBe(false)
  })

  it('still marks col.readonly=true fields disabled regardless of fieldPermissions', () => {
    const { isColDisabled } = setupForm({ bindingId: 99, fieldPermissions: null })
    expect(isColDisabled({ field: 'name', label: 'Name', type: 'text', readonly: true })).toBe(true)
  })
})
