import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  collapseSubTableRowsPreferFilled,
  enrichChildBindingRowsFromParentsNestedSubTables,
  mergeAllSubTableSlicesFromVariables,
  mergeSubTableRowsByRowId,
  miParentRowAlignsWithChildRow,
} from '../shared'
import { clearActiveMiConfig, setActiveMiConfig } from '../useMiConfig'

/**
 * MI 进度列名没有平台默认值（2026-09-02 删除，见 useMiConfig.ts）：terminal-wins 合并
 * 只对**配置了进度列**的 FU 生效。下面的用例用 task_status / task_current_node 这两个名字，
 * 所以必须像真实详情页那样先注册配置——否则合并会（正确地）退化成普通合并。
 */
const SCOPE_WITH_PROGRESS_COLUMNS = {
  subTableName: 'subtable',
  assigneeField: 'assignee',
  rowIdVariable: 'currentItem.rowId',
  miTaskStatusField: 'task_status',
  miTaskCurrentNodeField: 'task_current_node',
  collectionVariable: null,
  elementVariable: 'currentItem',
}

describe('mergeSubTableRowsByRowId MI dashboard columns', () => {
  beforeEach(() => setActiveMiConfig(SCOPE_WITH_PROGRESS_COLUMNS as any))
  afterEach(() => clearActiveMiConfig())

  /**
   * My Request link-form popup (useSubTableLinkFormOpen readOnlyIsolateLinkForm branch) merges this
   * binding's own nested row ("pool") with peer-binding fallback rows sharing the same table_id
   * ("narrowed") for the same PK. Peer bindings (e.g. Assign Task / Main forms) can carry a stale
   * snapshot of a shared participant row's plain fields (like `name`) that diverges from the row's
   * own binding (e.g. Sub task form). The caller must pass the peer/fallback rows as `existing` and
   * the binding's own rows as `incoming` so its non-empty fields win — swapping this order silently
   * let a peer's stale `name` clobber the binding's own current value (My Request popup showed "aaa"
   * instead of the correct "aaad").
   */
  it('own-binding row (passed as incoming) wins over a peer-binding row with a different plain field value', () => {
    const ownBindingRow = { id_idw: 'Test-000001', name: 'aaad', assignee: 'user-dev' }
    const peerBindingRow = { id_idw: 'Test-000001', name: 'aaa', assignee: 'user-dev' }
    const merged = mergeSubTableRowsByRowId([peerBindingRow], [ownBindingRow], ['id_idw'])
    expect(merged).toHaveLength(1)
    expect(merged[0].name).toBe('aaad')
  })

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

  it('merges stale N and saved Y for the same row when row_id IS the designer PK', () => {
    // ATM_Transaction's configured primary key is literally `row_id` (dw_field_definitions), so
    // these rows key on it through `pkFieldNames` like any other PK — not through a hardcoded
    // `row_id` branch, which no longer exists.
    const merged = mergeSubTableRowsByRowId(
      [{
        row_id: 'TRANS-1',
        merchant_name: 'FBT',
        merchant_credit: 'N',
        temporary_refund: 'N',
      }],
      [{
        row_id: 'TRANS-1',
        merchant_name: 'FBT',
        merchant_credit: 'Y',
        temporary_refund: 'N',
      }],
      ['row_id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].merchant_credit).toBe('Y')
    expect(merged[0].temporary_refund).toBe('N')
  })

  it('keys by designer PK and ignores row_id when the same row carries different row_ids per snapshot', () => {
    // Production shape: the SAME participants table is served twice — the engine-variables copy and
    // the portal subTableData copy — and each assigns its own `row_id` to the same physical row.
    // Keying on `row_id` split each participant into two rows, so the MI isolation filter kept the
    // wrong one and the sub-task submitted another participant's row (backend then refused to save).
    const merged = mergeSubTableRowsByRowId(
      [
        { row_id: '637e8dd4', id_idwvvbz: 'Test-000003', name: 'ff' },
        { row_id: '77f82d83', id_idwvvbz: 'Test-000004', name: 'uu' },
      ],
      [
        { row_id: 'e8c44b9d', id_idwvvbz: 'Test-000003', name: 'ff-newer' },
        { row_id: '6db91dc0', id_idwvvbz: 'Test-000004', name: 'uu-newer' },
      ],
      ['id_idwvvbz'],
    )

    expect(merged).toHaveLength(2)
    const byPk = (pk: string) => merged.find((r: any) => r.id_idwvvbz === pk)
    expect(byPk('Test-000003')?.name).toBe('ff-newer')
    expect(byPk('Test-000004')?.name).toBe('uu-newer')
  })

  it('honours Sub-Task Config status / current-node column names instead of fixed ones', () => {
    // Process Design → Sub-Task Config lets the designer name these mirror columns anything
    // (BPMN miTaskStatusField / miTaskCurrentNodeField). Terminal-wins must follow THAT config:
    // a stale IN_PROGRESS slice must not overwrite COMPLETED just because the column is not
    // literally called `task_status`.
    const merged = mergeSubTableRowsByRowId(
      [{ id_idw: 'R-1', my_status: 'COMPLETED', my_node: 'sub form2' }],
      [{ id_idw: 'R-1', my_status: 'IN_PROGRESS', my_node: 'sub form1' }],
      ['id_idw'],
      { statusField: 'my_status', currentNodeField: 'my_node' },
    )

    expect(merged).toHaveLength(1)
    expect(merged[0].my_status).toBe('COMPLETED')
  })

  it('does not merge distinct rows when row_id IS the designer PK', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ row_id: 'TRANS-1', merchant_credit: 'N' }],
      [{ row_id: 'TRANS-2', merchant_credit: 'Y' }],
      ['row_id'],
    )
    expect(merged).toHaveLength(2)
  })
})
