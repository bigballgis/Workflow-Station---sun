import { describe, expect, it, vi } from 'vitest'
import { workspace, state } from './batchWorkspaceTestHelpers'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
describe('Batch Form capacity and add', () => {
  it('rejects an outer selection larger than 200', async () => {
    const rows = Array.from({ length: 201 }, (_, i) => ({ key: String(i), caption: 'A' }))
    const wrapper = workspace({ rows, selectedRowIds: rows.map(row => row.key) })
    state(wrapper).setValue('caption', 'new')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('creates N independent rows from one form with default values', async () => {
    const prepare = vi.fn(async rows => ({ rows: rows.map((row: Record<string, unknown>, i: number) => ({ ...row, key: 'added-' + i })) }))
    const wrapper = workspace({ mode: 'add', selectedRowIds: [], defaultValueForField: () => null, prepareRows: prepare })
    state(wrapper).addCount = 2
    state(wrapper).setValue('caption', 'New')
    state(wrapper).setValue('amount', 0)
    await state(wrapper).apply()
    expect(prepare).toHaveBeenCalledWith([{ caption: 'New', amount: 0, active: null }, { caption: 'New', amount: 0, active: null }], '')
    expect(wrapper.emitted('apply-workspace')).toHaveLength(1)
  })
  it('rejects duplicate prepared identities without applying', async () => {
    const wrapper = workspace({ mode: 'add', selectedRowIds: [], prepareRows: async () => ({ rows: [{ key: 'one' }] }) })
    state(wrapper).setValue('caption', 'New')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
  })
})
