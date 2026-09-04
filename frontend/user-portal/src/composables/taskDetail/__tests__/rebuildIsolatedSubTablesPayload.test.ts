import { ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { createTaskDetailSubTableSync } from '../useTaskDetailSubTableSync'

/**
 * MI 子任务提交 payload 的组装（`rebuildIsolatedSubTablesPayload` / `syncMainSubTableRows`）。
 *
 * 规范 key 落地后，同一张设计器表只有一个 key（`dw:<name>`），历史上「一 binding 一份 + 一别名
 * 一份」的扇出消失，以下两类 bug 从结构上不再可能：
 *   1. 同一行在不同 key 下出现两个版本 → 后端逐 key 处理时旧值胜出（「改了又变回去」）
 *   2. 某个 key 漏更新 → 自己的行从 payload 里消失（后端拒绝保存）
 * 因此这里只需验证：payload 只有规范 key、编辑生效、其他参与者的行不受影响。
 */

const PK = ['id_idwvvbz']

function row(pk: string, extra: Record<string, unknown> = {}) {
  return { id_idwvvbz: pk, ...extra }
}

/** 最小 ctx：MI scope 按 PK 匹配；切片查找按规范 key。 */
function makeCtx(currentBindings: any[], previousFormBindings: any[]) {
  return {
    subTableBindings: ref(currentBindings),
    previousForms: ref(previousFormBindings.length ? [{ subTableBindings: previousFormBindings }] : []),
    nodeFormMap: ref(new Map()),
    isMiSubTaskMode: ref(true),
    miSubProcessScope: ref({ subTableName: 'subtable' }),
    taskForm: { formData: ref({}), scheduleSubTableAutosave: () => {} },
    miFullSubTablesSnapshotRef: ref(null),
    currentMiRowId: ref(null as string | null),
    warnMiMissingPrimaryKey: () => {},
    rowBelongsToCurrentMiScope: (r: any, myRowId: any) => String(r?.id_idwvvbz) === String(myRowId),
    getSavedSubTableRows: (slices: any, binding: any) => {
      const key = binding?.relationTableId != null
        ? `rt:${String(binding.relationTableName ?? binding.tableName).toLowerCase()}`
        : `dw:${String(binding?.designerTableName ?? binding?.tableName ?? '').toLowerCase()}`
      return slices?.[key]
    },
    isCurrentMiCollectionSubTableBinding: () => false,
    miRowBelongsToCurrentParticipant: () => false,
    syncMiLinkChildRowsIntoParentNested: () => {},
  } as any
}

const participantsBinding = (data: any[]) => ({
  bindingId: 50627, tableName: 'subtable', designerTableName: 'subtable', tableId: 50331,
  primaryKeyFields: PK, columns: [{ field: 'task_status' }], data,
})

describe('rebuildIsolatedSubTablesPayload — 规范 key', () => {
  it('payload 只含规范 key，不再有 binding id / 表名别名', () => {
    const current = [participantsBinding([row('Test-000006', { name: 'mine' })])]
    const out = createTaskDetailSubTableSync(makeCtx(current, []))
      .rebuildIsolatedSubTablesPayload('Test-000006')

    expect(Object.keys(out)).toEqual(['dw:subtable'])
    // 历史结构会同时写这些 key，正是同一行出现两个版本的来源
    expect(out).not.toHaveProperty('50627')
    expect(out).not.toHaveProperty('subtable')
    expect(out).not.toHaveProperty('Participants')
  })

  it('同一张表的多个 binding 收敛到同一个 key，不产生第二份', () => {
    // 实测 subtable 被 6 个 binding 绑定；历史结构下它们各存一份且会分叉
    // （50539 有 2 行而 50544 只有 1 行）。
    const current = [
      participantsBinding([row('Test-000006', { name: 'mine' })]),
      { ...participantsBinding([row('Test-000006', { name: 'mine' })]), bindingId: 50544 },
    ]
    const out = createTaskDetailSubTableSync(makeCtx(current, []))
      .rebuildIsolatedSubTablesPayload('Test-000006')

    expect(Object.keys(out)).toEqual(['dw:subtable'])
  })

  it('其他参与者的行来自 previous-form 快照时仍然保留', () => {
    const current = [participantsBinding([row('Test-000006', { name: 'mine' })])]
    const previous = [{
      ...participantsBinding([row('Test-000005', { name: 'theirs' }), row('Test-000006', { name: 'stale' })]),
      bindingId: 50539,
    }]

    const out = createTaskDetailSubTableSync(makeCtx(current, previous))
      .rebuildIsolatedSubTablesPayload('Test-000006')

    const pks = (out['dw:subtable'] ?? []).map((r: any) => r.id_idwvvbz).sort()
    expect(pks).toEqual(['Test-000005', 'Test-000006'])
  })

  it('当前表单的行覆盖 previous-form 的陈旧副本', () => {
    const current = [participantsBinding([row('Test-000006', { name: 'EDITED' })])]
    const previous = [{
      ...participantsBinding([row('Test-000006', { name: 'STALE' })]),
      bindingId: 50539,
    }]

    const out = createTaskDetailSubTableSync(makeCtx(current, previous))
      .rebuildIsolatedSubTablesPayload('Test-000006')

    const mine = (out['dw:subtable'] ?? []).find((r: any) => r.id_idwvvbz === 'Test-000006')
    expect(mine?.name).toBe('EDITED')
  })
})

describe('syncMainSubTableRows — 编辑生效', () => {
  it('用户清空字段时，持久化的旧值不得把它填回去', () => {
    // 回归：payload 曾用 prefer-filled 合并，持久化的旧值会盖过刚清空的字段，
    // 表现为「保存成功但刷新后又变回去」。
    const current = [participantsBinding([row('Test-000006', { name: 'old' })])]
    const ctx = makeCtx(current, [])
    ctx.currentMiRowId = ref('Test-000006')
    ctx.taskForm.formData = ref({
      __subTables__: {
        'dw:subtable': [row('Test-000005', { name: 'theirs' }), row('Test-000006', { name: 'old' })],
      },
    })
    const sync = createTaskDetailSubTableSync(ctx)

    sync.syncMainSubTableRows(50627, [row('Test-000006', { name: '' })])

    const payload = ctx.taskForm.formData.value.__subTables__['dw:subtable']
    expect(payload.find((r: any) => r.id_idwvvbz === 'Test-000006')?.name).toBe('')
    // 其他参与者的行必须完整保留在 payload 里
    expect(payload.find((r: any) => r.id_idwvvbz === 'Test-000005')?.name).toBe('theirs')
    // 表单只渲染自己的行
    expect((current[0].data as any[]).map(r => r.id_idwvvbz)).toEqual(['Test-000006'])
  })
})
