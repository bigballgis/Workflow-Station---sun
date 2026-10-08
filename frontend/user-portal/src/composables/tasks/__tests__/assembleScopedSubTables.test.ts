import { describe, expect, it } from 'vitest'
import { buildProcessFormUpdateBody } from '../assembleScopedSubTables'

describe('buildProcessFormUpdateBody', () => {
  it('places scopes beside form fields and rebuilds __subTables__ from widgets', () => {
    const body = buildProcessFormUpdateBody(
      { id: 'C1', title: 'case', emptiedSubTableKeys: 'should-be-replaced' },
      [
        {
          bindingId: 50705,
          tableId: 50348,
          tableName: 'p0_dual_file',
          primaryKeyFields: ['id'],
          filterFkRefTableId: 50100,
          filterFkFieldName: 'case_id',
          data: [
            { id: 'X', case_id: 'C1' },
            { id: 'Y', case_id: 'C1', party_id: 'P-A' },
          ],
        },
        {
          bindingId: 50706,
          tableId: 50348,
          tableName: 'p0_dual_file',
          primaryKeyFields: ['id'],
          filterFkRefTableId: 50200,
          filterFkFieldName: 'party_id',
          data: [{ id: 'Y', case_id: 'C1', party_id: 'P-A' }],
        },
        {
          bindingId: 50704,
          tableId: 50200,
          tableName: 'p0_dual_party',
          primaryKeyFields: ['id'],
          filterFkRefTableId: 50100,
          filterFkFieldName: 'case_id',
          data: [{ id: 'P-A' }],
        },
      ],
      { formData: { id: 'C1' }, primaryTableId: 50100, primaryPkFields: ['id'] },
    )
    expect(body.title).toBe('case')
    expect(body.id).toBe('C1')
    expect(body.emptiedSubTableKeys).toEqual([])
    expect(body.subTableBindingScopes).toEqual([
      expect.objectContaining({ bindingId: '50705', storeKey: 'dw:p0_dual_file' }),
      expect.objectContaining({ bindingId: '50706', storeKey: 'dw:p0_dual_file' }),
    ])
    expect((body.__subTables__ as Record<string, unknown>).subTableBindingScopes).toBeUndefined()
  })

  it('claims the persisted child deletion when its parent row is deleted', () => {
    const childRows = [{ id: 'PARTY-FILE', party_id: 'P-A', _wsRowVersion: 4 }]
    const bindings = [
      {
        bindingId: 50705,
        tableId: 50348,
        tableName: 'p0_dual_file',
        primaryKeyFields: ['id'],
        filterFkRefTableId: 50100,
        filterFkFieldName: 'case_id',
        data: childRows,
      },
      {
        bindingId: 50706,
        tableId: 50348,
        tableName: 'p0_dual_file',
        primaryKeyFields: ['id'],
        filterFkRefTableId: 50200,
        filterFkFieldName: 'party_id',
        data: childRows,
      },
      {
        bindingId: 50704,
        tableId: 50200,
        tableName: 'p0_dual_party',
        primaryKeyFields: ['id'],
        filterFkRefTableId: 50100,
        filterFkFieldName: 'case_id',
        data: [],
      },
    ]
    const baseline = {
      'dw:p0_dual_file': childRows,
      'dw:p0_dual_party': [{ id: 'P-A', case_id: 'C1', _wsRowVersion: 2 }],
    }

    const body = buildProcessFormUpdateBody(
      { id: 'C1' },
      bindings,
      { formData: { id: 'C1' }, primaryTableId: 50100, primaryPkFields: ['id'] },
      baseline,
    )

    expect((body.__subTables__ as Record<string, unknown>)['dw:p0_dual_file']).toEqual([])
    expect(body.subTableBindingScopes).toEqual(expect.arrayContaining([
      expect.objectContaining({
        bindingId: '50706',
        rowKeys: [],
        deletedRows: [{ id: 'PARTY-FILE', _wsRowVersion: 4 }],
      }),
    ]))
  })
})
