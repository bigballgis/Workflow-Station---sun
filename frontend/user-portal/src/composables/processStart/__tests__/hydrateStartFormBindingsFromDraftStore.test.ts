import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createProcessStartSubTables } from '../useProcessStartSubTables'
import type { ProcessStartSubTableBinding } from '../useProcessStartState'

function correspondenceBinding(
  extra: Partial<ProcessStartSubTableBinding> = {},
): ProcessStartSubTableBinding {
  return {
    bindingId: 1133,
    tableId: 392,
    bindingType: 'table',
    bindingMode: 'subtable',
    tableName: 'atm_correspondence',
    designerTableName: 'atm_correspondence',
    tableType: 'dw',
    tableDescription: '',
    columns: [],
    data: [],
    ...extra,
  }
}

function createApi(bindings: ProcessStartSubTableBinding[]) {
  const subTableBindings = ref(bindings)
  return {
    subTableBindings,
    ...createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings,
      deriveColumnsFromBinding: () => [],
    }),
  }
}

describe('hydrateStartFormBindingsFromDraftStore', () => {
  it('restores rows from the canonical dw: key written by save draft', () => {
    const { subTableBindings, hydrateStartFormBindingsFromDraftStore } = createApi([
      correspondenceBinding(),
    ])
    hydrateStartFormBindingsFromDraftStore({
      'dw:atm_correspondence': [{ correspondence_id: 'Corr-000047' }],
    })
    expect(subTableBindings.value[0].data.map(r => r.correspondence_id))
      .toEqual(['Corr-000047'])
  })

  it('falls back to binding-id keys for older drafts', () => {
    const { subTableBindings, hydrateStartFormBindingsFromDraftStore } = createApi([
      correspondenceBinding(),
    ])
    hydrateStartFormBindingsFromDraftStore({
      1133: [{ correspondence_id: 'Corr-000001' }],
    })
    expect(subTableBindings.value[0].data.map(r => r.correspondence_id))
      .toEqual(['Corr-000001'])
  })

  it('restores the same canonical file rows onto both dual-filter bindings', () => {
    const caseFiles: ProcessStartSubTableBinding = {
      bindingId: 50705,
      tableId: 50348,
      bindingType: 'SUB',
      bindingMode: 'EDITABLE',
      tableName: 'Files',
      designerTableName: 'p0_dual_file',
      tableType: 'dw',
      tableDescription: '',
      columns: [],
      data: [],
      primaryKeyFields: ['id'],
      filterFkRefTableId: 50100,
      filterFkFieldName: 'case_id',
    }
    const partyFiles: ProcessStartSubTableBinding = {
      ...caseFiles,
      bindingId: 50706,
      filterFkRefTableId: 50200,
      filterFkFieldName: 'party_id',
    }
    const parties: ProcessStartSubTableBinding = {
      bindingId: 50704,
      tableId: 50200,
      bindingType: 'SUB',
      bindingMode: 'EDITABLE',
      tableName: 'Parties',
      designerTableName: 'p0_dual_party',
      tableType: 'dw',
      tableDescription: '',
      columns: [],
      data: [],
      primaryKeyFields: ['id'],
      fieldDefinitions: [{ fieldName: 'id', isPrimaryKey: true }] as never,
      filterFkRefTableId: 50100,
      filterFkFieldName: 'case_id',
    }
    const subTableBindings = ref([caseFiles, partyFiles, parties])
    const { hydrateStartFormBindingsFromDraftStore } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings,
      deriveColumnsFromBinding: () => [],
    })
    hydrateStartFormBindingsFromDraftStore({
      'dw:p0_dual_file': [
        { id: 'X', case_id: 'C1', title: 'case-doc' },
        { id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' },
      ],
      'dw:p0_dual_party': [{ id: 'P-A' }],
    })
    expect(subTableBindings.value[0].data.map(r => r.id)).toEqual(['X', 'Y'])
    expect(subTableBindings.value[1].data.map(r => r.id)).toEqual(['X', 'Y'])
  })
})
