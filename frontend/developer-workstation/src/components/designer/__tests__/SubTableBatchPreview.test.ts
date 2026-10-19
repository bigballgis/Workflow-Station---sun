import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import SubTableBatchPreview from '../SubTableBatchPreview.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { info: vi.fn(), error: vi.fn() } }))
const definition = { batchControlId: 'grid', bindingId: '21', invalid: false, fieldWriteModes: {},
  flags: { add: true, edit: true, delete: true, assign: false }, rule: [{ type: 'input', field: 'caption' }] }
const workspace = defineComponent({ name: 'BatchWorkspace', props: ['visible', 'rows', 'showAdd', 'showDelete', 'prepareRows'],
  emits: ['apply', 'apply-workspace', 'update:visible'], template: '<div><slot name="assignment" :rows="rows"/></div>' })

function preview(rows: Array<Record<string, unknown>> = []) {
  return mount(SubTableBatchPreview, { props: { definition, modelValue: rows,
    config: { columns: [], bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'structuralFk', fieldDefinitions: [] } },
  global: { stubs: { BatchWorkspace: workspace, BatchAssignmentEditor: true, DesignerHelpLink: true,
    ElTooltip: { template: '<span><slot/></span>' }, ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' } } } })
}

describe('local shared batch preview host', () => {
  it('creates labelled local sample rows without writing back, and cancel leaves source unchanged', async () => {
    const wrapper = preview()
    await wrapper.get('button').trigger('click')
    const shared = wrapper.findComponent(workspace)
    expect(shared.props('rows')).toHaveLength(2)
    expect(shared.props('rows')[0].platformRowUuid).not.toBe(shared.props('rows')[1].platformRowUuid)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    shared.vm.$emit('update:visible', false)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
  it('applies only to the parent preview after explicit shared apply, preserving hidden columns', async () => {
    const original = [{ key: 'one', caption: 'original', hidden: 'keep' }]
    const wrapper = preview(original)
    await wrapper.get('button').trigger('click')
    const shared = wrapper.findComponent(workspace)
    const rows = shared.props('rows').map((row: Record<string, unknown>) => ({ ...row, caption: 'changed' }))
    shared.vm.$emit('apply', rows, {})
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual(rows)
    expect(original).toEqual([{ key: 'one', caption: 'original', hidden: 'keep' }])
    expect(wrapper.emitted('update:primaryFormData')).toBeUndefined()
    wrapper.unmount()
  })
  it('does not offer membership mutations for an MI participant binding', async () => {
    const wrapper = preview()
    await wrapper.setProps({ config: { columns: [], bindingType: 'SUB', bindingMode: 'EDITABLE', bindingLinkMode: 'miParticipantRow' } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.findComponent(workspace).props('showAdd')).toBe(false)
    expect(wrapper.findComponent(workspace).props('showDelete')).toBe(false)
    wrapper.unmount()
  })
})
