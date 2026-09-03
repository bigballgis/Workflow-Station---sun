/**
 * 保存时「已存切片 × 界面行集」如何合并 —— **按 binding 的 MI 分类决定**，一处定义、多处复用。
 *
 * <p><b>要解决的结构性缺陷。</b>{@link mergeSubTableRowsByRowId} 是**按主键取并集**：
 * 「在 existing 里、不在 incoming 里」的行永远被保留，所以并集**表达不了删除**。
 * 用户删掉一行点 Save，被删的行会从已存快照原样回填 —— 请求体里那行还在，
 * 后端自然什么也删不掉，日志上看不出任何异常（2026-09-03 实测：UI 2 行 / payload 3 行 / DB 3 行）。
 *
 * <p><b>但并集不能一删了之。</b>participant-child 切片（如 People）是**进程级的一个数组**，
 * `Test-000001/2/3` 三个子任务的行混在一起；当前用户界面上只看得到自己那部分，
 * 直接用界面行集覆盖会把另外两个参与者的行删光。并集当初就是为了防这个。
 *
 * <p><b>正确规则 —— 按参与者分片替换。</b>两个约束同时成立：
 * <pre>
 *   最终行集 = 基线里【不属于我】的行  +  界面上【我的】全部行
 * </pre>
 * peer 的行原样保留（不受我影响），我的那一片以界面为准（所以删得掉）。
 * 这**不是新发明**：后端 {@code MiSubTaskSubTableRowMerger.mergeRowsKeepingBaselineExceptCurrent}
 * 的 {@code ownByFk} 分支本来就是这么做的 —— 前端全是无差别并集，
 * **前后端语义不对称**才是这个 bug 的核心。
 *
 * <p><b>前提：界面确实展示了我全部的行。</b>成立 —— {@code isolateMiSubTaskData} 用
 * {@code rowBelongsToCurrentMiScope} 把 `binding.data` 精确过滤成当前参与者的行
 * （useTaskDetailMiIsolation.ts:53），所以「我这一片」的界面行集是权威的。
 *
 * <p><b>三类各走哪条路</b>（分类判据见 {@link resolveMiBindingKindFromConfig}，全部读设计器配置）：
 * <ul>
 *   <li><b>shared</b>（FK 指向主表，如 attachment）—— 不按参与者分片，界面看到的就是全部行，
 *       **直接替换**（f14599671 已确立）；</li>
 *   <li><b>participant-child</b>（FK 指向 collection，如 People）—— **分片替换**（本次修复）；</li>
 *   <li><b>判不出来（null）</b> —— **保守走并集**。最坏是删不掉，不会跨参与者丢数据；
 *       这是既有代码已确立的安全侧原则（见 {@link resolveMiBindingKindFromConfig} 的说明），此处沿用。</li>
 * </ul>
 */

import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { resolveMiBindingKindFromConfig, type MiKindBindingLike } from './miBindingKindFromConfig'

/** 归属判定：这一行属不属于当前参与者。由调用方注入（复用 `rowBelongsToCurrentMiScope`，不另造判据）。 */
export type RowOwnedByCurrentParticipant = (row: unknown) => boolean

export interface MiSaveMergeOptions {
  /** 已持久化的切片（含其他参与者的行）。 */
  existing: unknown[] | undefined
  /** 界面当前行集（MI 隔离后 = 当前参与者的全部行）。 */
  uiRows: unknown[]
  /** 设计器主键列，用于并集/替换时的行匹配。 */
  primaryKeyFields?: string[] | null
  /**
   * 归属谓词。**缺省（undefined）时 participant-child 退回并集** ——
   * 判不出归属就不能替换，否则会把 peer 的行当成「我删掉的」一起丢掉。
   */
  isOwnRow?: RowOwnedByCurrentParticipant | null
}

/**
 * 保存时合并一个 binding 的行集。
 *
 * @returns 应当写进 payload 的行集
 */
export function mergeSubTableRowsForMiSave(
  binding: MiKindBindingLike | null | undefined,
  options: MiSaveMergeOptions,
): unknown[] {
  const { existing, uiRows, primaryKeyFields, isOwnRow } = options
  const baseline = Array.isArray(existing) ? existing : []
  const rows = Array.isArray(uiRows) ? uiRows : []
  const kind = resolveMiBindingKindFromConfig(binding, null)

  // 全案共享：界面行集即权威。
  if (kind === 'shared') return rows

  // 参与者私有：保留 peer 的行，我的那一片整体以界面为准。
  if (kind === 'participant-child' && typeof isOwnRow === 'function') {
    const peerRows = baseline.filter(row => !isOwnRow(row))
    // 仍走一次并集，是为了让 peer 行与界面行在**主键相同**时正常收敛
    // （例如归属谓词把同一行既算 peer 又算我的边界情形），而不是直接拼接产生重复行。
    return mergeSubTableRowsByRowId(peerRows, rows, primaryKeyFields ?? null)
  }

  // 判不出分类、或拿不到归属谓词：保守并集（最坏删不掉，不会跨参与者丢数据）。
  return mergeSubTableRowsByRowId(baseline, rows, primaryKeyFields ?? null)
}
