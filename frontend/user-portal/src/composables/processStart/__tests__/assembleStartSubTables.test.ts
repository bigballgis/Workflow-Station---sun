import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createProcessStartSubTables } from '../useProcessStartSubTables'
import type { ProcessStartSubTableBinding } from '../useProcessStartState'

function fileBinding(
  extra: Partial<ProcessStartSubTableBinding> = {},
): ProcessStartSubTableBinding {
  return {
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
    ...extra,
  }
}

describe('assembleStartSubTables', () => {
  it('puts dual-binding scopes beside the payload, not inside formData', () => {
    const caseFiles = fileBinding({
      data: [
        { id: 'X', case_id: 'C1', title: 'case-doc' },
        { id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' },
      ],
    })
    const partyFiles = fileBinding({
      bindingId: 50706,
      filterFkRefTableId: 50200,
      filterFkFieldName: 'party_id',
      data: [{ id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' }],
    })
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
      data: [{ id: 'P-A' }],
      primaryKeyFields: ['id'],
      fieldDefinitions: [{ fieldName: 'id', isPrimaryKey: true }] as never,
      filterFkRefTableId: 50100,
      filterFkFieldName: 'case_id',
    }
    const formData = ref<Record<string, unknown>>({ id: 'C1' })
    const primaryTableBinding = ref({
      tableId: 50100,
      primaryKeyFields: ['id'],
      fieldDefinitions: [{ fieldName: 'id', isPrimaryKey: true }],
    })
    const { assembleStartSubTables } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings: ref([caseFiles, partyFiles, parties]),
      formData,
      primaryTableBinding,
      deriveColumnsFromBinding: () => [],
    })

    const assembled = assembleStartSubTables()
    expect(assembled.subTables['dw:p0_dual_file']).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 'X' }),
      expect.objectContaining({ id: 'Y' }),
    ]))
    expect(assembled.subTableBindingScopes).toEqual([
      {
        bindingId: '50705',
        storeKey: 'dw:p0_dual_file',
        rowKeys: [{ id: 'X' }, { id: 'Y' }],
        emptied: false,
      },
      {
        bindingId: '50706',
        storeKey: 'dw:p0_dual_file',
        rowKeys: [{ id: 'Y' }],
        emptied: false,
      },
    ])
    expect(assembled.emptiedSubTableKeys).toEqual([])
    expect(formData.value.emptiedSubTableKeys).toBeUndefined()
    expect(formData.value.subTableBindingScopes).toBeUndefined()
    expect(assembled.subTables.emptiedSubTableKeys).toBeUndefined()
    expect(assembled.subTables.subTableBindingScopes).toBeUndefined()
  })

  it('omits scopes when each table has a single binding (V1)', () => {
    const { assembleStartSubTables } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings: ref([fileBinding({ data: [{ id: 'X', case_id: 'C1' }] })]),
      formData: ref({ id: 'C1' }),
      primaryTableBinding: ref({ tableId: 50100, primaryKeyFields: ['id'] }),
      deriveColumnsFromBinding: () => [],
    })
    const assembled = assembleStartSubTables()
    expect(assembled.subTableBindingScopes).toEqual([])
    expect(Object.keys(assembled.subTables)).toEqual(['dw:p0_dual_file'])
  })

  it('uses frozen designer table names when catalog bindings omit runtime table IDs', () => {
    const caseFiles = fileBinding({
      tableId: null,
      filterFkRefTableId: null,
      filterFkRefTableName: 'p0_dual_case',
      data: [
        { id: 'X', case_id: 'C1', title: 'case-doc' },
        { id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' },
      ],
    })
    const partyFiles = fileBinding({
      bindingId: 50706,
      tableId: null,
      filterFkRefTableId: null,
      filterFkRefTableName: 'p0_dual_party',
      filterFkFieldName: 'party_id',
      data: [{ id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' }],
    })
    const parties = fileBinding({
      bindingId: 50704,
      tableId: null,
      tableName: 'P0 Dual Party',
      designerTableName: 'p0_dual_party',
      filterFkRefTableId: null,
      filterFkRefTableName: 'p0_dual_case',
      data: [{ id: 'P-A' }],
    })
    const { assembleStartSubTables } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings: ref([caseFiles, partyFiles, parties]),
      formData: ref({ id: 'C1' }),
      primaryTableBinding: ref({
        tableId: null,
        tableName: 'p0_dual_case',
        primaryKeyFields: ['id'],
      }),
      deriveColumnsFromBinding: () => [],
    })

    expect(assembleStartSubTables().subTableBindingScopes).toEqual([
      expect.objectContaining({ bindingId: '50705', rowKeys: [{ id: 'X' }, { id: 'Y' }] }),
      expect.objectContaining({ bindingId: '50706', rowKeys: [{ id: 'Y' }] }),
    ])
  })

  it('uses the configured structural FK when a legacy catalog has no filter FK declaration', () => {
    const fields = [
      { fieldName: 'id', isPrimaryKey: true, isForeignKey: false },
      { fieldName: 'case_id', isForeignKey: true, refTableId: 50100 },
      { fieldName: 'party_id', isForeignKey: true, refTableId: 50200 },
    ] as never
    const caseFiles = fileBinding({
      filterFkRefTableId: null,
      filterFkFieldName: null,
      foreignKeyField: 'case_id',
      fieldDefinitions: fields,
      data: [
        { id: 'X', case_id: 'C1', title: 'case-doc' },
        { id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' },
      ],
    })
    const partyFiles = fileBinding({
      bindingId: 50706,
      filterFkRefTableId: null,
      filterFkFieldName: null,
      foreignKeyField: 'party_id',
      fieldDefinitions: fields,
      data: [{ id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' }],
    })
    const parties = fileBinding({
      bindingId: 50704,
      tableId: 50200,
      designerTableName: 'p0_dual_party',
      data: [{ id: 'P-A' }],
    })
    const { assembleStartSubTables } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings: ref([caseFiles, partyFiles, parties]),
      formData: ref({ id: 'C1' }),
      primaryTableBinding: ref({ tableId: 50100, primaryKeyFields: ['id'] }),
      deriveColumnsFromBinding: () => [],
    })

    expect(assembleStartSubTables().subTableBindingScopes).toEqual([
      expect.objectContaining({ bindingId: '50705', rowKeys: [{ id: 'X' }, { id: 'Y' }] }),
      expect.objectContaining({ bindingId: '50706', rowKeys: [{ id: 'Y' }] }),
    ])
  })

  it('omits scopes when shared-table bindings do not declare distinct filter FKs', () => {
    const a = fileBinding({ bindingId: 1, data: [{ id: 'X', case_id: 'C1' }] })
    const b = fileBinding({
      bindingId: 2,
      filterFkRefTableId: undefined,
      filterFkFieldName: undefined,
      data: [{ id: 'Y', party_id: 'P-A' }],
    })
    const { assembleStartSubTables } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings: ref([a, b]),
      formData: ref({ id: 'C1' }),
      primaryTableBinding: ref({ tableId: 50100, primaryKeyFields: ['id'] }),
      deriveColumnsFromBinding: () => [],
    })
    expect(assembleStartSubTables().subTableBindingScopes).toEqual([])
  })

  it('removes a newly-added child row when its parent row was deleted before submit', () => {
    const canonicalRows = [
      { id: 'CASE-FILE', case_id: 'C1', title: 'case-doc' },
      { id: 'PARTY-FILE', party_id: 'P-A', title: 'party-doc' },
    ]
    const caseFiles = fileBinding({ data: canonicalRows })
    const partyFiles = fileBinding({
      bindingId: 50706,
      filterFkRefTableId: 50200,
      filterFkFieldName: 'party_id',
      data: canonicalRows,
    })
    const parties = fileBinding({
      bindingId: 50704,
      tableId: 50200,
      designerTableName: 'p0_dual_party',
      data: [],
    })
    const { assembleStartSubTables } = createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings: ref([caseFiles, partyFiles, parties]),
      formData: ref({ id: 'C1' }),
      primaryTableBinding: ref({ tableId: 50100, primaryKeyFields: ['id'] }),
      deriveColumnsFromBinding: () => [],
    })

    const assembled = assembleStartSubTables()
    expect(assembled.subTables['dw:p0_dual_file']).toEqual([
      expect.objectContaining({ id: 'CASE-FILE' }),
    ])
    expect(assembled.subTableBindingScopes).toEqual([
      expect.objectContaining({ bindingId: '50705', rowKeys: [{ id: 'CASE-FILE' }] }),
      expect.objectContaining({ bindingId: '50706', rowKeys: [] }),
    ])
  })
})
