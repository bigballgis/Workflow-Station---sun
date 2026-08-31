import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'
import type { FormField } from '../formRendererHelpers'

// Mock i18n
vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  }
})

// Heavy real children (SubTableField, SubTableInlineForm's own internals, FieldRenderer) are
// stubbed — this test only asserts PortalFormFields' own dispatch/prop-wiring, not their behavior.
const globalStubs = {
  SubTableField: { template: '<div class="stub-sub-table-field" :data-binding-type="bindingType" />', props: ['bindingType', 'editable', 'allowAdd', 'allowEdit', 'allowDelete'] },
  FieldRenderer: true,
  ElCol: { template: '<div><slot /></div>' },
  ElFormItem: { template: '<div><slot /></div>' },
}

describe('PortalFormFields — inlineSubForm dispatch and ACTION binding-type wiring', () => {
  it('field.type === "subTable" passes the resolved binding bindingType through to SubTableField (not blank/undefined)', () => {
    const fields: FormField[] = [
      { key: 'meetingRemark', type: 'subTable', _bindingId: 42, span: 24 } as unknown as FormField,
    ]
    const wrapper = mount(PortalFormFields, {
      props: {
        fields,
        model: {},
        editable: true,
        subTableBindings: [
          {
            bindingId: 42,
            tableName: 'meeting_remark',
            columns: [],
            data: [],
            bindingType: 'ACTION',
          },
        ],
      },
      global: { stubs: globalStubs },
    })

    const subTableField = wrapper.find('.stub-sub-table-field')
    expect(subTableField.exists()).toBe(true)
    expect(subTableField.attributes('data-binding-type')).toBe('ACTION')
  })

  it('field.type === "inlineSubForm" renders SubTableInlineForm, not the generic empty-shell fallback', async () => {
    const fields: FormField[] = [
      { key: 'inlineWidget', type: 'inlineSubForm', _bindingId: 99, span: 24 } as unknown as FormField,
    ]
    const wrapper = mount(PortalFormFields, {
      props: {
        fields,
        model: {},
        editable: true,
        subTableBindings: [
          {
            bindingId: 99,
            tableName: 'other_sub_table',
            columns: [],
            formFields: [],
            data: [{ id: 1, note: 'hello' }],
            bindingMode: 'EDITABLE',
          },
        ],
      },
      global: {
        stubs: {
          ...globalStubs,
          // SubTableInlineForm is dynamically imported (defineAsyncComponent) to break the
          // PortalFormFields <-> SubTableInlineForm module cycle — assert on its stub name.
          SubTableInlineForm: { template: '<div class="stub-inline-sub-form" />' },
        },
      },
    })
    await wrapper.vm.$nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.stub-inline-sub-form').exists()).toBe(true)
    // Must NOT fall through to the generic el-form-item/FieldRenderer empty-shell fallback.
    expect(wrapper.findComponent({ name: 'ElFormItem' }).exists()).toBe(false)
  })

  it('threads fieldPermissions down to the nested inlineSubForm (Link Form dialog / nested-in-a-dialog path) so its fields get the same READONLY enforcement as the top-level FormRendererFields path', async () => {
    const fields: FormField[] = [
      { key: 'inlineWidget', type: 'inlineSubForm', _bindingId: 99, span: 24 } as unknown as FormField,
    ]
    const wrapper = mount(PortalFormFields, {
      props: {
        fields,
        model: {},
        editable: true,
        fieldPermissions: { '99:name': 'READONLY' },
        subTableBindings: [
          {
            bindingId: 99,
            tableName: 'other_sub_table',
            columns: [],
            formFields: [{ key: 'name', label: 'Name', type: 'text' }],
            data: [{ id: 1, name: 'hello' }],
            bindingMode: 'EDITABLE',
          },
        ],
      },
      global: {
        stubs: {
          ...globalStubs,
          SubTableInlineForm: {
            name: 'SubTableInlineForm',
            props: ['fieldPermissions'],
            template: '<div class="stub-inline-sub-form" />',
          },
        },
      },
    })
    await wrapper.vm.$nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    const stub = wrapper.findComponent({ name: 'SubTableInlineForm' })
    expect(stub.exists()).toBe(true)
    expect(stub.props('fieldPermissions')).toEqual({ '99:name': 'READONLY' })
  })

  it('threads the bound sub-form formOptions and dialogColumns so field/Form JS can run', async () => {
    const formOptions = { onChange: '$FNX:\napi.hidden(true, "name")' }
    const dialogColumns = [{ field: 'name', label: 'Name' }]
    const fields: FormField[] = [
      { key: 'inlineWidget', type: 'inlineSubForm', _bindingId: 99, span: 24 } as unknown as FormField,
    ]
    const wrapper = mount(PortalFormFields, {
      props: {
        fields,
        model: {},
        editable: true,
        subTableBindings: [
          {
            bindingId: 99,
            tableName: 'other_sub_table',
            columns: [],
            formFields: [{ key: 'name', label: 'Name', type: 'text' }],
            formOptions,
            dialogColumns,
            data: [{ id: 1, name: 'hello' }],
            bindingMode: 'EDITABLE',
          },
        ],
      },
      global: {
        stubs: {
          ...globalStubs,
          SubTableInlineForm: {
            name: 'SubTableInlineForm',
            props: ['formOptions', 'dialogColumns'],
            template: '<div class="stub-inline-sub-form" />',
          },
        },
      },
    })
    await wrapper.vm.$nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    const stub = wrapper.findComponent({ name: 'SubTableInlineForm' })
    expect(stub.exists()).toBe(true)
    expect(stub.props('formOptions')).toEqual(formOptions)
    expect(stub.props('dialogColumns')).toEqual(dialogColumns)
  })
})
