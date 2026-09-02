import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailMiLinkChild } from '../useTaskDetailMiLinkChild'
import type { TaskDetailCtx } from '../context'

const allocatePrimaryKeys = vi.hoisted(() => vi.fn(async () => ({ values: ['alloc-uuid-1'] })))
vi.mock('@/api/process', () => ({ processApi: { allocatePrimaryKeys } }))

/**
 * 空的 link-child 子表（People）在 Save 时不得凭空多出一行。
 *
 * <p>历史上 Save 走 {@code allocateMissingPrimaryKeys: true} 时，若绑定一行都没有就塞一个 `[{}]`
 * 占位——那是为了让"表格下方内联表单"有行可绑。内联表单条早已删除（改走 Link Form 弹窗新增/编辑），
 * 占位行于是没有任何编辑器去填：seeding 给它盖上参与人 FK、分配器再发一个真 UUID 主键，
 * 于是每次 Save 都往空 People 表里持久化一条空白幽灵行（#1531）。
 *
 * <p>这里锁住两条：空绑定 Save 后仍然为空且不申请主键；已有真实行的绑定照常分配主键（修复没有误伤）。
 */
describe('seedMiParticipantScopedBindingForeignKeys — 空 link-child 绑定', () => {
  const PARTICIPANT = 'Test-000002'

  function ctxOf(bindings: any[]): TaskDetailCtx {
    return {
      subTableBindings: ref(bindings),
      previousForms: ref([]),
      miSubProcessScope: ref({ subTableName: 'subtable' }),
      miFullSubTablesSnapshotRef: ref(null),
      lastBindingRelationTableMap: ref(new Map()),
      functionUnitIdRef: ref('50005'),
      taskId: 'task-1',
      currentMiRowId: ref(PARTICIPANT),
      miCollectionPrimaryKeyFields: () => ['id_idw'],
      miRowBelongsToCurrentParticipant: () => false,
      rowBelongsToCurrentMiScope: () => true,
      getSavedSubTableRows: () => [],
      refreshPreviousFormsSubTableDataFromSnapshot: () => {},
    } as unknown as TaskDetailCtx
  }

  /**
   * MI collection（subtable）绑定 —— 必须存在，child 才判得出来：
   * 「FK 指向 collection 的 tableId」是 participant-child 的唯一判据。
   */
  function collectionBinding() {
    return {
      bindingId: 50544,
      tableId: 50331,
      tableName: 'subtable',
      physicalTableName: 'subtable',
      bindingLinkMode: 'miParticipantRow',
      primaryKeyFields: ['id_idw'],
      fieldDefinitions: [{ fieldName: 'id_idw', isPrimaryKey: true }],
      data: [{ id_idw: PARTICIPANT }],
    }
  }

  /** People 绑定：MI 参与人维度的 link child，foreignKeyField=id。 */
  function peopleBinding(data: any[]) {
    return {
      bindingId: 50547,
      tableId: 50333,
      tableName: 'people',
      foreignKeyField: 'id',
      primaryKeyFields: ['id'],
      fieldDefinitions: [
        { fieldName: 'id', primaryKey: true, isPrimaryKey: true, pkStrategy: 'UUID' },
        // 结构外键必须在设计器里声明并指向 MI collection（50331 subtable）——
        // 分类与行归属都读它，不猜列名。
        { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 50331 },
      ],
      data,
    }
  }

  it('空绑定 + Save：不造行、不申请主键', async () => {
    allocatePrimaryKeys.mockClear()
    const binding = peopleBinding([])
    const fns = createTaskDetailMiLinkChild(ctxOf([collectionBinding(), binding]))

    await fns.seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: true,
    })

    expect(binding.data).toEqual([])
    expect(allocatePrimaryKeys).not.toHaveBeenCalled()
  })

  it('已有真实行 + Save：仍然给缺主键的行分配（修复未误伤正常新增）', async () => {
    allocatePrimaryKeys.mockClear()
    const binding = peopleBinding([{ sub_task_id: PARTICIPANT, sex: 'M' }])
    const fns = createTaskDetailMiLinkChild(ctxOf([collectionBinding(), binding]))

    await fns.seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: true,
    })

    expect(binding.data).toHaveLength(1)
    expect(allocatePrimaryKeys).toHaveBeenCalled()
  })
})
