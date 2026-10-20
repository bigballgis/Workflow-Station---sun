import { describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import { workspace, state, rows } from './batchWorkspaceTestHelpers'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) } }))
describe('selected-row Batch Form', () => {
  it('honors disabled layout ancestors when collecting user input', async () => {
    const wrapper = workspace({ rules: [{ type: 'elCard', props: { disabled: true }, children: [{ type: 'input', field: 'caption', title: 'Caption' }] }] })
    state(wrapper).setValue('caption', 'must not write')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('starts unchanged, including fields with designer defaults', async () => {
    const wrapper = workspace({ defaultValueForField: () => 'default' })
    expect(state(wrapper).values).toEqual({})
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toEqual([[false]])
  })
  it('fills once and changes only selected rows, preserving hidden values', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('caption', 'Shared')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toEqual(rows.map((row, i) => i < 2 ? { ...row, caption: 'Shared' } : row))
    expect(wrapper.emitted('apply')![0]![1]).toMatchObject({ uniformPatches: [{ rowIds: ['one', 'two'], fields: { caption: { kind: 'set', value: 'Shared' } } }] })
  })
  it('preserves zero and false as explicit changes', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', 0); state(wrapper).setValue('active', false)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toMatchObject([{ amount: 0, active: false }, { amount: 0, active: false }, { amount: 3, active: true }])
  })
  it('distinguishes a cleared field from untouched fields', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', null)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toMatchObject([{ caption: 'A', amount: null }, { caption: 'B', amount: null }, { caption: 'C', amount: 3 }])
  })
  it('rejects clearing a required field atomically', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('caption', null)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(ElMessage.error).toHaveBeenCalledWith('Error: subTable.batchInvalidField')
  })
  it('does not change protected fields even if an update event is forged', async () => {
    const wrapper = workspace({ fieldWriteModes: { caption: 'READONLY' }, fieldDefinitions: [{ fieldName: 'amount', isPrimaryKey: true }] })
    state(wrapper).setValue('caption', 'bad'); state(wrapper).setValue('amount', 8)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('passes only the outer selection to assignment collection', async () => {
    const collect = vi.fn(selected => ({ rows: selected, change: undefined }))
    const wrapper = workspace({ showAssign: true, collectAssignment: collect, assignmentHasChanges: () => true })
    await state(wrapper).apply()
    expect(collect).toHaveBeenCalledWith(rows.slice(0, 2))
  })
  it('allows ordinary edits when an untouched assignment block is not mounted in a lazy tab', async () => {
    const collect = vi.fn(() => { throw new Error('editor is not mounted') })
    const wrapper = workspace({ showAssign: true, collectAssignment: collect, assignmentHasChanges: () => false })
    state(wrapper).setValue('caption', 'Ordinary edit')
    await state(wrapper).apply()
    expect(collect).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply')).toHaveLength(1)
  })
  it('rejects changes to a selected record while the form is open', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', 7)
    await wrapper.setProps({ rows: rows.map((row, i) => i === 0 ? { ...row, amount: 99 } : row) })
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('preserves concurrent updates to unselected rows', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', 7)
    await wrapper.setProps({ rows: rows.map((row, i) => i === 2 ? { ...row, caption: 'Other edit' } : row) })
    await state(wrapper).apply()
    expect((wrapper.emitted('apply')![0]![0] as typeof rows)[2]!.caption).toBe('Other edit')
  })
  it('keeps designed field columns instead of flattening their spans', () => {
    const wrapper = workspace({ rules: [
      { type: 'input', field: 'caption', col: { span: 12 } },
      { type: 'inputNumber', field: 'amount', col: { span: 12, offset: 0 } },
    ] })
    expect(wrapper.findAll('[span="12"]')).toHaveLength(2)
  })
  it('renders the configured layout without row tables or internal checkboxes', () => {
    const wrapper = workspace({ rules: [{ type: 'elCard', props: { header: 'Designed card' }, children: [{ type: 'input', field: 'caption', title: 'Caption' }, { type: 'miAssignment' }] }] })
    expect(wrapper.findComponent({ name: 'BatchDesignedForm' }).exists()).toBe(true)
    expect(wrapper.findAll('input')).toHaveLength(1)
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('[type="checkbox"]').exists()).toBe(false)
  })
})
