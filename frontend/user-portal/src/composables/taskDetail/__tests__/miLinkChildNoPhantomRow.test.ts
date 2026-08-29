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

  /** People 绑定：MI 参与人维度的 link child，foreignKeyField=id。 */
  function peopleBinding(data: any[]) {
    return {
      bindingId: 50547,
      tableId: 50333,
      tableName: 'people',
      foreignKeyField: 'id',
      primaryKeyFields: ['id'],
      fieldDefinitions: [
        { fieldName: 'id', primaryKey: true, pkStrategy: 'UUID' },
        { fieldName: 'sub_task_id' },
      ],
      data,
    }
  }

  it('空绑定 + Save：不造行、不申请主键', async () => {
    allocatePrimaryKeys.mockClear()
    const binding = peopleBinding([])
    const fns = createTaskDetailMiLinkChild(ctxOf([binding]))

    await fns.seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: true,
    })

    expect(binding.data).toEqual([])
    expect(allocatePrimaryKeys).not.toHaveBeenCalled()
  })

  it('已有真实行 + Save：仍然给缺主键的行分配（修复未误伤正常新增）', async () => {
    allocatePrimaryKeys.mockClear()
    const binding = peopleBinding([{ sub_task_id: PARTICIPANT, sex: 'M' }])
    const fns = createTaskDetailMiLinkChild(ctxOf([binding]))

    await fns.seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: true,
    })

    expect(binding.data).toHaveLength(1)
    expect(allocatePrimaryKeys).toHaveBeenCalled()
  })
})
