import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import { defineComponent } from 'vue'
import BatchWorkspace from '../SubTableBatchEditDialog.vue'
import type { SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { PreparedBatchRows } from '@/utils/subTableBatchAdd'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/request', () => ({ request: { get: vi.fn() } }))
vi.mock('@/api/permission', () => ({ permissionApi: {} }))
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
}))

interface WorkspaceState {
  draftRows: Array<Record<string, unknown>>
  addCount: number
  addDraftRows(): void
  onSelectionChange(rows: Array<Record<string, unknown>>): void
  deleteSelected(): Promise<void>
  apply(): Promise<void>
  close(): Promise<boolean>
  editableRow(row: Record<string, unknown>, field: string): boolean
}

const capability: SubTableBatchCapability = {
  batchControlId: 'lines-grid', bindingId: '101', originalCrud: {},
  capabilities: { allowBatchAdd: true, allowBatchDelete: true, allowBatchEdit: true, allowBatchAssign: false },
  batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description', required: true }] },
}
const initialRows = [{ line_key: 'L1', description: 'first', hidden: 'keep-1' }, { line_key: 'L2', description: 'second', hidden: 'keep-2' }]
const collectAssignment = vi.fn()
const assignmentDirty = vi.fn(() => false)

function workspace(prepareRows = vi.fn(async (rows: Array<Record<string, unknown>>): Promise<PreparedBatchRows> => ({
  rows: rows.map((row, index) => ({ ...row, line_key: `N${index + 1}` })),
}))) {
  const wrapper = mount(BatchWorkspace, { props: {
    visible: true, rows: initialRows, capability, showAdd: true, showDelete: true,
    rowIdentity: row => String(row.line_key), prepareRows,
  }, global: { stubs: {
    ElDialog: { template: '<div><slot name="header"/><slot/><slot name="footer"/></div>' },
    DesignerHelpLink: { props: ['path', 'ariaLabel', 'testId'], template: '<a :href="path" :data-testid="testId"/>' },
    ElTable: { name: 'ElTable', props: ['data'], template: '<div />' }, ElTableColumn: true, ElForm: { template: '<div><slot/></div>' },
    ElFormItem: { template: '<div><slot/></div>' }, ElInputNumber: true, ElButton: { template: '<button><slot/></button>' },
    ElPagination: true, ElDivider: true, ElSelect: true, ElOption: true, ElInput: true,
    SubTableBatchAssignmentEditor: defineComponent({
      name: 'SubTableBatchAssignmentEditor',
      setup(_, { expose }) { expose({ collect: collectAssignment, hasChanges: assignmentDirty }); return () => null },
    }),
  } } })
  return { wrapper, state: wrapper.findComponent({ name: 'BatchWorkspace' }).vm as unknown as WorkspaceState, prepareRows }
}

describe('batch workspace Add/Delete transaction', () => {
  it('does not emit data updates for an unchanged workspace', async () => {
    const { wrapper, state } = workspace()
    await state.apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toEqual([[false]])
    wrapper.unmount()
  })

  it('confirms discarding an Assignment-only draft and keeps it when dismissal is cancelled', async () => {
    const { wrapper, state } = workspace()
    await wrapper.setProps({ showAssign: true, allowEdit: false, showAdd: false, showDelete: false,
      assignmentCapability: { ...capability, assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'handler_code' } },
      rowScopeKey: (row: Record<string, unknown>) => ({ line_key: row.line_key }) })
    assignmentDirty.mockReturnValueOnce(true)
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    expect(await state.close()).toBe(false)
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    expect(wrapper.emitted('apply')).toBeUndefined()
    wrapper.unmount()
  })

  it('does not apply valid Assignment when an ordinary field fails validation', async () => {
    const { wrapper, state } = workspace()
    await wrapper.setProps({ showAssign: true, assignmentCapability: { ...capability, assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'handler_code' } }, rowScopeKey: (row: Record<string, unknown>) => ({ line_key: row.line_key }) })
    state.draftRows[0]!.description = ''
    collectAssignment.mockImplementation((rows: Array<Record<string, unknown>>) => ({ rows, change: { rowPatches: [{ rowId: 'L1' }] } }))
    await state.apply()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    expect(state.draftRows[0]!.description).toBe('')
    wrapper.unmount()
  })
  it('keeps Assignment inside the workspace and applies both drafts in one event', async () => {
    const { wrapper, state } = workspace()
    const assignCapability = { ...capability, assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'handler_code' } }
    await wrapper.setProps({ showAssign: true, assignmentCapability: assignCapability, rowScopeKey: (row: Record<string, unknown>) => ({ line_key: row.line_key }) })
    state.draftRows[0]!.description = 'ordinary draft'
    const change = { batchControlId: 'lines-grid', bindingId: '101', operation: 'ASSIGN', rowPatches: [{ rowId: 'L1', rowKey: { line_key: 'L1' }, fields: ['handler_code'], assignment: { mode: 'PERSON', assigneeId: 'user-new' } }] }
    collectAssignment.mockImplementation((rows: Array<Record<string, unknown>>) => ({ rows: rows.map((row, index) => index === 0 ? { ...row, handler_code: 'user-new' } : row), change }))
    expect(wrapper.findComponent({ name: 'SubTableBatchAssignmentEditor' }).exists()).toBe(true)
    await state.apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    const event = wrapper.emitted('apply-workspace')?.[0]
    expect(event?.[0]).toEqual([{ ...initialRows[0], description: 'ordinary draft', handler_code: 'user-new' }, initialRows[1]])
    expect(event?.[3]).toEqual(change)
    wrapper.unmount()
  })

  it('retains both drafts and emits nothing when Assignment validation fails', async () => {
    const { wrapper, state, prepareRows } = workspace()
    await wrapper.setProps({ showAssign: true, assignmentCapability: { ...capability, assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'handler_code' } }, rowScopeKey: (row: Record<string, unknown>) => ({ line_key: row.line_key }) })
    state.draftRows[0]!.description = 'ordinary draft'
    collectAssignment.mockImplementation(() => { throw new Error('subTable.batchAssignmentRequired') })
    await state.apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    expect(prepareRows).not.toHaveBeenCalled()
    expect(state.draftRows[0]!.description).toBe('ordinary draft')
    wrapper.unmount()
  })
  it('uses the frozen read-only field policy and typed literal defaults for new rows', async () => {
    const { wrapper, state } = workspace()
    await wrapper.setProps({ capability: { ...capability,
      batchForm: { schemaVersion: 1, rule: [{ type: 'inputNumber', field: 'quantity' }] },
      rowDesign: { binding: { tableId: 20, bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk',
        primaryKeyFields: ['line_key'], fieldDefinitions: [{ fieldName: 'quantity' }] },
        fields: [{ fieldName: 'quantity', dataType: 'INTEGER', defaultValue: '0' }], parentTablesById: {},
        fieldWriteModes: { quantity: 'READONLY' },
      },
    } })
    state.addDraftRows()
    const added = state.draftRows[2]!
    expect(added.quantity).toBe(0)
    expect(state.editableRow(added, 'quantity')).toBe(false)
    wrapper.unmount()
  })
  it('does not replace table data when selection changes', async () => {
    const { wrapper, state } = workspace()
    const data = wrapper.getComponent({ name: 'ElTable' }).props('data')
    state.onSelectionChange([state.draftRows[0]!])
    await wrapper.vm.$nextTick()
    expect(wrapper.getComponent({ name: 'ElTable' }).props('data')).toBe(data)
    wrapper.unmount()
  })
  it('does not prompt to discard an already applied workspace when the dialog reports its close', async () => {
    const { wrapper, state } = workspace()
    state.draftRows[0]!.description = 'applied value'
    await state.apply()
    await wrapper.setProps({ visible: false })
    vi.mocked(ElMessageBox.confirm).mockClear()
    await state.close()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('links its header to the existing Sub-Table batch guideline', () => {
    const { wrapper } = workspace()
    expect(wrapper.get('[data-testid="batch-workspace-guide-link"]').attributes('href')).toBe('/form-ctl-sub-table#batch')
    wrapper.unmount()
  })
  it('keeps saved rows read-only in Add-only mode while accepting new rows', async () => {
    const { wrapper, state } = workspace()
    await wrapper.setProps({ allowEdit: false, showDelete: false })
    state.draftRows[0]!.description = 'attempted existing edit'
    state.addDraftRows()
    state.draftRows[2]!.description = 'new description'
    await state.apply()
    expect(wrapper.emitted('apply-workspace')?.[0]?.[0]).toEqual([
      ...initialRows, { line_key: 'N1', description: 'new description' },
    ])
    expect(wrapper.emitted('apply-workspace')?.[0]?.[1]).toEqual({})
    wrapper.unmount()
  })

  it('accepts only the deletion in Delete-only mode', async () => {
    const { wrapper, state, prepareRows } = workspace()
    await wrapper.setProps({ allowEdit: false, showAdd: false })
    state.draftRows[1]!.description = 'attempted existing edit'
    state.onSelectionChange([state.draftRows[0]!])
    await state.deleteSelected()
    await state.apply()
    expect(wrapper.emitted('apply-workspace')?.[0]?.[0]).toEqual([initialRows[1]])
    expect(prepareRows).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('can delete the last saved rows without preparing additions', async () => {
    const { wrapper, state, prepareRows } = workspace()
    state.onSelectionChange([...state.draftRows])
    await state.deleteSelected()
    await state.apply()
    expect(prepareRows).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply-workspace')?.[0]?.[0]).toEqual([])
    expect(wrapper.emitted('apply-workspace')?.[0]?.[1]).toEqual({ deletedRowIds: ['L1', 'L2'] })
    wrapper.unmount()
  })

  it('rejects duplicated prepared identities without emitting page changes', async () => {
    const prepare = vi.fn(async (rows: Array<Record<string, unknown>>): Promise<PreparedBatchRows> => ({
      rows: rows.map(row => ({ ...row, line_key: 'L1' })),
    }))
    const { wrapper, state } = workspace(prepare)
    state.addDraftRows()
    state.draftRows[2]!.description = 'duplicate'
    await state.apply()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    wrapper.unmount()
  })

  it('refuses applying a stale baseline after polling changed the page', async () => {
    const { wrapper, state, prepareRows } = workspace()
    state.addDraftRows()
    state.draftRows[2]!.description = 'new'
    await wrapper.setProps({ rows: [{ ...initialRows[0]!, description: 'external edit' }, initialRows[1]!] })
    await state.apply()
    expect(prepareRows).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(state.draftRows[0]!.description).toBe('first')
    wrapper.unmount()
  })
  it('applies mixed edits, additions and deletions as one event with untouched hidden fields', async () => {
    const { wrapper, state, prepareRows } = workspace()
    state.draftRows[1]!.description = 'edited'
    state.addCount = 2
    state.addDraftRows()
    state.draftRows[2]!.description = 'new-one'
    state.draftRows[3]!.description = 'new-two'
    state.onSelectionChange([state.draftRows[0]!])
    await state.deleteSelected()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    await state.apply()
    expect(prepareRows).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('apply-workspace')?.[0]?.[0]).toEqual([
      { line_key: 'L2', description: 'edited', hidden: 'keep-2' },
      { line_key: 'N1', description: 'new-one' }, { line_key: 'N2', description: 'new-two' },
    ])
    expect(initialRows[0]!.description).toBe('first')
    expect(initialRows[1]!.description).toBe('second')
    wrapper.unmount()
  })

  it('removes new drafts without requesting any primary keys', async () => {
    const { wrapper, state, prepareRows } = workspace()
    state.addDraftRows()
    state.onSelectionChange([state.draftRows[2]!])
    await state.deleteSelected()
    await state.close()
    expect(prepareRows).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    wrapper.unmount()
  })

  it('retains the draft and emits nothing when preparation fails', async () => {
    const prepare = vi.fn(async (): Promise<PreparedBatchRows> => { throw new Error('allocation failed') })
    const { wrapper, state } = workspace(prepare)
    state.addDraftRows()
    state.draftRows[2]!.description = 'new'
    await state.apply()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toBeUndefined()
    expect(state.draftRows).toHaveLength(3)
    wrapper.unmount()
  })
})
