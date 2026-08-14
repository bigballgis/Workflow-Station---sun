import { describe, it, expect } from 'vitest'
import {
  mergeSubTableRowsByRowId,
  shouldSyncStaleSiblingSubTableSlice,
  filterRowsForMiCollectionSubTableBinding,
  isMiDashboardSubTableBinding,
} from '../shared'

function sliceUnchanged(snapshot: Record<string, any> | undefined, bindingId: number, out: unknown[]): boolean {
  if (!snapshot) return false
  const prev = snapshot[bindingId] ?? snapshot[String(bindingId)]
  try {
    return JSON.stringify(prev) === JSON.stringify(out)
  } catch {
    return false
  }
}

function syncStaleSiblingSubTableSlicesFromActiveBindings(
  subTables: Record<string, any>,
  bindings: Array<{
    bindingId: number
    primaryKeyFields?: string[] | null
    data?: unknown[]
    tableId?: number | null
    tableName?: string
    columns?: Array<{ field?: string }> | null
    formFields?: unknown[] | null
    foreignKeyField?: string | null
  }>,
  tableMap?: Map<number, number | null>,
  snapshot?: Record<string, any>,
) {
  const currentIds = new Set(bindings.map(b => Number(b.bindingId)))
  for (const binding of bindings) {
    const source =
      subTables[binding.bindingId] ??
      subTables[String(binding.bindingId)] ??
      binding.data
    if (!Array.isArray(source) || source.length === 0) continue
    const pk = Array.isArray(binding.primaryKeyFields) ? binding.primaryKeyFields : null
    const sourceHasForm = Array.isArray(binding.formFields) && binding.formFields.length > 0
    const sourceIsMiDashboard = isMiDashboardSubTableBinding(binding)
    const sourceChanged = snapshot ? !sliceUnchanged(snapshot, binding.bindingId, source) : sourceHasForm
    for (const key of Object.keys(subTables)) {
      if (!/^\d+$/.test(key)) continue
      if (Number(key) === Number(binding.bindingId)) continue
      if (currentIds.has(Number(key)) && !sourceIsMiDashboard && !sourceChanged) continue
      const target = subTables[key]
      if (!Array.isArray(target) || target.length === 0) continue
      if (!shouldSyncStaleSiblingSubTableSlice(target, binding, bindings, key, tableMap, source)) continue
      subTables[key] = mergeSubTableRowsByRowId(target, source as any[], pk)
    }
  }
}

const miCollectionBinding = {
  bindingId: 66,
  tableId: 10,
  tableName: 'sub_task',
  primaryKeyFields: ['id_idw'],
  columns: [{ field: 'assignee' }, { field: 'task_status' }],
}

describe('syncStaleSiblingSubTableSlicesFromActiveBindings', () => {
  it('merges binding 66 assignee into stale sibling slice 64 by PK', () => {
    const subTables: Record<string, any> = {
      '64': [{
        id_idw: 'Test-000063',
        assignee: { id: 'user-dev', display_name: 'Developer Tester' },
      }],
      '66': [{
        id_idw: 'Test-000063',
        assignee: { id: 'user-e2e-sunqiang', display_name: '孙强' },
      }],
    }
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [{
      bindingId: 66,
      tableId: 10,
      tableName: 'sub_task',
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'assignee' }, { field: 'task_status' }],
      data: subTables['66'],
    }])
    expect(subTables['64'][0].assignee.id).toBe('user-e2e-sunqiang')
  })

  it('does not merge MI collection rows into attachment slice 104', () => {
    const subTables: Record<string, any> = {
      '66': [{
        id_idw: 'Test-000070',
        assignee: 'user-e2e-sunqiang',
        name: '6666',
      }],
      '104': [{
        id: 'pdf-1',
        file: '/api/v1/upload/files/x.pdf',
        main_id: 'main-1',
      }],
    }
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [{
      ...miCollectionBinding,
      data: subTables['66'],
    }])
    expect(subTables['104']).toHaveLength(1)
    expect(subTables['104'][0].file).toContain('.pdf')
    expect(subTables['104'][0].id_idw).toBeUndefined()
  })

  it('copies form-below-table Y onto a stale same-table sibling slice keyed only by row_id', () => {
    const tableId = 50327
    const subTables: Record<string, any> = {
      '50522': [{
        row_id: 'TRANS-FBT-1',
        merchant_name: 'FBT',
        merchant_credit: 'N',
        temporary_refund: 'N',
      }],
      '50527': [{
        row_id: 'TRANS-FBT-1',
        merchant_name: 'FBT',
        merchant_credit: 'Y',
        temporary_refund: 'N',
      }],
      '50528': [{
        row_id: 'TRANS-FBT-1',
        comment: 'note',
        merchant_credit: 'N',
      }],
    }
    const tableMap = new Map<number, number | null>([
      [50522, tableId],
      [50527, tableId],
      [50528, 50326],
    ])
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [{
      bindingId: 50527,
      tableId,
      tableName: 'ATM Transaction',
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'assignee' }, { field: 'merchant_name' }],
      data: subTables['50527'],
    }], tableMap)
    expect(subTables['50522'][0].merchant_credit).toBe('Y')
    expect(subTables['50528'][0].merchant_credit).toBe('N')
  })

  it('copies form-below-table Y onto leftover binding 50533 by row_id without a table map', () => {
    const subTables: Record<string, any> = {
      '50527': [{
        row_id: 'TRANS-FBT-1',
        merchant_name: 'FBT',
        merchant_credit: 'Y',
      }],
      '50533': [{
        row_id: 'TRANS-FBT-1',
        merchant_name: 'FBT',
        merchant_credit: 'N',
      }],
    }
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [{
      bindingId: 50527,
      tableId: 50327,
      tableName: 'ATM Transaction',
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'merchant_name' }],
      formFields: [{ key: 'merchant_credit' }],
      data: subTables['50527'],
    }])
    expect(subTables['50533'][0].merchant_credit).toBe('Y')
  })

  it('does not let a list-only current binding N overwrite the live form slice Y', () => {
    const tableId = 50327
    const subTables: Record<string, any> = {
      '50533': [{
        row_id: 'TRANS-FBT-1',
        merchant_credit: 'N',
      }],
      '50527': [{
        row_id: 'TRANS-FBT-1',
        merchant_credit: 'Y',
      }],
    }
    const tableMap = new Map<number, number | null>([
      [50533, tableId],
      [50527, tableId],
    ])
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [
      {
        bindingId: 50533,
        tableId,
        tableName: 'ATM Transaction',
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }],
        data: subTables['50533'],
      },
      {
        bindingId: 50527,
        tableId,
        tableName: 'ATM Transaction',
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }],
        formFields: [{ key: 'merchant_credit' }],
        data: subTables['50527'],
      },
    ], tableMap)
    expect(subTables['50527'][0].merchant_credit).toBe('Y')
    expect(subTables['50533'][0].merchant_credit).toBe('Y')
  })

  it('copies live form N onto a current list binding whose tableId only exists on the map', () => {
    const tableId = 50327
    const subTables: Record<string, any> = {
      '50533': [{
        row_id: 'TRANS-FBT-1',
        merchant_credit: 'Y',
      }],
      '50527': [{
        row_id: 'TRANS-FBT-1',
        merchant_credit: 'N',
      }],
    }
    const tableMap = new Map<number, number | null>([
      [50533, tableId],
      [50527, tableId],
    ])
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [
      {
        bindingId: 50533,
        tableName: 'ATM Transaction',
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }],
        data: subTables['50533'],
      },
      {
        bindingId: 50527,
        tableId,
        tableName: 'ATM Transaction',
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }],
        formFields: [{ key: 'merchant_credit' }],
        data: subTables['50527'],
      },
    ], tableMap)
    expect(subTables['50527'][0].merchant_credit).toBe('N')
    expect(subTables['50533'][0].merchant_credit).toBe('N')
  })

  it('copies changed form Y onto an FK-scoped current list binding even without formFields', () => {
    const tableId = 50327
    const yRow = { row_id: 'TRANS-FBT-1', merchant_credit: 'Y' }
    const nRow = { row_id: 'TRANS-FBT-1', merchant_credit: 'N' }
    const subTables: Record<string, any> = {
      '50533': [{ ...nRow }],
      '50527': [{ ...yRow }],
    }
    const snapshot = {
      '50533': [{ ...nRow }],
      '50527': [{ ...nRow }],
    }
    const tableMap = new Map<number, number | null>([
      [50533, tableId],
      [50527, tableId],
    ])
    syncStaleSiblingSubTableSlicesFromActiveBindings(subTables, [
      {
        bindingId: 50533,
        tableId,
        tableName: 'ATM Transaction',
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }],
        foreignKeyField: 'id',
        data: subTables['50533'],
      },
      {
        bindingId: 50527,
        tableId,
        tableName: 'ATM Transaction',
        primaryKeyFields: ['id_idw'],
        columns: [{ field: 'merchant_name' }],
        foreignKeyField: 'id',
        data: subTables['50527'],
      },
    ], tableMap, snapshot)
    expect(subTables['50527'][0].merchant_credit).toBe('Y')
    expect(subTables['50533'][0].merchant_credit).toBe('Y')
  })
})

describe('filterRowsForMiCollectionSubTableBinding', () => {
  it('drops attachment leak rows from polluted MI collection slice', () => {
    const polluted = [
      { id_idw: 'Test-000069', assignee: 'user-e2e-lina', name: '55' },
      { id_idw: 'Test-000070', assignee: 'user-e2e-sunqiang', name: '6666' },
      { id: 'pdf-1', file: '/files/x.pdf', main_id: 'm1' },
    ]
    const filtered = filterRowsForMiCollectionSubTableBinding(polluted, {
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'assignee' }, { field: 'name' }],
    })
    expect(filtered).toHaveLength(2)
    expect(filtered.map(r => r.id_idw)).toEqual(['Test-000069', 'Test-000070'])
  })

  it('drops assignee-only ghost rows without designer PK', () => {
    const polluted = [
      { id_idw: 'Test-000069', assignee: 'user-e2e-lina' },
      { assignee: { id: 'user-dev', display_name: 'Developer Tester' } },
    ]
    const filtered = filterRowsForMiCollectionSubTableBinding(polluted, {
      primaryKeyFields: ['id_idw'],
      columns: [{ field: 'assignee' }],
    })
    expect(filtered).toHaveLength(1)
    expect(filtered[0].id_idw).toBe('Test-000069')
  })
})
