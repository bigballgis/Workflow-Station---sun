import { describe, it, expect } from 'vitest'
import {
  mergeSubTableRowsByRowId,
  shouldSyncStaleSiblingSubTableSlice,
  filterRowsForMiCollectionSubTableBinding,
} from '../shared'

function syncStaleSiblingSubTableSlicesFromActiveBindings(
  subTables: Record<string, any>,
  bindings: Array<{
    bindingId: number
    primaryKeyFields?: string[] | null
    data?: unknown[]
    tableId?: number | null
    tableName?: string
    columns?: Array<{ field?: string }> | null
  }>,
) {
  for (const binding of bindings) {
    const source =
      subTables[binding.bindingId] ??
      subTables[String(binding.bindingId)] ??
      binding.data
    if (!Array.isArray(source) || source.length === 0) continue
    const pk = Array.isArray(binding.primaryKeyFields) ? binding.primaryKeyFields : null
    for (const key of Object.keys(subTables)) {
      if (!/^\d+$/.test(key)) continue
      if (Number(key) === Number(binding.bindingId)) continue
      const target = subTables[key]
      if (!Array.isArray(target) || target.length === 0) continue
      if (!shouldSyncStaleSiblingSubTableSlice(target, binding, bindings, key)) continue
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
