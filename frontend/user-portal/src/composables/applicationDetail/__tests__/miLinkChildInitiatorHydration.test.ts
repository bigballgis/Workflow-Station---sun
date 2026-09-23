import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailMiHydration } from '../useApplicationDetailMiHydration'

/**
 * My Request dual-binding: a file widget with a subform must not adopt another
 * table's rows just because both carry the parent record's business key.
 * The other table stays under its own canonical store key.
 */
describe('hydrateMiLinkChildBindingsForInitiatorMyRequest', () => {
  const fileRows = [
    { id: 'File-000009', case_id: 'Case-000004', file_name: 'case file' },
    { id: 'File-000010', party_id: 'Party-000004', file_name: 'alice file' },
    { id: 'File-000011', party_id: 'Party-000005', file_name: 'bob file' },
  ]
  const partyRows = [
    { id: 'Party-000004', case_id: 'Case-000004', party_name: 'alice' },
    { id: 'Party-000005', case_id: 'Case-000004', party_name: 'bob' },
  ]

  function fileBinding(extra?: Record<string, unknown>) {
    return {
      bindingId: 50761,
      tableId: 50371,
      bindingType: 'SUB',
      designerTableName: 'p0_mf_file',
      tableName: 'P0 MF File',
      foreignKeyField: 'party_id',
      primaryKeyFields: ['id'],
      formFields: [{ key: 'file_name', type: 'input' }],
      columns: [
        { field: 'id' },
        { field: 'case_id' },
        { field: 'party_id' },
        { field: 'file_name' },
      ],
      data: fileRows.map(row => ({ ...row })),
      ...extra,
    }
  }

  function hydrate(binding: ReturnType<typeof fileBinding>, saved: Record<string, unknown>, tableByBindingId?: Map<number, number | null>) {
    const formData = ref<Record<string, unknown>>({ __subTables__: saved })
    const subTableBindings = ref([binding])
    const fns = createApplicationDetailMiHydration({
      formData,
      subTableBindings,
      previousForms: ref([]),
      nodeFormMap: ref(new Map()),
      lastBindingRelationTableMap: ref(tableByBindingId ?? new Map([[50761, 50371]])),
      isInitiatorMyRequestView: ref(true),
    } as never)
    fns.hydrateMiLinkChildBindingsForInitiatorMyRequest()
    return binding.data as Array<Record<string, unknown>>
  }

  it('does not adopt another table canonical slice that only shares the parent key', () => {
    const rows = hydrate(fileBinding(), {
      'dw:p0_mf_file': fileRows,
      'dw:p0_mf_party': partyRows,
    })
    expect(rows.map(row => row.id)).toEqual(['File-000009', 'File-000010', 'File-000011'])
  })

  it('still merges a same-table legacy binding-id slice', () => {
    const rows = hydrate(
      fileBinding(),
      {
        'dw:p0_mf_file': fileRows,
        '88001': [{ id: 'File-000012', case_id: 'Case-000004', file_name: 'sibling copy' }],
      },
      new Map([[50761, 50371], [88001, 50371]]),
    )
    expect(rows.map(row => row.id)).toContain('File-000012')
  })
})
