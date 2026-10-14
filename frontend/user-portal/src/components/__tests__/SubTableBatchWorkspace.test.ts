import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import BatchWorkspace from '../SubTableBatchEditDialog.vue'
import type { SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { PreparedBatchRows } from '@/utils/subTableBatchAdd'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
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
}

const capability: SubTableBatchCapability = {
  batchControlId: 'lines-grid', bindingId: '101', originalCrud: {},
  capabilities: { allowBatchAdd: true, allowBatchDelete: true, allowBatchEdit: true, allowBatchAssign: false },
  batchForm: { schemaVersion: 1, rule: [{ type: 'input', field: 'description', required: true }] },
}
const initialRows = [{ line_key: 'L1', description: 'first', hidden: 'keep-1' }, { line_key: 'L2', description: 'second', hidden: 'keep-2' }]

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
  } } })
  return { wrapper, state: wrapper.vm as unknown as WorkspaceState, prepareRows }
}

describe('batch workspace Add/Delete transaction', () => {
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
