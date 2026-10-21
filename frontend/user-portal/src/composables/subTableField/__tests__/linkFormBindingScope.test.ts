import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { SubTableBinding, SubTableFieldProps } from '../subTableFieldTypes'
import { isLinkFormBoundToHostGrid, subTableBindingMatches } from '../subTableLinkFormRowMatch'
import { useSubTableLinkFormDialog } from '../useSubTableLinkFormDialog'
import { useSubTableLinkFormOpen } from '../useSubTableLinkFormOpen'
import { useSubTableLinkFormScope } from '../useSubTableLinkFormScope'

vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() } }))

const rowA = { token: 'A', owner: 'CASE', reviewer: null, caption: 'A ONLY' }
const rowB = { token: 'B', owner: null, reviewer: 'CASE', caption: 'B ONLY' }
function setup(editable = true, data: Record<string, unknown>[] = [rowB]) {
  const target: SubTableBinding = { bindingId: 2, tableId: 50, tableName: 'Lines',
    bindingType: 'SUB', bindingMode: 'EDITABLE', tableType: 'SUB', tableDescription: '', columns: [],
    filterFkFieldName: 'reviewer', filterFkRefTableId: 10, bindingLinkMode: 'structuralFk',
    primaryKeyFields: ['token'], data,
    formFields: [{ key: 'caption', type: 'input', label: 'Caption' }],
  }
  const host: SubTableFieldProps = { bindingId: 1, tableId: 50, title: 'Lines', columns: [], editable,
    showLinkFormDialogFooter: true, filterFkFieldName: 'owner', filterFkRefTableId: 10,
    bindingLinkMode: 'structuralFk', primaryKeyFields: ['token'], linkedSubTableBindings: [target] }
  const rows = ref<Record<string, unknown>[]>([{ ...rowA }])
  const emit = vi.fn()
  const deps = { resolveSubTableRowPk: (row: Record<string, unknown> | null | undefined) => String(row?.token ?? '') }
  const scope = useSubTableLinkFormScope(host, deps)
  const dialog = useSubTableLinkFormDialog(host, rows, emit, key => key, { ...deps,
    filterRowsByMiLinkFormParent: scope.filterRowsByMiLinkFormParent })
  const open = useSubTableLinkFormOpen(host, dialog, scope)
  const col = { field: 'details', label: 'Details', props: { boundSubTableBindingId: 2 } }
  return { target, host, rows, emit, dialog, open, col }
}

describe('Link Form same-table binding scopes', () => {
  beforeEach(() => vi.clearAllMocks())
  it('does not equate different configured filtering FKs', () => {
    const { target, host, col } = setup()
    expect(isLinkFormBoundToHostGrid(col, host, target)).toBe(false)
    expect(subTableBindingMatches(target, { ...target, bindingId: 1, filterFkFieldName: 'owner' })).toBe(false)
  })
  it('retains cross-form MI aliases of the same configured filter scope', () => {
    const { target, host, col } = setup()
    target.filterFkFieldName = 'owner'
    target.bindingLinkMode = 'miParticipantRow'
    expect(isLinkFormBoundToHostGrid(col, host, target)).toBe(true)
  })
  it('keeps an unsaved same-scope MI row even before its parent FK is filled', () => {
    const { target, host, col, rows, dialog, open } = setup()
    target.filterFkFieldName = 'owner'
    target.bindingLinkMode = 'miParticipantRow'
    rows.value[0] = { ...rowA, owner: null }
    open.handleLinkFormClick(col, rows.value[0], 0)
    expect(dialog.linkedFormData.value.caption).toBe('A ONLY')
    expect(isLinkFormBoundToHostGrid(col, host, target)).toBe(true)
  })
  it('does not accept a same-PK target whose filter references a different case', () => {
    const { dialog, target } = setup(true, [{ ...rowA, reviewer: 'OTHER CASE' }])
    expect(dialog.buildLinkedFormData(target, {}, rowA).caption).toBeNull()
  })
  it.each([true, false])('rejects an unmatched cross-scope row (editable=%s)', editable => {
    const { open, col, rows, dialog, emit } = setup(editable)
    open.handleLinkFormClick(col, rows.value[0]!, 0)
    expect(dialog.linkFormDialogVisible.value).toBe(false)
    expect(ElMessage.warning).toHaveBeenCalledWith('linkForm.noMatchingRow')
    expect(emit).not.toHaveBeenCalled()
  })
  it('never seeds a same-table target from a row outside its filtering scope', () => {
    const { dialog, target } = setup(true, [{ ...rowA }])
    expect(dialog.buildLinkedFormData(target, {}, rowA).caption).toBeNull()
  })
  it('does not merge a different filter-scope peer to fill an empty target', () => {
    const { dialog, target, host } = setup(true, [])
    host.linkedSubTableBindings!.push({ ...target, bindingId: 1, filterFkFieldName: 'owner', data: [{ ...rowA }] })
    expect(dialog.resolveLinkedFallbackRows(target)).toEqual([])
  })
  it('blocks cross-scope save even if a stale dialog state tries to write the host row', () => {
    const { dialog, col, emit, rows } = setup()
    dialog.activeLinkColumn.value = col
    dialog.activeLinkRowIndex.value = 0
    dialog.linkedSubTableRows.value = [{ ...rowA }]
    dialog.linkedFormData.value = { caption: 'WRONG' }
    dialog.saveLinkedFormData()
    expect(emit).not.toHaveBeenCalled()
    expect(rows.value[0]!.caption).toBe('A ONLY')
    expect(ElMessage.warning).toHaveBeenCalledWith('linkForm.noMatchingRow')
  })
  it('opens and saves the same uniquely identified row when it belongs to both scopes', () => {
    const shared = { ...rowA, reviewer: 'CASE' }
    const { open, col, rows, dialog, emit } = setup(true, [shared, rowB])
    rows.value[0] = shared
    open.handleLinkFormClick(col, shared, 0)
    expect(dialog.linkFormDialogVisible.value).toBe(true)
    expect(dialog.linkedFormData.value.caption).toBe('A ONLY')
    dialog.updateLinkedFormField('caption', 'SHARED UPDATED')
    dialog.saveLinkedFormData()
    expect(emit).toHaveBeenCalledWith('update:modelValue', [{ ...shared, caption: 'SHARED UPDATED' }])
  })
  it('rejects ambiguous duplicate identities instead of choosing a row', () => {
    const shared = { ...rowA, reviewer: 'CASE' }
    const { open, col, rows, dialog } = setup(true, [shared, { ...shared, caption: 'DUPLICATE' }])
    rows.value[0] = shared
    open.handleLinkFormClick(col, shared, 0)
    expect(dialog.linkFormDialogVisible.value).toBe(false)
  })
  it('blocks changing the row identity through a cross-scope dialog', () => {
    const shared = { ...rowA, reviewer: 'CASE' }
    const { open, col, rows, dialog, emit } = setup(true, [shared, rowB])
    rows.value[0] = shared
    open.handleLinkFormClick(col, shared, 0)
    dialog.updateLinkedFormField('token', 'B')
    dialog.saveLinkedFormData()
    expect(emit).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('linkForm.noMatchingRow')
  })
})
