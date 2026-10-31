import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useSubTableLinkFormDialog } from '../useSubTableLinkFormDialog'
import type { SubTableBinding, SubTableFieldProps } from '../subTableFieldTypes'

function dialog(editable = true) {
  const binding: SubTableBinding = { bindingId: 42, bindingMode: 'EDITABLE', bindingType: 'SUB', tableType: 'SUB',
    tableDescription: '', tableName: 'Rows', columns: [], data: [], formFields: [
    { key: 'assignment', label: '', type: 'miAssignment', children: [
      { key: 'person', label: 'Person', type: 'lookup', sourceRule: { type: 'lookup', field: 'person', on: {
        change: '$FNX:api.setValue("email", value ? value.email : null);',
      } } },
      { key: 'email', label: 'Email', type: 'text' },
    ] },
  ] }
  const api = useSubTableLinkFormDialog({ editable, linkedSubTableBindings: [binding] } as SubTableFieldProps,
    ref([]), () => {}, key => key, { resolveSubTableRowPk: () => null, filterRowsByMiLinkFormParent: (_parent, rows) => rows })
  api.activeLinkColumn.value = { field: 'details', label: 'Details', props: { boundSubTableBindingId: 42 } }
  api.linkedFormData.value = { email: '' }
  return api
}
describe('Details field change events', () => {
  it('fills an initially empty target from the selected Lookup object and clears it on deselect', () => {
    const api = dialog()
    api.updateLinkedFormField('person', { id: 'one', email: 'one@example.test' })
    expect(api.linkedFormData.value.email).toBe('one@example.test')
    api.updateLinkedFormField('person', null)
    expect(api.linkedFormData.value.email).toBeNull()
  })
  it('keeps separate dialogs isolated and does not execute edit scripts in a readonly view', () => {
    const first = dialog(), other = dialog(), readonly = dialog(false)
    first.updateLinkedFormField('person', { id: 'one', email: 'one@example.test' })
    expect(other.linkedFormData.value.email).toBe('')
    readonly.updateLinkedFormField('person', { id: 'one', email: 'one@example.test' })
    expect(readonly.linkedFormData.value.email).toBe('')
  })
})
