import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import Workspace from '@platform-shared/batch/BatchWorkspace.vue'
import type { BatchEditableField } from '@platform-shared/batch/fields'

export const fields: BatchEditableField[] = [
  { field: 'caption', label: 'Caption', type: 'input', required: true, options: [] },
  { field: 'amount', label: 'Amount', type: 'inputNumber', required: false, options: [], min: 0, max: 100 },
  { field: 'active', label: 'Active', type: 'switch', required: false, options: [] },
]
export const rows = [
  { key: 'one', caption: 'A', amount: 1, active: true, hidden: 'keep' },
  { key: 'two', caption: 'B', amount: 2, active: true, hidden: 'keep' },
  { key: 'three', caption: 'C', amount: 3, active: true, hidden: 'keep' },
]
const passthrough = { template: '<div><slot name="header"/><slot/><slot name="footer"/></div>' }
export function workspace(extra: Record<string, any> = {}) {
  return mount(Workspace, { props: {
    visible: true, rows: structuredClone(rows), fields, selectedRowIds: ['one', 'two'],
    rules: fields.map(field => ({ type: field.type, field: field.field, title: field.label, props: { min: field.min, max: field.max, maxlength: field.maxlength } })),
    rowIdentity: (row: Record<string, unknown>) => String(row.key), defaultValueForField: () => null, formatError: String,
    ...extra,
  }, global: { stubs: {
    ElSelect: true, ElOption: true, ElDatePicker: true,
    ElAlert: true, ElDialog: passthrough, ElForm: passthrough, ElFormItem: { ...passthrough, methods: { validate: () => Promise.resolve(), clearValidate: () => {} } }, ElRow: passthrough, ElCol: passthrough, ElCard: passthrough,
    ElButton: { template: '<button><slot/></button>' },
    ElInputNumber: defineComponent({ name: 'ElInputNumber', props: ['modelValue', 'min', 'max'], emits: ['update:modelValue'], template: '<input/>' }),
    ElInput: defineComponent({ name: 'ElInput', props: ['modelValue', 'maxlength'], emits: ['update:modelValue'], template: '<input/>' }),
    ElSwitch: defineComponent({ name: 'ElSwitch', props: ['modelValue'], emits: ['update:modelValue'], template: '<input/>' }),
  } } })
}
export function state(wrapper: ReturnType<typeof workspace>) {
  return wrapper.vm as unknown as {
    values: Record<string, unknown>; intents: Record<string, unknown>; addCount: number
    setValue(name: string, value: unknown): void
    apply(): Promise<void>; close(): Promise<boolean>
  }
}
