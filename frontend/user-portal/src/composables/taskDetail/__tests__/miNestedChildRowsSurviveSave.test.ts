import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailMiLinkChild } from '../useTaskDetailMiLinkChild'
import type { TaskDetailCtx } from '../context'

/** 每次调用发一个**不同**的 UUID —— 真实分配器就是这样，折叠守卫也依赖它们互不相同。 */
const allocatePrimaryKeys = vi.hoisted(() => {
  let n = 0
  return vi.fn(async () => ({
    values: [`3f2504e0-4f89-11d3-9a0c-0305e82c${String(++n).padStart(4, '0')}`],
  }))
})
vi.mock('@/api/process', () => ({ processApi: { allocatePrimaryKeys } }))

/**
 * 嵌在父表内联表单里的子表（People 嵌在 Participants 的 inlineSubForm），新增的行只落在
 * **父行的 `__subTables__`**，永远不会进入 People 自己的 `binding.data`。
 *
 * <p>Save 时 {@code seedMiParticipantScopedBindingForeignKeys} 先调
 * {@code materializeMiLinkChildBindingRowsFromParents} 把嵌套行捞回绑定，再调
 * {@code syncMiLinkChildRowsIntoParentNested} 把绑定的行写回父行嵌套槽。
 *
 * <p>回捞那一步原本只在「绑定里现有行没有已保存字段」时才认嵌套切片；一旦绑定里已经有一条从服务端
 * 加载的完整行，之后新增的行就在这里被丢掉，紧接着又被当成权威切片盖回父行 —— 用户加了 2 行，
 * Save 后只剩 1 行（#1546）。
 *
 * <p>实测现场：打开 1 行 → 加到 3 行 → 提交 payload 里 nested people 只有 1 行。
 */
describe('seedMiParticipantScopedBindingForeignKeys — 嵌套新增行不得被吞', () => {
  const PARTICIPANT = 'Test-000003'

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
      rowBelongsToCurrentMiScope: (row: any) =>
        row?.id_idw === PARTICIPANT || row?.sub_task_id === PARTICIPANT,
      getSavedSubTableRows: () => [],
      refreshPreviousFormsSubTableDataFromSnapshot: () => {},
    } as unknown as TaskDetailCtx
  }

  /** People：嵌在 Participants 内联表单里的 link child。 */
  const peopleBinding = (data: any[]) => ({
    bindingId: 50547,
    tableId: 50333,
    tableName: 'people',
    physicalTableName: 'people',
    foreignKeyField: 'id',
    primaryKeyFields: ['id'],
    fieldDefinitions: [
      { fieldName: 'id', primaryKey: true, pkStrategy: 'UUID' },
      { fieldName: 'sub_task_id' },
      { fieldName: 'age' },
    ],
    data,
  })

  /** Participants（MI collection）：其行的 __subTables__ 里装着 People 的真实切片。 */
  const participantsBinding = (nestedPeople: any[]) => ({
    bindingId: 50544,
    tableId: 50331,
    tableName: 'subtable',
    physicalTableName: 'subtable',
    primaryKeyFields: ['id_idw'],
    fieldDefinitions: [{ fieldName: 'id_idw', primaryKey: true }],
    data: [
      {
        id_idw: PARTICIPANT,
        __subTables__: { 'dw:people': nestedPeople },
      },
    ],
  })

  it('父行嵌套槽比绑定多出来的行会被捞回绑定（不再丢新增）', async () => {
    allocatePrimaryKeys.mockClear()
    // 打开时加载 1 行；用户又在弹窗里加了 2 行 —— 只落在父行嵌套槽
    const loaded = { id: '7c9e6679-7425-40de-944b-e07fc1f90ae7', sub_task_id: PARTICIPANT, age: 70 }
    const people = peopleBinding([{ ...loaded }])
    const participants = participantsBinding([
      { ...loaded },
      { sub_task_id: PARTICIPANT, age: 71 },
      { sub_task_id: PARTICIPANT, age: 72 },
    ])

    await createTaskDetailMiLinkChild(
      ctxOf([participants, people]),
    ).seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: true,
    })

    expect(people.data).toHaveLength(3)
    expect(people.data.map((r: any) => r.age).sort()).toEqual([70, 71, 72])
  })

  it('回写父行嵌套槽时带的是 3 行，而不是绑定里那 1 行陈旧数据', async () => {
    allocatePrimaryKeys.mockClear()
    const loaded = { id: '7c9e6679-7425-40de-944b-e07fc1f90ae7', sub_task_id: PARTICIPANT, age: 70 }
    const people = peopleBinding([{ ...loaded }])
    const participants = participantsBinding([
      { ...loaded },
      { sub_task_id: PARTICIPANT, age: 71 },
      { sub_task_id: PARTICIPANT, age: 72 },
    ])

    await createTaskDetailMiLinkChild(
      ctxOf([participants, people]),
    ).seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: true,
    })

    const nested = (participants.data[0] as any).__subTables__['dw:people']
    expect(nested).toHaveLength(3)
  })

  it('嵌套槽没有多余行时不改动绑定（不误伤删除）', async () => {
    allocatePrimaryKeys.mockClear()
    // 用户删到只剩 1 行：嵌套槽 1 行、绑定 1 行 —— 不得被"补回"成更多
    const only = { id: '7c9e6679-7425-40de-944b-e07fc1f90ae7', sub_task_id: PARTICIPANT, age: 70 }
    const people = peopleBinding([{ ...only }])
    const participants = participantsBinding([{ ...only }])

    await createTaskDetailMiLinkChild(
      ctxOf([participants, people]),
    ).seedMiParticipantScopedBindingForeignKeys(PARTICIPANT, {
      allocateMissingPrimaryKeys: false,
    })

    expect(people.data).toHaveLength(1)
  })
})
