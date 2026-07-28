import { describe, expect, it } from 'vitest'
import {
  finalizeMiCollectionSubTableBindingRows,
  mergeMiCollectionSubTableRows,
  filterRowsForMiCollectionSubTableBinding,
  rowResolvesDesignerPrimaryKey,
} from '../shared'

/**
 * MI collection dedupe choke point: designer PK merge across current task / snapshot / resync,
 * keep filled fields from thin+fat snapshots, drop rows without a complete PK.
 */

const BINDING_ID_IDW = {
  primaryKeyFields: ['id_idw'],
  columns: [{ field: 'id_idw' }, { field: 'assignee' }, { field: 'task_status' }, { field: 'name' }],
}

const BINDING_COMPOSITE = {
  primaryKeyFields: ['org_id', 'emp_id'],
  columns: [{ field: 'org_id' }, { field: 'emp_id' }, { field: 'name' }, { field: 'task_status' }],
}

describe('mergeMiCollectionSubTableRows — designer PK choke point', () => {
  it('single PK: merges thin snapshot + fat current row and keeps filled fields', () => {
    const thinSnap = [{ id_idw: 'Test-000069', task_status: 'IN_PROGRESS', name: '' }]
    const current = [
      {
        id_idw: 'Test-000069',
        name: '55',
        assignee: 'user-e2e-lina',
        task_status: 'IN_PROGRESS',
        task_current_node: 'subform2',
      },
    ]
    const merged = mergeMiCollectionSubTableRows([thinSnap, current], BINDING_ID_IDW)
    expect(merged).toHaveLength(1)
    expect(merged[0].id_idw).toBe('Test-000069')
    expect(merged[0].name).toBe('55')
    expect(merged[0].assignee).toBe('user-e2e-lina')
    expect(merged[0].task_current_node).toBe('subform2')
  })

  it('single PK: number vs string id collapse to one row', () => {
    const merged = mergeMiCollectionSubTableRows(
      [[{ id_idw: 555, name: 'thin' }], [{ id_idw: '555', assignee: 'u1', name: 'fat' }]],
      BINDING_ID_IDW,
    )
    expect(merged).toHaveLength(1)
    expect(String(merged[0].id_idw)).toBe('555')
    expect(merged[0].name).toBe('fat')
    expect(merged[0].assignee).toBe('u1')
  })

  it('composite PK: merges only when all parts match', () => {
    const snap = [{ org_id: 1, emp_id: 2, name: '', task_status: 'IN_PROGRESS' }]
    const current = [{ org_id: 1, emp_id: 2, name: 'Ada', task_status: 'IN_PROGRESS', task_current_node: 'subform1' }]
    const other = [{ org_id: 1, emp_id: 3, name: 'Bob' }]
    const merged = mergeMiCollectionSubTableRows([snap, current, other], BINDING_COMPOSITE)
    expect(merged).toHaveLength(2)
    const ada = merged.find(r => r.emp_id === 2 || r.emp_id === '2')
    expect(ada?.name).toBe('Ada')
    expect(ada?.task_current_node).toBe('subform1')
    expect(merged.find(r => Number(r.emp_id) === 3)?.name).toBe('Bob')
  })

  it('drops rows without a complete designer PK (and attachment leaks)', () => {
    const rows = [
      { id_idw: 'Test-000069', name: 'ok' },
      { name: 'ghost-no-pk' },
      { id: 'pdf-1', file: '/files/x.pdf', main_id: 'm1' },
      { org_id: 1, name: 'partial-composite' },
    ]
    expect(filterRowsForMiCollectionSubTableBinding(rows, BINDING_ID_IDW)).toHaveLength(1)
    expect(filterRowsForMiCollectionSubTableBinding(rows, BINDING_COMPOSITE)).toHaveLength(0)

    const merged = mergeMiCollectionSubTableRows([rows], BINDING_ID_IDW)
    expect(merged).toHaveLength(1)
    expect(merged[0].id_idw).toBe('Test-000069')
  })

  it('duplicate same-PK rows across sources collapse once (resync + snapshot + current)', () => {
    const a = [{ id_idw: 'Test-000070', name: '6666', task_status: 'IN_PROGRESS' }]
    const b = [{ id_idw: 'Test-000070', assignee: 'user-e2e-sunqiang', task_status: 'IN_PROGRESS' }]
    const c = [{ id_idw: 'Test-000070', name: '6666', assignee: 'user-e2e-sunqiang', task_current_node: 'subform1' }]
    const merged = mergeMiCollectionSubTableRows([a, b, c], BINDING_ID_IDW)
    expect(merged).toHaveLength(1)
    expect(merged[0].name).toBe('6666')
    expect(merged[0].assignee).toBe('user-e2e-sunqiang')
    expect(merged[0].task_current_node).toBe('subform1')
  })

  it('thin later snapshot does not wipe richer prior fields', () => {
    const fat = [
      {
        id_idw: 'Test-000069',
        name: '55',
        assignee: 'user-e2e-lina',
        task_status: 'IN_PROGRESS',
        task_current_node: 'subform2',
      },
    ]
    const thinLater = [{ id_idw: 'Test-000069', name: '', task_status: 'IN_PROGRESS' }]
    const merged = mergeMiCollectionSubTableRows([fat, thinLater], BINDING_ID_IDW)
    expect(merged).toHaveLength(1)
    expect(merged[0].name).toBe('55')
    expect(merged[0].assignee).toBe('user-e2e-lina')
    expect(merged[0].task_current_node).toBe('subform2')
  })

  it('finalizeMiCollectionSubTableBindingRows is the single-slice alias of the choke point', () => {
    const dup = [
      { id_idw: 'Test-000069', name: 'a' },
      { id_idw: 'Test-000069', assignee: 'u1', name: 'b' },
      { name: 'no-pk' },
    ]
    expect(finalizeMiCollectionSubTableBindingRows(dup, BINDING_ID_IDW)).toEqual(
      mergeMiCollectionSubTableRows([dup], BINDING_ID_IDW),
    )
  })

  it('rowResolvesDesignerPrimaryKey understands id↔id_idw alias used by merge', () => {
    expect(rowResolvesDesignerPrimaryKey({ id_idw: 'Test-1' }, ['id'])).toBe(true)
    expect(rowResolvesDesignerPrimaryKey({ id: 'uuid-1' }, ['id_idw'])).toBe(true)
    expect(rowResolvesDesignerPrimaryKey({ name: 'x' }, ['id_idw'])).toBe(false)
    expect(rowResolvesDesignerPrimaryKey({ org_id: 1 }, ['org_id', 'emp_id'])).toBe(false)
  })
})
