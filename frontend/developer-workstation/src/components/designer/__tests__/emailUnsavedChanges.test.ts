import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import i18n from '@/i18n'
import { ElMessageBox } from 'element-plus'
import EmailMonitorDesigner from '../EmailMonitorDesigner.vue'
import EmailTemplateDesigner from '../EmailTemplateDesigner.vue'
import EmailExtractionWizard from '../email/EmailExtractionWizard.vue'
import type { ExtractionFieldRule } from '@/api/emailMonitor'
import type { DirtyDesigner } from '@/composables/useDesignerDraft'

vi.mock('@/api/emailMonitor', () => ({ emailMonitorApi: { list: vi.fn(async () => ({ data: [] })) } }))
vi.mock('@/api/connection', () => ({ connectionApi: { list: vi.fn(async () => ({ data: [] })) } }))
vi.mock('@/api/emailTemplate', () => ({ emailTemplateApi: { list: vi.fn(async () => ({ data: [] })) } }))
vi.mock('@/composables/email/useProcessFormSubBindings', async () => {
  const { ref } = await import('vue')
  return { useProcessFormSubBindings: () => ({ loading: ref(false), options: ref([{ id: 21, tableName: 'test_lines', tableDescription: 'Test lines', bindingType: 'SUB' }]), fieldsByBindingId: ref({}), mainFieldOptions: ref([]) }) }
})
vi.mock('@/composables/email/useEmailTemplateVariables', async () => {
  const { ref } = await import('vue')
  return {
    EMAIL_SUBJECT_VAR_EXAMPLE: '${name}', resolveEmailVariableGroupLabel: (label: string) => label,
    useEmailTemplateVariables: () => ({ groups: ref([]), loading: ref(false), load: vi.fn(async () => undefined) }),
  }
})
vi.mock('element-plus', async original => ({
  ...await original<typeof import('element-plus')>(),
  ElMessageBox: { confirm: vi.fn() },
}))

const Dialog = defineComponent({
  props: ['modelValue'],
  setup(props, { slots }) { return () => props.modelValue ? h('section', [slots.header?.(), slots.default?.(), slots.footer?.()]) : null },
})
const Button = defineComponent({ setup(_, { slots }) { return () => h('button', slots.default?.()) } })
const Slot = defineComponent({ setup(_, { slots }) { return () => h('div', slots.default?.()) } })
const Input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input />' })
const BodyEditor = defineComponent({
  name: 'EmailBodySplitEditor', props: ['modelValue', 'mode'],
  emits: ['update:modelValue', 'update:mode', 'initialized'], template: '<div />',
})
const Mapping = defineComponent({ name: 'EmailFieldMappingTable', props: ['fields'], emits: ['add-field'], template: '<div />' })
const global = {
  plugins: [i18n],
  directives: { loading: {} },
  stubs: {
    ElDialog: Dialog, ElButton: Button, ElForm: Slot, ElFormItem: Slot, ElTabs: Slot, ElTabPane: Slot,
    ElInput: Input, ElSelect: Slot, ElOption: true, ElOptionGroup: Slot, ElCheckbox: true, ElSwitch: true,
    ElInputNumber: true, ElDivider: Slot, ElAlert: Slot, ElIcon: Slot, ElTag: Slot, ElEmpty: true, ElTable: Slot, ElTableColumn: true,
    DesignerListTable: true, DesignerHelpLink: true, SystemInitiatorSelect: true, SubTableBindingSelect: true,
    EmailFieldMappingTable: Mapping, EmailBodySplitEditor: BodyEditor,
  },
}
enableAutoUnmount(afterEach)
beforeEach(() => { vi.clearAllMocks() })

describe('email designer unsaved drafts', () => {
  it('detects unfinished extraction rules and retains them when the leave warning is dismissed', async () => {
    const wrapper = mount(EmailMonitorDesigner, { props: { functionUnitId: 1 }, global })
    await flushPromises()
    await wrapper.get('.designer-toolbar button').trigger('click')
    const handle = wrapper.vm as unknown as DirtyDesigner
    expect(await handle.hasUnsavedChanges()).toBe(false)
    const mapping = wrapper.getComponent(Mapping)
    mapping.vm.$emit('add-field')
    await flushPromises()
    const fields = mapping.props('fields') as ExtractionFieldRule[]
    fields[0].label = 'Customer reference: '
    await flushPromises()
    expect(wrapper.getComponent(EmailExtractionWizard).vm.buildRules()).toEqual({})
    expect(await handle.hasUnsavedChanges()).toBe(true)
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('close')
    await wrapper.findAllComponents(Button).find(button => button.text() === 'Cancel')!.trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledOnce()
    expect(wrapper.findComponent(Mapping).exists()).toBe(true)
    expect((wrapper.getComponent(Mapping).props('fields') as ExtractionFieldRule[])[0].label).toBe('Customer reference: ')
    await handle.discardChanges()
    await flushPromises()
    expect(wrapper.findComponent(Mapping).exists()).toBe(false)
  })

  it('tracks unfinished sub-table blocks independently of the cleaned persistence payload', async () => {
    const wrapper = mount(EmailExtractionWizard, {
      props: { functionUnitId: 1, modelValue: {} }, global,
    })
    await flushPromises()
    expect(wrapper.vm.hasUnsavedChanges()).toBe(false)
    expect(wrapper.vm.hasIncompleteRules()).toBe(false)
    await wrapper.findAllComponents(Button).find(button => button.text() === 'Add Sub-table')!.trigger('click')
    await flushPromises()
    expect(wrapper.vm.buildRules()).toEqual({})
    expect(wrapper.vm.hasUnsavedChanges()).toBe(true)
    expect(wrapper.vm.hasIncompleteRules()).toBe(true)
  })

  it('treats initial rich-text normalization as pristine', async () => {
    const wrapper = mount(EmailTemplateDesigner, { props: { functionUnitId: 1 }, global })
    await flushPromises()
    await wrapper.get('.designer-toolbar button').trigger('click')
    await flushPromises()
    const editor = wrapper.getComponent(BodyEditor)
    editor.vm.$emit('update:modelValue', '<p><br></p>')
    editor.vm.$emit('initialized', '<p><br></p>')
    await flushPromises()
    expect(await (wrapper.vm as unknown as DirtyDesigner).hasUnsavedChanges()).toBe(false)
    await wrapper.findAllComponents(Button).find(button => button.text() === 'Cancel')!.trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('retains name edits made before the asynchronous body editor finishes initialization', async () => {
    const wrapper = mount(EmailTemplateDesigner, { props: { functionUnitId: 1 }, global })
    await flushPromises()
    await wrapper.get('.designer-toolbar button').trigger('click')
    await flushPromises()
    wrapper.findAllComponents(Input)[0].vm.$emit('update:modelValue', 'Changed name')
    const editor = wrapper.getComponent(BodyEditor)
    editor.vm.$emit('update:modelValue', '<p><br></p>')
    editor.vm.$emit('initialized', '<p><br></p>')
    await flushPromises()
    expect(await (wrapper.vm as unknown as DirtyDesigner).hasUnsavedChanges()).toBe(true)
  })

  it('does not rebase body changes on a later Visual editor remount', async () => {
    const wrapper = mount(EmailTemplateDesigner, { props: { functionUnitId: 1 }, global })
    await flushPromises()
    await wrapper.get('.designer-toolbar button').trigger('click')
    await flushPromises()
    const editor = wrapper.getComponent(BodyEditor)
    editor.vm.$emit('update:modelValue', '<p><br></p>')
    editor.vm.$emit('initialized', '<p><br></p>')
    editor.vm.$emit('update:modelValue', '<p>User edit</p>')
    editor.vm.$emit('initialized', '<p>User edit</p>')
    await flushPromises()
    expect(await (wrapper.vm as unknown as DirtyDesigner).hasUnsavedChanges()).toBe(true)
  })
})
