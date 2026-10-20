import { describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { workspace, state, rows } from './batchWorkspaceTestHelpers'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
describe('Batch Form input constraints', () => {
  it('passes the Assignment container style through a slot-based runtime adapter', () => {
    const wrapper = workspace({ fields: [], showAssign: true, rules: [{ type: 'miAssignment', input: false, style: { width: '421px', marginTop: '19px' } }] })
    const designedContainer = wrapper.find('[style*="421px"]')
    expect(designedContainer.exists()).toBe(true)
    expect(designedContainer.attributes('style')).toContain('margin-top: 19px')
  })
  it('preserves the developer-defined label width rather than applying a dialog override', () => {
    const wrapper = workspace({ options: { form: { labelWidth: '213px', labelPosition: 'right' } } })
    expect(wrapper.findComponent({ name: 'FormCreate' }).props('option').form).toMatchObject({ labelWidth: '213px', labelPosition: 'right' })
  })
  it('lets native conditional rules reveal their designed fields without treating defaults as edits', async () => {
    const wrapper = workspace({ rules: [{ type: 'input', field: 'caption', value: 'closed', control: [{ value: 'open', rule: [{ type: 'inputNumber', field: 'amount', value: 9 }] }] }] })
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'ElInputNumber' })).toHaveLength(0)
    wrapper.findComponent({ name: 'ElInput' }).vm.$emit('update:modelValue', 'open')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElInputNumber' }).props('modelValue')).toBe(9)
    expect(state(wrapper).intents).toEqual({ caption: { kind: 'set', value: 'open' } })
  })
  it('passes custom control styling and form options to the native renderer without forcing widths', () => {
    const rules = [{ type: 'inputNumber', field: 'amount', title: 'Units', style: { width: '173px', color: 'purple' }, props: { size: 'small', controlsPosition: 'right', step: 5 } }]
    const wrapper = workspace({ rules, options: { form: { labelPosition: 'top', size: 'small', hideRequiredAsterisk: true }, row: { gutter: 6 } } })
    const input = wrapper.findComponent({ name: 'ElInputNumber' })
    expect(input.attributes('style')).toContain('width: 173px')
    expect(input.attributes('step')).toBe('5')
    const renderer = wrapper.findComponent({ name: 'FormCreate' })
    expect(renderer.props('option')).toMatchObject({ form: { labelPosition: 'top', size: 'small', hideRequiredAsterisk: true }, row: { gutter: 6 }, submitBtn: false, resetBtn: false })
    expect(rules[0]!.type).toBe('inputNumber')
  })
  it('keeps designer defaults visible but does not write them until an input is changed', async () => {
    const wrapper = workspace({ rules: [{ type: 'input', field: 'caption', title: 'Caption', value: 'Designed default' }, { type: 'inputNumber', field: 'amount', value: 8 }] })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElInput' }).props('modelValue')).toBe('Designed default')
    expect(state(wrapper).intents).toEqual({})
    wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 0)
    await flushPromises()
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toEqual(rows.map((row, i) => i < 2 ? { ...row, amount: 0 } : row))
  })
  it('remounts from the design when reopened, without retaining values from a cancelled draft', async () => {
    const wrapper = workspace({ rules: [{ type: 'input', field: 'caption', title: 'Caption', value: 'Designed default' }] })
    await flushPromises()
    wrapper.findComponent({ name: 'ElInput' }).vm.$emit('update:modelValue', 'Cancelled draft')
    await flushPromises()
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ElInput' }).props('modelValue')).toBe('Designed default')
    expect(state(wrapper).intents).toEqual({})
  })
  it('does not add field intent controls or status paragraphs to the designed form', () => {
    const wrapper = workspace()
    expect(wrapper.find('.batch-edit-dialog__intent').exists()).toBe(false)
    expect(wrapper.find('p[role="status"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('subTable.batchEditDraftHint')
  })
  it('renders one numeric input with the configured bounds', () => {
    const wrapper = workspace()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs).toHaveLength(1)
    expect(inputs[0]!.props()).toMatchObject({ min: 0, max: 100 })
  })
  it('preserves input props from the designed rule', () => {
    const wrapper = workspace({ fields: [{ field: 'caption', label: 'Caption', type: 'input', required: false, options: [], maxlength: 12 }],
      rules: [{ type: 'input', field: 'caption', props: { type: 'textarea', rows: 3, placeholder: 'Write here', maxlength: 12 } }] })
    expect(wrapper.findComponent({ name: 'ElInput' }).props('maxlength')).toBe(12)
    expect(wrapper.findComponent({ name: 'ElInput' }).attributes()).toMatchObject({ type: 'textarea', rows: '3', placeholder: 'Write here' })
  })
})
