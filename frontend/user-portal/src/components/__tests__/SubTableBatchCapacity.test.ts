import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import BatchWorkspace from '@platform-shared/batch/BatchWorkspace.vue'
import type { PreparedBatchRows } from '@/utils/subTableBatchAdd'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') } }))

interface State {
  draftRows: Array<Record<string, unknown>>
  page: number
  addCount: number
  addDraftRows(): void
  apply(): Promise<void>
  close(): Promise<boolean>
}

function workspace(count: number, prepareRows = vi.fn(async (rows: Array<Record<string, unknown>>): Promise<PreparedBatchRows> => ({
  rows: rows.map((row, index) => ({ ...row, line_key: `N${index}` })),
}))) {
  const rows = Array.from({ length: count }, (_, index) => ({ line_key: `L${index}`, description: 'original', hidden: index }))
  const wrapper = mount(BatchWorkspace, { props: {
    visible: true, rows, fields: [{ field: 'description', label: 'Description', type: 'input', options: [], required: true }],
    rowIdentity: row => String(row.line_key), showAdd: true, showDelete: true, prepareRows,
    defaultValueForField: () => 'fresh', formatError: error => String(error),
  }, global: { stubs: {
    ElDialog: { template: '<div><slot/><slot name="footer"/></div>' },
    ElTable: { name: 'ElTable', props: ['data'], template: '<div />' }, ElTableColumn: true,
    ElForm: { template: '<div><slot/></div>' }, ElFormItem: { template: '<div><slot/></div>' },
    ElButton: { template: '<button><slot/></button>' }, ElInputNumber: true, ElInput: true,
    ElPagination: true, ElDivider: true,
    ElSelect: { name: 'ElSelect', props: { persistent: { type: Boolean, default: true } }, template: '<div><slot/></div>' }, ElOption: true,
  } } })
  return { wrapper, state: wrapper.vm as unknown as State, rows, prepareRows }
}

describe('batch capacity and single-apply boundary', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('projects only twenty rows to the table even with a thousand existing rows', async () => {
    const { wrapper, state, rows } = workspace(1000)
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')).toEqual(rows.slice(0, 20))
    state.page = 50
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')).toEqual(rows.slice(980))
    wrapper.unmount()
  })

  it('applies exactly two hundred changed rows without losing hidden fields', async () => {
    const { wrapper, state, rows } = workspace(200)
    state.draftRows.forEach(row => { row.description = 'changed' })
    await state.apply()
    const applied = wrapper.emitted('apply')?.[0]?.[0] as Array<Record<string, unknown>>
    expect(applied).toHaveLength(200)
    expect(applied.every((row, index) => row.hidden === index && row.description === 'changed')).toBe(true)
    expect(rows.every(row => row.description === 'original')).toBe(true)
    wrapper.unmount()
  })

  it('reduces wide-table page size instead of mounting a thousand field inputs', async () => {
    const { wrapper, state, rows } = workspace(200)
    await wrapper.setProps({ fields: [
      { field: 'description', label: 'Description', type: 'input', options: [], required: true },
      ...Array.from({ length: 50 }, (_, index) => ({ field: `extra_${index}`, label: `Extra ${index}`, type: 'input', options: [], required: false })),
    ] })
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')).toEqual(rows.slice(0, 3))
    state.page = 67
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')).toEqual(rows.slice(198))
    wrapper.unmount()
  })

  it('does not eagerly mount every uniform-value dropdown in a wide form', async () => {
    const { wrapper } = workspace(200)
    await wrapper.setProps({ fields: Array.from({ length: 51 }, (_, index) => ({
      field: `extra_${index}`, label: `Extra ${index}`, type: 'input', options: [], required: false,
    })) })
    const modes = wrapper.findAllComponents({ name: 'ElSelect' })
    expect(modes).toHaveLength(51)
    expect(modes.every(mode => mode.props('persistent') === false)).toBe(true)
    wrapper.unmount()
  })

  it('uses the wide-table page size when focusing invalid and newly added rows', async () => {
    const { wrapper, state } = workspace(200)
    await wrapper.setProps({ fields: [
      { field: 'description', label: 'Description', type: 'input', options: [], required: true },
      ...Array.from({ length: 50 }, (_, index) => ({ field: `extra_${index}`, label: `Extra ${index}`, type: 'input', options: [], required: false })),
    ] })
    state.draftRows[199]!.description = ''
    await state.apply()
    expect(state.page).toBe(67)
    expect(wrapper.emitted('apply')).toBeUndefined()
    state.page = 1
    state.addDraftRows()
    expect(state.page).toBe(67)
    wrapper.unmount()
  })

  it('rejects two hundred and one affected rows without truncating the retained draft', async () => {
    const { wrapper, state, prepareRows } = workspace(201)
    state.draftRows.forEach(row => { row.description = 'changed' })
    await state.apply()
    expect(ElMessage.error).toHaveBeenCalledWith('subTable.batchLimit')
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    expect(prepareRows).not.toHaveBeenCalled()
    expect(state.draftRows).toHaveLength(201)
    wrapper.unmount()
  })

  it('counts edits and additions together rather than granting each a separate limit', async () => {
    const { wrapper, state, prepareRows } = workspace(1)
    state.draftRows[0]!.description = 'changed'
    state.addCount = 200
    state.addDraftRows()
    await state.apply()
    expect(ElMessage.error).toHaveBeenCalledWith('subTable.batchLimit')
    expect(prepareRows).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    wrapper.unmount()
  })

  it('prepares only once and refuses closing while Apply is pending', async () => {
    let finish!: (value: PreparedBatchRows) => void
    const prepareRows = vi.fn(() => new Promise<PreparedBatchRows>(resolve => { finish = resolve }))
    const { wrapper, state } = workspace(0, prepareRows)
    state.addDraftRows()
    const first = state.apply()
    await state.apply()
    expect(await state.close()).toBe(false)
    expect(prepareRows).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
    finish({ rows: [{ line_key: 'N0', description: 'fresh' }] })
    await first
    expect(wrapper.emitted('apply-workspace')).toHaveLength(1)
    wrapper.unmount()
  })
})
