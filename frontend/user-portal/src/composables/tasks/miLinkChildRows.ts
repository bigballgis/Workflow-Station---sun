/**
 * MI link-child row set operations: per-participant collapse, PK backfill from variables,
 * picking child rows for an MI parent row and expansion-id row lookup.
 */

import {
  isAllocatedUuidPrimaryKey,
  normalizeMiLinkMatchId,
} from './internal'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { isMiParticipantScopedSubTableBinding } from './subTableBindingKinds'
import { getSavedSubTableRows } from './subTableSliceResolve'
import {
  linkChildRowIsForeignParticipantPlaceholder,
  miLinkChildRowBelongsToParticipant,
  miLinkChildRowBusinessFieldRank,
  miParentRowAlignsWithChildRow,
  miChildFkConfigOfBinding,
  resolveMiChildPrimaryKeyColumns,
  resolveMiChildStructuralFkColumns,
  resolveMiChildStructuralParentFk,
  rowMatchesMiExpansionId,
  scoreMiLinkChildRowQuality,
} from './miLinkChildIdentity'
import type { MiChildFkConfig } from './miLinkChildIdentity'

/**
 * The row's own allocated primary key value, read through the DESIGNER's PK column.
 *
 * <p>Never `row.id` by default: People's designer PK is `idqc` in the Multi-Instance Subtask Demo,
 * and reading `id` there answered `undefined` for every row. `id` is kept only as the last resort
 * for callers with no config in scope (legacy payloads whose PK genuinely is `id`).
 */
function linkChildRowAllocatedPk(
  row: Record<string, unknown>,
  config?: MiChildFkConfig | null,
): string | undefined {
  const pkCols = resolveMiChildPrimaryKeyColumns(config)
  for (const col of pkCols.length > 0 ? pkCols : ['id']) {
    const v = row[col]
    if (v != null && isAllocatedUuidPrimaryKey(v)) return String(v).trim()
  }
  return undefined
}

function pickAllocatedUuidFromLinkChildGroup(
  group: Record<string, unknown>[],
  config?: MiChildFkConfig | null,
): string | undefined {
  for (const r of group) {
    const id = linkChildRowAllocatedPk(r, config)
    if (id) return id
  }
  return undefined
}

/**
 * Which column to merge a participant's fragment rows on. Taken from the designer FK config; the
 * first FK column that any row in the group actually carries wins. Returns null when the caller
 * supplied no config — the caller then skips the merge instead of guessing a column name.
 */
function resolveParticipantMergePkField(
  group: Record<string, unknown>[],
  config?: MiChildFkConfig | null,
): string | null {
  const fkColumns = resolveMiChildStructuralFkColumns(config)
  for (const rec of group) {
    for (const fk of fkColumns) {
      if (normalizeMiLinkMatchId(rec[fk])) return fk
    }
  }
  return fkColumns[0] ?? null
}

/**
 * After {@link repairMisassignedPrimaryKeyFromParentId} clears a misassigned {@code id}, re-copy the
 * allocated PK from the persisted variables slice when the in-memory row still carries form payload
 * but lost its id. Without this, inline form-below-table reads nested stubs with empty id even though
 * {@code __subTables__} already holds the UUID from a prior Save.
 */
export function backfillMiLinkChildPrimaryKeysFromVariables<
  T extends {
    bindingId: number
    tableName?: string
    physicalTableName?: string
    data: any[]
    foreignKeyField?: string | null
    bindingLinkMode?: string | null
    fieldDefinitions?: MiChildFkConfig['fieldDefinitions']
    columns?: Array<{ field?: string }> | null
  },
>(
  bindings: T[],
  savedSubTables: Record<string, unknown> | null | undefined,
  myRowId: string | number | null | undefined,
  miCollectionTableId?: number | null,
): void {
  if (!savedSubTables || typeof savedSubTables !== 'object') return
  // 调用方已经知道 collection 的 tableId —— 分类判据也要用它，否则判不出 participant-child。
  const miKindCtx = { miCollectionTableId: miCollectionTableId ?? null, primaryTableId: null }
  for (const binding of bindings) {
    if (!isMiParticipantScopedSubTableBinding(binding, miKindCtx)) continue
    const config = miChildFkConfigOfBinding(binding, miCollectionTableId)
    const saved = getSavedSubTableRows(savedSubTables, binding) ?? []
    if (!Array.isArray(binding.data) || binding.data.length === 0) continue
    for (let i = 0; i < binding.data.length; i++) {
      const row = binding.data[i]
      if (!row || typeof row !== 'object') continue
      const rec = row as Record<string, unknown>
      const existingId = rec.id
      if (existingId != null && String(existingId).trim() !== '') continue
      // A sibling participant's placeholder row (id_idw points elsewhere, no structural FK) must NOT inherit
      // the current participant's allocated id — that collides PKs and collapse then leaks its id_idw onto the
      // current row (#1444). Only backfill rows that actually belong to the current participant.
      if (myRowId != null && linkChildRowIsForeignParticipantPlaceholder(rec, myRowId, config)) continue
      const participantKey =
        resolveMiChildStructuralParentFk(rec, config)
        ?? (myRowId != null ? normalizeMiLinkMatchId(myRowId) : null)
      if (!participantKey) continue
      const donor = saved.find(s => {
        if (!s || typeof s !== 'object') return false
        const sr = s as Record<string, unknown>
        const sid = sr.id
        if (sid == null || String(sid).trim() === '') return false
        const sidNorm = normalizeMiLinkMatchId(sid)
        if (!sidNorm || sidNorm === participantKey) return false
        // Participant identity of the saved row: structural parent FK first, else its id_idw
        // (the participant discriminator for People-style link children whose PK is plain `id`).
        // NEVER the donor's own `id`: that is the allocated UUID, which can never equal the
        // parent's id_idw — the old `?? sidNorm` fallback made FK-less donors unmatchable, so
        // hydration lost the persisted UUID and every Save re-allocated a fresh PK (id churn).
        const sParticipant =
          resolveMiChildStructuralParentFk(sr, config) ?? normalizeMiLinkMatchId(sr.id_idw)
        return sParticipant != null && sParticipant === participantKey
      }) as Record<string, unknown> | undefined
      if (donor?.id != null && String(donor.id).trim() !== '') {
        binding.data[i] = { ...rec, id: donor.id }
      }
    }
  }
}

/** When multiple link-child rows share the same participant FK, merge payloads (sub form1 → sub form2). */
export function collapseMiLinkChildRowsToOnePerParticipant(
  rows: unknown[],
  config?: MiChildFkConfig | null,
): any[] {
  if (!Array.isArray(rows) || rows.length <= 1) return Array.isArray(rows) ? [...rows] : []
  const byParticipant = new Map<string, Record<string, unknown>[]>()
  const ungrouped: Record<string, unknown>[] = []
  for (const raw of rows) {
    if (!raw || typeof raw !== 'object') continue
    const rec = raw as Record<string, unknown>
    const pid = resolveMiChildStructuralParentFk(rec, config)
    if (!pid) {
      ungrouped.push(rec)
      continue
    }
    const g = byParticipant.get(pid) ?? []
    g.push(rec)
    byParticipant.set(pid, g)
  }
  const out: any[] = [...ungrouped]
  for (const group of byParticipant.values()) {
    if (group.length === 1) {
      out.push(group[0])
      continue
    }
    // 同一参与者**本来就可以有多行**（People 是普通子表，用户点 Add 就加一行）。
    // 这个折叠只为合并「同一行被 sub form1 / sub form2 拆成的碎片」，不是去重：
    // 每行都带着自己**已分配的 UUID 主键**时，它们是不同的行，折叠会把用户刚加的行吃掉。
    // 实测：加两行 People 后 Save，3 行被折成 1 行，刷新只剩最早那条。
    // 主键列取自设计器（binding.primaryKeyFields / isPrimaryKey 字段定义），不是写死的 `id`：
    // 本 demo 的 People 主键叫 idqc，读 `id` 会让每行都「没有已分配主键」，守卫失效 ——
    // 两条各自独立的行于是被当成同一行的碎片合并，刷新后用户只剩一条。
    const allocatedIds = group
      .map(r => linkChildRowAllocatedPk(r, config))
      .filter((v): v is string => !!v)
    if (allocatedIds.length === group.length && new Set(allocatedIds).size === group.length) {
      out.push(...group)
      continue
    }
    const pkField = resolveParticipantMergePkField(group, config)
    // No FK config reachable → we cannot tell fragments of one row apart from separate rows.
    // Keep the group intact rather than merging on a guessed column and eating the user's rows.
    if (!pkField) {
      out.push(...group)
      continue
    }
    const sorted = [...group].sort(
      (a, b) =>
        miLinkChildRowBusinessFieldRank(a) - miLinkChildRowBusinessFieldRank(b)
        || scoreMiLinkChildRowQuality(a) - scoreMiLinkChildRowQuality(b),
    )
    let merged: any[] = []
    for (const r of sorted) {
      merged = mergeSubTableRowsByRowId(merged, [r], [pkField])
    }
    const allocatedId = pickAllocatedUuidFromLinkChildGroup(group, config)
    const row = merged[0] ?? sorted[sorted.length - 1]
    if (row && allocatedId) {
      // 回填到设计器主键列（没有配置时才退回 `id`），否则会给 idqc 表凭空加一个 `id` 字段。
      const pkCol = resolveMiChildPrimaryKeyColumns(config)[0] ?? 'id'
      out.push({ ...(row as Record<string, unknown>), [pkCol]: allocatedId })
    } else if (row) {
      out.push(row)
    }
  }
  return out
}

/**
 * MI nested link-form slices often split placeholder ({@code id}, {@code task_status}) and real fields across
 * multiple objects in the same array — fold into one row for modal / binding hydration.
 */
export function collapseSubTableRowsPreferFilled(rows: any[]): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  if (rows.length === 1) return [...rows]
  // Same participant split across placeholder + payload fragments; first-non-empty used to
  // freeze stale task_current_node (sub form1) — use MI-aware row merge instead.
  return mergeSubTableRowsByRowId([], rows, ['id'])
}

/**
 * Rows in a link-child binding slice that belong to the given MI parent participant row.
 *
 * @param collectionPrimaryKeyFields MI collection 的设计器主键列 —— 宿主行的参与者标识按它取。
 *   不传时归属判定退回历史列名表（id_idw / rowId / …），collection 主键改过名的 FU 会判不出。
 */
export function pickMiLinkChildRowsForParent(
  parentRow: Record<string, unknown>,
  candidateRows: unknown[],
  primaryKeyFields?: string[] | null,
  config?: MiChildFkConfig | null,
  collectionPrimaryKeyFields?: string[] | null,
): any[] {
  if (!Array.isArray(candidateRows) || candidateRows.length === 0) return []
  const matched = candidateRows.filter(
    r =>
      r &&
      typeof r === 'object' &&
      miParentRowAlignsWithChildRow(
        parentRow,
        r as Record<string, unknown>,
        config,
        collectionPrimaryKeyFields,
      ),
  )
  if (matched.length === 0) return []
  const deduped = collapseMiLinkChildRowsToOnePerParticipant(matched, config)
  return mergeSubTableRowsByRowId([], deduped, primaryKeyFields ?? null)
}

/** Find the participant row in a sub-table binding for the current MI sub-task. */
export function findSubTableRowByMiExpansionId(
  rows: unknown[],
  miRowId: string | number | null | undefined,
  primaryKeyFields?: string[] | null,
): Record<string, unknown> | null {
  if (miRowId == null || String(miRowId).trim() === '') return null
  if (!Array.isArray(rows) || rows.length === 0) return null
  for (const row of rows) {
    if (
      row && typeof row === 'object'
      // 设计器主键优先；名字列表只是兜底（见 rowMatchesMiExpansionId）
      && rowMatchesMiExpansionId(row as Record<string, unknown>, miRowId, primaryKeyFields)
    ) {
      return row as Record<string, unknown>
    }
  }
  return null
}

/**
 * MI assignee todo: after {@link isolateMiSubTaskData} the parent sub-table usually has exactly one row for this
 * participant, but {@code _currentItem.rowId} may be the designer PK ({@code id_idw}) while the hydrated row only
 * exposes SQL {@code id} (e.g. 6532) — strict expansion match fails and link-form inline subtable2 stays empty.
 */
export function findMiIsolatedParentRow(
  rows: unknown[],
  miRowId: string | number | null | undefined,
  primaryKeyFields?: string[] | null,
  config?: MiChildFkConfig | null,
): Record<string, unknown> | null {
  const matched = findSubTableRowByMiExpansionId(rows, miRowId, primaryKeyFields)
  if (matched) return matched
  if (!Array.isArray(rows) || rows.length !== 1) return null
  const only = rows[0]
  if (!only || typeof only !== 'object') return null
  const rec = only as Record<string, unknown>
  if (miRowId != null && String(miRowId).trim() !== '') {
    // 「唯一那行是不是别的参与者」的排他判定：按设计器主键取值，**不猜列名**。
    //
    // 没有设计器主键时**不能**退化成"放行"：这个分支的语义是「就一行，姑且当成我的」，
    // 猜错就是把别人的行交给当前用户编辑。既然无从判定归属，就**拒绝**这一行（返回 null），
    // 调用方会退回"这个参与者还没有自己的行"，是安全的一侧。
    //
    // 也**不能**抛错：本函数会被逐个 peer binding 调用（useSubTableBindings 等），
    // 共享附件（main_id）、非 MI 单行子表本来就没有设计器 PK —— 在这里抛会中断整个 Save。
    // link-child 行（People 式）通过**结构 FK**（sub_task_id / participant_id …）指向参与者，
    // 它自己的主键是行 UUID。带结构 FK 时按它判归属 —— 命中就是我的行，不能再拿主键去比：
    // 那个比较对 link-child 行**恒不相等**（UUID ≠ 参与者 id），会把用户刚存的行判成别人的、
    // 于是页面渲染 0 行、下一次保存又把它当陈旧数据丢掉（实测 People 加两行 Save 后消失）。
    //
    // **必须先确认真有结构 FK 才走这条捷径**：miLinkChildRowBelongsToParticipant 对「一个参与者
    // 标识都没有」的行会放行（那是给弹窗里刚新增、FK 尚未种下的行留的口子）。而设计器主键不叫
    // id_idw / id 时（如 id_idwvvbz），别人的行在它眼里同样"没有标识"——不加这道门就会走捷径
    // return rec，把下面那段本该拒绝外来行的主键排他判定整段跳过，等于把别人的行交给当前用户编辑。
    if (resolveMiChildStructuralParentFk(rec, config) != null) {
      if (miLinkChildRowBelongsToParticipant(rec, miRowId, config)) return rec
    }
    if (linkChildRowIsForeignParticipantPlaceholder(rec, miRowId, config)) return null

    const pkNames = (primaryKeyFields ?? []).map(f => String(f ?? '').trim()).filter(Boolean)
    // 无主键：无从判定这唯一一行归谁 —— 拒绝（放行 = 把别人的行交给当前用户编辑）。
    // 是不是"子任务表缺主键"这种配置错误，由 binding 入口 resolveSubTablePrimaryKeyFields 负责判定。
    if (pkNames.length === 0) return null
    const mid = normalizeMiLinkMatchId(miRowId)
    for (const name of pkNames) {
      const rowPk = normalizeMiLinkMatchId(rec[name])
      if (rowPk && mid && rowPk !== mid) return null
    }
  }
  return rec
}

/**
 * Gather child-table rows nested under {@code parentRows[*].__subTables__} for the given child binding.
 * Exported for FormRenderer inline form-below-table when {@code target.data} is thin/empty but rows nest under
 * parent rows (e.g. legacy {@code bindingId} keys after BPMN form copy).
 */
export function scopeMiLinkChildRowsForParentRow(
  parentRow: Record<string, unknown>,
  candidateRows: unknown[],
  config?: MiChildFkConfig | null,
  /** MI collection 的设计器主键列 —— 宿主行的参与者标识按它取，不猜列名。 */
  collectionPrimaryKeyFields?: string[] | null,
): Record<string, unknown>[] {
  if (!Array.isArray(candidateRows)) return []
  return candidateRows.filter(
    (r): r is Record<string, unknown> =>
      !!r &&
      typeof r === 'object' &&
      miParentRowAlignsWithChildRow(
        parentRow,
        r as Record<string, unknown>,
        config,
        collectionPrimaryKeyFields,
      ),
  )
}

/** Participant-identity keys an MI host row (a collection/participant row) can carry. */
const MI_HOST_ROW_PARTICIPANT_KEYS = [
  'id_idw',
  'rowId',
  'participant_id',
  'participantId',
] as const

/**
 * True when a host row identifies ONE MI participant, i.e. scoping its nested child rows is meaningful.
 *
 * <p>Deliberately does NOT accept a bare {@code id}: plain (non-MI) link-form and nested-dialog hosts
 * carry one too, and treating that as a participant key would start filtering sub-tables on forms that
 * have no MI sub-process at all.
 *
 * <p>Also gates whether the cross-participant {@code binding.data} fallback may be used at all — for
 * an MI participant host it must not be, since that pool holds every sibling sub-task's rows.
 */
export function hostRowIsMiParticipant(
  row: Record<string, unknown> | null | undefined,
  collectionPrimaryKeyFields?: string[] | null,
): boolean {
  if (!row || typeof row !== 'object') return false
  // 设计器主键优先：MI collection 的 PK 叫什么由配置决定（demo FU 改名后是 id_idwze）。
  // 只认下面那张历史列名表的话，改名即失效 —— 宿主行认不出来，嵌套子表就完全不做参与者过滤，
  // 于是一个参与者的内联表单里会同时列出别人的子行（实测 2 行而非 1 行）。
  for (const pk of collectionPrimaryKeyFields ?? []) {
    const name = String(pk ?? '').trim()
    if (name && normalizeMiLinkMatchId(row[name])) return true
  }
  for (const k of MI_HOST_ROW_PARTICIPANT_KEYS) {
    if (normalizeMiLinkMatchId(row[k])) return true
  }
  return false
}

/**
 * Scope a link-child slice (People, subtable2, …) to the MI participant row that hosts it.
 *
 * <p>Used by the render path for a sub-table nested inside an <b>Inline Form</b>: that form edits
 * exactly one participant row, so its nested grid must show only that participant's child rows.
 * Both slice sources need this — the nested {@code parentRow.__subTables__} map (which a stale BPMN
 * copy or an older save can leave pooled across participants) and, critically, the flat top-level
 * {@code binding.data} fallback, which is cross-participant by construction. Without it a participant
 * who had added no rows of their own was shown — and could edit — a sibling sub-task's rows.
 *
 * <p>Rows carrying no participant identity at all are KEPT: those are freshly added rows whose
 * structural FK has not been seeded yet (seeding happens at save, not on input), and dropping them
 * would make a row vanish the moment the user typed it.
 *
 * <p>No-ops (returns the input unchanged) when the host row is not an MI participant row, so plain
 * link-form / nested-dialog hosts and shared process-level tables keep today's behavior.
 */
export function scopeLinkChildRowsToMiHostRow(
  hostRow: Record<string, unknown> | null | undefined,
  candidateRows: unknown[],
  config?: MiChildFkConfig | null,
  collectionPrimaryKeyFields?: string[] | null,
): unknown[] {
  if (!Array.isArray(candidateRows) || candidateRows.length === 0) return candidateRows
  if (!hostRowIsMiParticipant(hostRow, collectionPrimaryKeyFields)) return candidateRows

  // 取宿主行的参与者 id：同样先按设计器主键，再退回历史列名。
  const pid =
    (collectionPrimaryKeyFields ?? [])
      .map(k => normalizeMiLinkMatchId(hostRow![String(k ?? '').trim()]))
      .find(v => v != null)
    ?? MI_HOST_ROW_PARTICIPANT_KEYS
      .map(k => normalizeMiLinkMatchId(hostRow![k]))
      .find(v => v != null)
  if (!pid) return candidateRows

  return candidateRows.filter(raw => {
    if (!raw || typeof raw !== 'object') return true
    const rec = raw as Record<string, unknown>
    // No participant identity yet — a fresh row the user just added. Keep it.
    if (!resolveMiChildStructuralParentFk(rec, config) && !normalizeMiLinkMatchId(rec.id_idw)) return true
    return miLinkChildRowBelongsToParticipant(rec, pid, config)
  })
}
