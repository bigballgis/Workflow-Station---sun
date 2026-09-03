import { describe, it, expect } from 'vitest'
import { finalizeSharedProcessSubTableBindingRows } from '../sharedProcessSubTableFilters'

/**
 * My Request → application detail, FU 50005 "Multi-Instance Subtask Demo": the Attachment grid
 * rendered one ghost row — a real UUID with MAIN_ID and FILE both "-" — although the process
 * variables hold no attachment data whatsoever (every attachment slice is an empty array and the
 * payload contains no `file` key anywhere). The UUID is the id of a People row, a link-child of MI
 * participant Test-000002.
 *
 * Two independent gaps let it through, both covered here:
 *  1. 「外来行 id 注册表」只遍历键名像参与者表的切片（该注册表已删除）
 *     (`subtable`/`subtable2`/`participants`, or relation table id 20/21). FU 50005's participant
 *     table is 50331 and its slice key is the numeric binding id 50544, so the registry came back
 *     EMPTY and the id-based leak check had nothing to match against.
 *  2. Even with an empty registry, an id-only row passed every remaining heuristic: it has no
 *     `file`, no `id_idw` and no `name`, so nothing classified it as foreign.
 */

const ATTACHMENT_BINDING = {
  bindingId: 50553,
  tableId: 50330,
  tableName: 'Attachment',
  physicalTableName: 'attachment',
  foreignKeyField: 'main_id',
  columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
  // 真实 binding payload 里 columns 常为 null，判据走设计器字段定义（data_type='FILE'）
  fieldDefinitions: [
    { fieldName: 'id', isPrimaryKey: true },
    { fieldName: 'main_id', isForeignKey: true },
    { fieldName: 'file', dataType: 'FILE' },
  ],
}

/** The real People row from instance a784cdde-… (link-child of participant Test-000002). */
const PEOPLE_ROW = {
  id: 'a1701c2f-4fc4-4ae4-9a38-8cb9364610e5',
  age: 'u',
  sex: false,
  sub_task_id: 'Test-000002',
}

describe('shared attachment grid — ghost rows carrying no attachment data are dropped', () => {
  it('drops an id-only ghost row (renders as a row of "-")', () => {
    const ghost = { id: 'a1701c2f-4fc4-4ae4-9a38-8cb9364610e5' }
    expect(
      finalizeSharedProcessSubTableBindingRows([ghost], ATTACHMENT_BINDING, undefined),
    ).toEqual([])
  })

  it('drops a full link-child row projected into the attachment binding', () => {
    expect(
      finalizeSharedProcessSubTableBindingRows([{ ...PEOPLE_ROW }], ATTACHMENT_BINDING, undefined),
    ).toEqual([])
  })

  it('drops it even when the foreign-id registry is empty (the FU 50005 case)', () => {
    // Reproduces the real payload shape: participant rows keyed by numeric binding id 50544,
    // relation table 50331 — neither matches the registry's participant-slice heuristic.
    const flat: Record<string, unknown> = {
      50539: [{ id_idw: 'Test-000001' }, { id_idw: 'Test-000002' }],
      50544: [
        { id_idw: 'Test-000002', sub_task_id: 'Test-000002', __subTables__: { 50547: [PEOPLE_ROW] } },
      ],
      50547: [PEOPLE_ROW],
      50548: [],
      50553: [],
    }
    // 注册表已删除：它只走「键名看着像参与者表」的切片，而键早已是规范键 `dw:<name>`，
    // 因此对任何 FU 都返回空集（本用例原先就断言 size === 0）。防线由下面两条承担：
    // 结构外键指向参与者 / 本表列上无数据。
    expect(
      finalizeSharedProcessSubTableBindingRows([{ ...PEOPLE_ROW }], ATTACHMENT_BINDING),
    ).toEqual([])
  })

  it('KEEPS a genuine attachment row', () => {
    const real = { id: 'f0000000-0000-4000-8000-000000000001', main_id: 'Meeting-000001', file: 'spec.pdf' }
    expect(
      finalizeSharedProcessSubTableBindingRows([real], ATTACHMENT_BINDING, undefined),
    ).toEqual([real])
  })

  it('KEEPS a genuine attachment row while dropping a co-located ghost', () => {
    const real = { id: 'f0000000-0000-4000-8000-000000000002', main_id: 'Meeting-000001', file: 'a.png' }
    expect(
      finalizeSharedProcessSubTableBindingRows(
        [{ ...PEOPLE_ROW }, real],
        ATTACHMENT_BINDING,
        undefined,
      ),
    ).toEqual([real])
  })

  /**
   * The "no own column data" rule must not empty a non-attachment shared table whose list view
   * happens to show only its PK — that class of over-filtering previously emptied whole grids
   * (see the hasOwnData comment in filterRowsForSharedProcessSubTableBinding).
   */
  it('does not apply the attachment ghost rule to a non-attachment shared binding', () => {
    const idOnly = { id: 'c0000000-0000-4000-8000-000000000003' }
    const plainShared = {
      bindingId: 51000,
      tableId: 51001,
      tableName: 'Remark',
      physicalTableName: 'remark',
      foreignKeyField: 'main_id',
      columns: [{ field: 'id' }],
    }
    expect(
      finalizeSharedProcessSubTableBindingRows([idOnly], plainShared, undefined),
    ).toEqual([idOnly])
  })
})
