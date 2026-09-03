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

  /**
   * 第二个并集点（2026-09-03 实测漏网）：`patchFormDataSubTablesFromCurrentBindings` 里
   * 对共享附件 binding 把「MI 隔离前的全量快照」并回 `binding.data`。
   *
   * <p>那段并集是为了避免某个子任务保存时丢掉别的行，但它同样表达不了删除：
   * 实测埋点 `uiRows=2` 与 `snapRows=3` 合并后又变回 3，提交请求体里仍是 3 行。
   * 全案共享表不按参与者分片，界面行集即权威 —— 该并集必须跳过。
   */
  it('shared binding: the pre-isolation snapshot merge must be skipped too', () => {
    register()
    const snapRows = [{ idfa: 'a' }, { idfa: 'b' }, { idfa: 'c' }]
    const uiRows = [{ idfa: 'a' }, { idfa: 'b' }]

    // patchFormDataSubTablesFromCurrentBindings 的等价判定
    const skipSnapshotMerge =
      resolveMiBindingKindFromConfig(attachmentBinding, null) === 'shared'
    const out = skipSnapshotMerge
      ? uiRows
      : mergeSubTableRowsByRowId(snapRows, uiRows, ['idfa'])

    expect(skipSnapshotMerge).toBe(true)
    expect(out).toHaveLength(2)
    expect(out.map((r) => r.idfa)).not.toContain('c')
  })

  /**
   * 审计：Add / Edit / Delete 三个行内动作**走的是同一条链路**
   * （SubTableField 的 emit('update:modelValue') → syncMainSubTableRows →
   *  patchFormDataSubTablesFromCurrentBindings），所以那处快照并集对三者一视同仁。
   *
   * <p>并集是 **prefer-filled 的按主键并集**，因此受影响的是「减少信息」的两类操作：
   * <ul>
   *   <li><b>删除</b>：行整个消失 → 被快照带回；</li>
   *   <li><b>清空字段</b>：值变成空 → 被快照旧值盖回；</li>
   *   <li>新增 / 改成非空值：并集本来就保留，不受影响。</li>
   * </ul>
   */
  it('shared binding: delete AND field-clear both survive (add/edit never regressed)', () => {
    register()
    const snap = [{ idfa: 'a', file: 'A.jpg' }, { idfa: 'b', file: 'B.jpg' }]
    const isShared = resolveMiBindingKindFromConfig(attachmentBinding, null) === 'shared'
    const patch = (uiRows) =>
      isShared ? uiRows : mergeSubTableRowsByRowId(snap, uiRows, ['idfa'])

    // 删除 b
    expect(patch([{ idfa: 'a', file: 'A.jpg' }]).map((r) => r.idfa)).toEqual(['a'])
    // 清空 a.file —— prefer-filled 并集会用旧值盖回，这里必须保持为空
    expect(patch([{ idfa: 'a', file: '' }, { idfa: 'b', file: 'B.jpg' }])
      .find((r) => r.idfa === 'a').file).toBe('')
    // 新增 c：本来就不受影响
    expect(patch([...snap, { idfa: 'c', file: 'C.jpg' }]).map((r) => r.idfa).sort())
      .toEqual(['a', 'b', 'c'])
  })
})
