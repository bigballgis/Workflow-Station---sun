import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import Workspace from '@platform-shared/batch/BatchWorkspace.vue'
import type { BatchEditableField } from '@platform-shared/batch/fields'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))

function workspace(field: BatchEditableField) {
  return mount(Workspace, { props: {
    visible: true, rows: [{ key: 'one', metric: 2, note: 'ok' }], fields: [field],
    rowIdentity: row => String(row.key), defaultValueForField: () => null, formatError: String,
  }, global: { stubs: {
    ElDialog: { template: '<div><slot name="header"/><slot/><slot name="footer"/></div>' },
    ElTable: { template: '<div><slot/></div>' },
    ElTableColumn: { template: '<div><slot :row="{metric:2,note:\'ok\'}"/></div>' },
    ElForm: { template: '<div><slot/></div>' }, ElFormItem: { template: '<div><slot/></div>' },
    ElInputNumber: defineComponent({ name: 'ElInputNumber', props: ['modelValue', 'min', 'max'], template: '<input/>' }),
    ElInput: defineComponent({ name: 'ElInput', props: ['modelValue', 'maxlength'], template: '<input/>' }),
    ElButton: true, ElDivider: true, ElSelect: true, ElOption: true, ElPagination: true,
  } } })
}

describe('shared batch input constraints', () => {
  it('applies numeric zero minimum and maximum to both row and uniform editors', async () => {
    const wrapper = workspace({ field: 'metric', label: 'Metric', type: 'inputNumber', required: true, options: [], min: 0, max: 10 })
    const state = wrapper.vm as unknown as { uniformMode: Record<string, string> }
    state.uniformMode.metric = 'set'
    await nextTick()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs).toHaveLength(2)
    for (const input of inputs) expect(input.props()).toMatchObject({ min: 0, max: 10 })
    wrapper.unmount()
  })

  it('applies saved text maxlength to both editors without imposing a fabricated default', async () => {
    const wrapper = workspace({ field: 'note', label: 'Note', type: 'input', required: false, options: [], maxlength: 12 })
    const state = wrapper.vm as unknown as { uniformMode: Record<string, string> }
    state.uniformMode.note = 'set'
    await nextTick()
    const inputs = wrapper.findAllComponents({ name: 'ElInput' })
    expect(inputs).toHaveLength(2)
    for (const input of inputs) expect(input.props('maxlength')).toBe(12)
    wrapper.unmount()
    const unconstrained = workspace({ field: 'note', label: 'Note', type: 'input', required: false, options: [] })
    expect(unconstrained.findComponent({ name: 'ElInput' }).props('maxlength')).toBeUndefined()
    unconstrained.unmount()
  })
})
