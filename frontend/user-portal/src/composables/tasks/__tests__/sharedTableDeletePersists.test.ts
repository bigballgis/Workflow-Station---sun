import { afterEach, describe, expect, it } from 'vitest'
import {
  registerMiKindTableIdsFromBindings,
  resolveMiBindingKindFromConfig,
} from '../miBindingKindFromConfig'
import { mergeSubTableRowsByRowId } from '../shared'
import { clearActiveMiConfig } from '../useMiConfig'

/**
 * 删除一行共享附件、点 Save，刷新后那一行又回来了（2026-09-03 实测复现）。
 *
 * <p>根因：保存时对每个 binding 都做 `mergeSubTableRowsByRowId(existing, uiRows)` ——
 * 那是**按主键取并集**，"在 existing 里、不在 incoming 里"的行永远被保留，
 * 结构上表达不了"删除"。被删的行从服务端快照原样回填，请求体里仍是 3 行。
 *
 * <p>修法：全案共享表（字段级 FK 指向主表）不做并集，界面行集即权威。
 * 参与者作用域的表（FK 指向 collection）必须继续并集 —— 那里界面上只有"我这一行"。
 *
 * <p>下面的 binding 形状是从真实运行时 payload 抄来的
 * （`/processes/function-units/{fu}/content`，2026-09-03）：
 * **`dataType` 缺失、`refTableId` 存在** —— 所以分类只能靠 refTableId，
 * 不能靠 `dataType === 'FILE'`。
 */

const MAIN_TABLE_ID = 50332
const COLLECTION_TABLE_ID = 50331

/** attachment：FK main_idva → main（50332）= 全案共享 */
const attachmentBinding = {
  bindingId: 50542,
  tableId: 50330,
  tableName: 'attachment',
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'main_idva',
  fieldDefinitions: [
    { fieldName: 'idfa' },
    { fieldName: 'main_idva', isForeignKey: true, refTableId: MAIN_TABLE_ID },
    { fieldName: 'file' },
  ],
} as never

/** people：FK sub_task_idqc → subtable（50331，collection）= 参与者私有 */
const peopleBinding = {
  bindingId: 50550,
  tableId: 50333,
  tableName: 'people',
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'sub_task_idqc',
  fieldDefinitions: [
    { fieldName: 'idk' },
    { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: COLLECTION_TABLE_ID },
  ],
} as never

const collectionBinding = {
  bindingId: 50540,
  tableId: COLLECTION_TABLE_ID,
  tableName: 'subtable',
  bindingLinkMode: 'miParticipantRow',
} as never

function register() {
  registerMiKindTableIdsFromBindings(
    [collectionBinding, attachmentBinding, peopleBinding] as never,
    MAIN_TABLE_ID,
  )
}

afterEach(() => clearActiveMiConfig())

describe('shared sub-table deletion survives Save', () => {
  it('classifies the real attachment binding as shared, people as participant-child', () => {
    register()
    expect(resolveMiBindingKindFromConfig(attachmentBinding, null)).toBe('shared')
    expect(resolveMiBindingKindFromConfig(peopleBinding, null)).toBe('participant-child')
    expect(resolveMiBindingKindFromConfig(collectionBinding, null)).toBe('collection')
  })

  it('merge-by-PK cannot express a deletion — the reason shared tables must replace', () => {
    const saved = [{ idfa: 'a' }, { idfa: 'b' }, { idfa: 'c' }]
    const afterUserDeletedC = [{ idfa: 'a' }, { idfa: 'b' }]

    const merged = mergeSubTableRowsByRowId(saved, afterUserDeletedC, ['idfa'])

    // 这正是 bug：并集把删掉的 c 又带回来了
    expect(merged).toHaveLength(3)
    expect(merged.map((r: any) => r.idfa).sort()).toEqual(['a', 'b', 'c'])
  })

  it('shared binding: the UI row set wins, so the deleted row stays deleted', () => {
    register()
    const saved = [{ idfa: 'a' }, { idfa: 'b' }, { idfa: 'c' }]
    const uiRows = [{ idfa: 'a' }, { idfa: 'b' }]

    const isShared = resolveMiBindingKindFromConfig(attachmentBinding, null) === 'shared'
    const out = isShared ? uiRows : mergeSubTableRowsByRowId(saved, uiRows, ['idfa'])

    expect(isShared).toBe(true)
    expect(out).toHaveLength(2)
    expect(out.map((r: any) => r.idfa)).not.toContain('c')
  })

  it('participant-scoped binding still merges, so a peer participant row is never dropped', () => {
    register()
    // 界面上只有「我这一行」；别人的行必须保留
    const saved = [{ idk: 'mine' }, { idk: 'peer' }]
    const uiRows = [{ idk: 'mine' }]

    const isShared = resolveMiBindingKindFromConfig(peopleBinding, null) === 'shared'
    const out = isShared ? uiRows : mergeSubTableRowsByRowId(saved, uiRows, ['idk'])

    expect(isShared).toBe(false)
    expect(out.map((r: any) => r.idk).sort()).toEqual(['mine', 'peer'])
  })

  it('unclassifiable binding falls back to merge (safe side: cannot cross-participant drop)', () => {
    register()
    // FK 元数据缺失 —— 判不出来
    const unknown = {
      bindingId: 99, tableId: 999, tableName: 'mystery',
      bindingLinkMode: 'structuralFk', foreignKeyField: 'x',
      fieldDefinitions: [{ fieldName: 'x', isForeignKey: true }],
    } as never
    expect(resolveMiBindingKindFromConfig(unknown, null)).toBeNull()
  })
})
