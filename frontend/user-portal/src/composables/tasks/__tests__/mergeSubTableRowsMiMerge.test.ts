import { describe, expect, it } from 'vitest'
import {
  collapseSubTableRowsPreferFilled,
  enrichChildBindingRowsFromParentsNestedSubTables,
  mergeAllSubTableSlicesFromVariables,
  mergeSubTableRowsByRowId,
  miParentRowAlignsWithChildRow,
} from '../shared'

describe('mergeSubTableRowsByRowId MI dashboard columns', () => {
  it('does not let later IN_PROGRESS overwrite COMPLETED for the same PK row', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ id: 2323, task_status: 'COMPLETED', task_current_node: 'end' }],
      [{ id: 2323, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
      ['id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].task_status).toBe('COMPLETED')
    expect(merged[0].task_current_node).toBe('end')
  })

  it('upgrades IN_PROGRESS to COMPLETED when a later slice completes', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ id: 1, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
      [{ id: 1, task_status: 'COMPLETED', task_current_node: 'end' }],
      ['id'],
    )
    expect(merged[0].task_status).toBe('COMPLETED')
    expect(String(merged[0].task_current_node).toLowerCase()).toBe('end')
  })

  it('for same status rank prefers the incoming node label', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ id: 1, task_status: 'IN_PROGRESS', task_current_node: 'a' }],
      [{ id: 1, task_status: 'IN_PROGRESS', task_current_node: 'b' }],
      ['id'],
    )
    expect(merged[0].task_status).toBe('IN_PROGRESS')
    expect(merged[0].task_current_node).toBe('b')
  })

  it('keeps richer MI slice current node when a thinner slice merges later', () => {
    const merged = mergeSubTableRowsByRowId(
      [
        {
          id: 8778,
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form2',
        },
      ],
      [
        {
          id: 8778,
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form1',
        },
      ],
      ['id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].task_current_node).toBe('sub form2')
  })

  it('when score ties and incoming is strict non-MI key subset, keep prior current node (duplicate binding)', () => {
    const merged = mergeSubTableRowsByRowId(
      [
        {
          id: 8778,
          filler_col: 'x',
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form2',
        },
      ],
      [
        {
          id: 8778,
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form1',
        },
      ],
      ['id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].task_current_node).toBe('sub form2')
  })

  it('equal-weight slices: refuses sub form number regression (2 → 1)', () => {
    const merged = mergeSubTableRowsByRowId(
      [
        {
          id: 8778,
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form2',
        },
      ],
      [
        {
          id: 8778,
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form1',
        },
      ],
      ['id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].task_current_node).toBe('sub form2')
  })

  it('collapseSubTableRowsPreferFilled picks furthest sub form across fragments', () => {
    const collapsed = collapseSubTableRowsPreferFilled([
      { name: '2', id_idw: 1123, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' },
      { id: 1123, assignee_user_id: 'u1', task_status: 'IN_PROGRESS', task_current_node: 'sub form2' },
    ])
    expect(collapsed).toHaveLength(1)
    expect(collapsed[0].task_current_node).toBe('sub form2')
  })

  it('merges id_idw-only row with id row and keeps furthest sub form node', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ name: '2', id_idw: 1123, assignee_user_id: 'u1', task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
      [{ id: 1123, assignee_user_id: 'u1', task_status: 'IN_PROGRESS', task_current_node: 'sub form2' }],
      ['id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].task_current_node).toBe('sub form2')
    expect(merged[0].id ?? merged[0].id_idw).toBe(1123)
  })

  it('equal-weight slices: allows ordinal progression sub form1 → sub form2', () => {
    const merged = mergeSubTableRowsByRowId(
      [
        {
          id: 8778,
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form1',
        },
      ],
      [
        {
          id: 8778,
          assignee_user_id: 'u1',
          task_status: 'IN_PROGRESS',
          task_current_node: 'sub form2',
        },
      ],
      ['id'],
    )
    expect(merged[0].task_current_node).toBe('sub form2')
  })

  it('miParentRowAlignsWithChildRow rejects different subtasks that share IN_PROGRESS', () => {
    expect(
      miParentRowAlignsWithChildRow(
        { id: 1123, task_status: 'IN_PROGRESS', task_current_node: 'sub form2' },
        { id: 3453, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' },
      ),
    ).toBe(false)
  })

  it('miParentRowAlignsWithChildRow matches link child id FK to parent id_idw', () => {
    expect(
      miParentRowAlignsWithChildRow(
        { id_idw: 'Test-000017', task_status: 'IN_PROGRESS' },
        { id: 'Test-000017', sex: true },
      ),
    ).toBe(true)
  })

  it('enrichChild does not copy another subtask current node onto a different row', () => {
    const bindings = [
      {
        bindingId: 1,
        tableName: 'subtable',
        data: [
          {
            id: 1123,
            task_status: 'IN_PROGRESS',
            task_current_node: 'sub form2',
            __subTables__: { '2': [{ task_current_node: 'sub form2', task_status: 'IN_PROGRESS' }] },
          },
        ],
        primaryKeyFields: ['id'],
      },
      {
        bindingId: 2,
        tableName: 'subtable',
        data: [{ id: 3453, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
        primaryKeyFields: ['id'],
      },
    ]
    enrichChildBindingRowsFromParentsNestedSubTables(bindings)
    expect(bindings[1]!.data[0]!.task_current_node).toBe('sub form1')
  })

  it('mergeAllSubTableSlicesFromVariables picks overlay from sibling binding key (subform_copy resync)', () => {
    const subTables = {
      '64': [{ id: 1123, task_status: 'IN_PROGRESS', task_current_node: 'sub form2' }],
      '66': [{ id: 1123, id_idw: 1123, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
    }
    const allMerged = mergeAllSubTableSlicesFromVariables(subTables, ['id'])
    const alignedBinding = [{ id: 1123, assignee_user_id: 'u1', task_current_node: 'sub form1' }]
    const afterResync = mergeSubTableRowsByRowId(alignedBinding, allMerged, ['id'])
    expect(afterResync[0].task_current_node).toBe('sub form2')
  })
})
