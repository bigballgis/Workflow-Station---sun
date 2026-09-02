import { describe, it, expect } from 'vitest'
import { applyUnionFindMergeToBindingList } from '../subTableRowHelpers'

const makeBindingWithPk = (bindingId: number, rows: any[]) => ({
  bindingId,
  tableId: 700,
  tableName: 'participants',
  data: rows,
  primaryKeyFields: ['id_idw'],
  // 自持有标记（sub_task_id === 自己的 PK）要能被识别，该列必须是设计器声明的外键 ——
  // 运行时按 fieldDefinitions 解析 FK 列名，不再有列名清单兜底。
  fieldDefinitions: [
    { fieldName: 'id_idw', isPrimaryKey: true },
    { fieldName: 'sub_task_id', isForeignKey: true },
  ],
})

/**
 * Regression: My Request sub-tables (e.g. Participants) shuffled order between page refreshes because
 * the merge preserved whichever async source populated the rows first. The align step now sorts the
 * merged snapshot by a stable per-row key (id_idw → sub_task_id → id) so order is deterministic.
 */
describe('applyUnionFindMergeToBindingList — deterministic row order', () => {
  const makeBinding = (bindingId: number, rows: any[]) => ({
    bindingId,
    tableId: 700,
    tableName: 'participants',
    data: rows,
  })

  it('sorts MI rows by id_idw regardless of incoming order', () => {
    const reversed = [
      { id_idw: 'Test-000019', name: 'b' },
      { id_idw: 'Test-000018', name: 'a' },
    ]
    const all = [makeBinding(1, reversed)] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.id_idw)).toEqual(['Test-000018', 'Test-000019'])
  })

  it('produces the same order whichever binding chunk was seen first', () => {
    const orderA = [
      makeBinding(1, [{ id_idw: 'Test-000018', name: 'a' }]),
      makeBinding(2, [{ id_idw: 'Test-000019', name: 'b' }]),
    ] as any[]
    const orderB = [
      makeBinding(2, [{ id_idw: 'Test-000019', name: 'b' }]),
      makeBinding(1, [{ id_idw: 'Test-000018', name: 'a' }]),
    ] as any[]
    applyUnionFindMergeToBindingList(orderA)
    applyUnionFindMergeToBindingList(orderB)
    const idsA = orderA[0].data.map((r: any) => r.id_idw)
    const idsB = orderB[0].data.map((r: any) => r.id_idw)
    expect(idsA).toEqual(['Test-000018', 'Test-000019'])
    expect(idsB).toEqual(['Test-000018', 'Test-000019'])
  })

  it('is numeric-aware (Test-000002 before Test-000010)', () => {
    const all = [
      makeBinding(1, [
        { id_idw: 'Test-000010', name: 'ten' },
        { id_idw: 'Test-000002', name: 'two' },
      ]),
    ] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.id_idw)).toEqual(['Test-000002', 'Test-000010'])
  })

  it('falls back to id when id_idw is absent', () => {
    const all = [
      makeBinding(1, [
        { id: 'b41ba72c', file: 'c' },
        { id: 'a3149247', file: 'a' },
      ]),
    ] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.id)).toEqual(['a3149247', 'b41ba72c'])
  })

  it('leaves rows untouched when no stable key is present on every row', () => {
    const rows = [{ name: 'z' }, { name: 'a' }]
    const all = [makeBinding(1, rows)] as any[]
    applyUnionFindMergeToBindingList(all)
    expect(all[0].data.map((r: any) => r.name)).toEqual(['z', 'a'])
  })

  /**
   * Regression: My Request Sub task's Participants "Details" edit dialog showed a stale `name`
   * ("aaa") instead of the row's current value ("aaac"). Root cause: three form bindings (Assign
   * Task / Sub task / Main) all share table_id 50331 and land in the same union-find group — but
   * only the binding whose form actually owns the row's writes ("Sub task", binding 50544) stamps
   * the row's structural self-reference FK (sub_task_id === its own id_idw); the other two bindings
   * (Assign Task 50539, Main 50627) only ever held an initialization-time copy that never got this
   * FK populated. The merge must not depend on array/binding order (real callers interleave
   * subTableBindings.value with previousForms/nodeFormMap peers in an order that has no reliable
   * relationship to which binding actually owns the row) — it must prefer whichever row is
   * self-owned by structural FK, regardless of position.
   */
  it('the row carrying its own structural FK (sub_task_id === id_idw) wins over a peer without it, whichever array position it is in', () => {
    const owningBinding = makeBindingWithPk(50544, [
      { id_idw: 'Test-000001', name: 'aaac', sub_task_id: 'Test-000001' },
    ])
    const staleCopyBinding = makeBindingWithPk(50539, [{ id_idw: 'Test-000001', name: 'aaa' }])

    const orderA = [owningBinding, staleCopyBinding] as any[]
    applyUnionFindMergeToBindingList(orderA)
    expect(orderA[0].data.find((r: any) => r.id_idw === 'Test-000001').name).toBe('aaac')
    expect(orderA[1].data.find((r: any) => r.id_idw === 'Test-000001').name).toBe('aaac')

    const owningBinding2 = makeBindingWithPk(50544, [
      { id_idw: 'Test-000001', name: 'aaac', sub_task_id: 'Test-000001' },
    ])
    const staleCopyBinding2 = makeBindingWithPk(50539, [{ id_idw: 'Test-000001', name: 'aaa' }])
    const orderB = [staleCopyBinding2, owningBinding2] as any[]
    applyUnionFindMergeToBindingList(orderB)
    expect(orderB[0].data.find((r: any) => r.id_idw === 'Test-000001').name).toBe('aaac')
    expect(orderB[1].data.find((r: any) => r.id_idw === 'Test-000001').name).toBe('aaac')
  })
})
