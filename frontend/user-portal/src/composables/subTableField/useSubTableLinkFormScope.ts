import type { FormField } from '@/components/formRendererHelpers'
import { extractUserIdFromCellValue } from '@/components/subTableAddDialogHelpers'
import type { SubTableBinding, SubTableFieldProps } from './subTableFieldTypes'
import {
  buildFkListForChildMatch,
  filterLinkedChildRowsByMiTaskStatus,
  filterLinkedChildRowsByParentIdIdw,
  isMiStyleParentRowForLinkForm,
  isTerminalMiParticipantRow,
  narrowRowsByParentIdSetWithFk,
  normalizeFkIdForMatch,
  rowHasAnyFkColumn,
  rowMatchesParentFk,
  rowMatchesParentForLinkModal,
  shallowScalarMatchesAnyParentId
} from './subTableLinkFormRowMatch'
import {
  countLinkFormFields,
  isAllocatedLinkChildBusinessId,
  isPresentLinkedModalValue,
  linkFormRowsLackFormPayload,
  resolveLinkFormFieldValueForModal,
  scoreRowForLinkedFormFields
} from './subTableLinkFormFields'

/** Parent-row scoped matching/scoring for link-form child rows (depends on component props). */
export function useSubTableLinkFormScope(
  props: SubTableFieldProps,
  deps: { resolveSubTableRowPk: (row: Record<string, unknown> | null | undefined) => string | number | null },
) {
  const { resolveSubTableRowPk } = deps

  /** MI rows may duplicate a main-form scalar in {@code row.id}; omit it from FK sets when relation PK differs. */
  function shouldIncludeMiParentRowIdInLinkMatch(parentRow: Record<string, unknown>): boolean {
    const isMiParent =
      parentRow.task_status !== undefined
      && parentRow.task_status !== null
      && String(parentRow.task_status).trim() !== ''
    if (!isMiParent) return true
    const pk = resolveSubTableRowPk(parentRow)
    const rowIdNorm = normalizeFkIdForMatch(parentRow.id)
    const pkNorm = normalizeFkIdForMatch(pk)
    return rowIdNorm != null && pkNorm != null && rowIdNorm === pkNorm
  }

  /**
   * Sub-table / MI **row** identity only (relation id, row id, Flowable participant columns on the row).
   * Omits portal-user ids from assignee snapshots — multiple MI instances often share the same assignee UUID; including it
   * in parent id sets makes every instance match the same link-form child row (runtime: same firstChildIds for two rows).
   */
  function collectSubTableScopedParentIdsForLinkMatch(
    parentRow: Record<string, unknown> | null | undefined
  ): Set<string> {
    const out = new Set<string>()
    const add = (v: unknown) => {
      const n = normalizeFkIdForMatch(v)
      if (n != null) out.add(n)
    }
    if (!parentRow || typeof parentRow !== 'object') return out
    const pk = resolveSubTableRowPk(parentRow)
    add(pk)
    /**
     * MI parent rows often carry a duplicated main-form scalar in {@code row.id} (e.g. "uuoo") shared by every
     * instance while the relation PK is {@code id_idw} / designer PK (8778 vs 4554). Including {@code row.id} in the
     * scoped set makes shallow FK fallback match the same wrong child row for every Details click.
     */
    if (shouldIncludeMiParentRowIdInLinkMatch(parentRow as Record<string, unknown>)) {
      add(parentRow.id)
    }
    add(parentRow.id_idw)
    add((parentRow as { id_idw_id?: unknown }).id_idw_id)
    for (const pkf of props.primaryKeyFields ?? []) {
      if (typeof pkf === 'string' && pkf.trim()) add(parentRow[pkf.trim()])
    }
    add(parentRow.rowId)
    add(parentRow.participant_id)
    add(parentRow.participantId)
    return out
  }

  /** Parent MI row id variants child FK columns may reference (string-normalized; avoids Number(uuid) → NaN). */
  function collectParentIdsForChildFkMatch(parentRow: Record<string, unknown> | null | undefined): Set<string> {
    const out = new Set<string>()
    const add = (v: unknown) => {
      const n = normalizeFkIdForMatch(v)
      if (n != null) out.add(n)
    }
    if (!parentRow || typeof parentRow !== 'object') return out
    add(resolveSubTableRowPk(parentRow))
    if (shouldIncludeMiParentRowIdInLinkMatch(parentRow)) {
      add(parentRow.id)
    }
    add(parentRow.id_idw)
    for (const pkf of props.primaryKeyFields ?? []) {
      if (typeof pkf === 'string' && pkf.trim()) add(parentRow[pkf.trim()])
    }
    add(parentRow.rowId)
    add(parentRow.participant_id)
    add(parentRow.participantId)
    add(parentRow.user_id)
    add(parentRow.userId)
    add(parentRow.assignee_id)
    add(parentRow.assigneeId)
    const part = parentRow.participant
    if (part && typeof part === 'object') {
      const po = part as Record<string, unknown>
      add(po.id)
      add(po.userId)
      add(po.user_id)
    }
    /**
     * Child link-form rows often FK to portal user id (UUID) from the MI assignee snapshot, not the sub-table row id.
     * {@link extractUserIdFromCellValue} matches task/detail hydration used elsewhere in this component.
     */
    const af = props.assigneeField
    if (typeof af === 'string' && af.trim()) {
      add(extractUserIdFromCellValue(parentRow[af.trim()]))
    }
    for (const nestKey of ['assignee', 'assignee_user', 'owner', 'user', 'handler']) {
      const v = parentRow[nestKey]
      if (v && typeof v === 'object' && !Array.isArray(v)) {
        const o = v as Record<string, unknown>
        add(o.id)
        add(o.userId)
        add(o.user_id)
      }
    }
    return out
  }

  /**
   * MI link child (subtable2): {@code id_idw} may match parent while {@code id} still holds another
   * participant's scalar (e.g. id=44, id_idw=88). Reject when selecting rows to display or save.
   */
  function miLinkFormChildRowMatchesParent(
    parentRow: Record<string, unknown>,
    childRow: unknown,
    binding?: SubTableBinding
  ): boolean {
    if (!childRow || typeof childRow !== 'object') return false
    if (!isMiStyleParentRowForLinkForm(parentRow)) return true
    const parentKey =
      normalizeFkIdForMatch(parentRow.id_idw)
      ?? normalizeFkIdForMatch(resolveSubTableRowPk(parentRow))
    if (parentKey == null) return true
    const rec = childRow as Record<string, unknown>
    const subTaskId = normalizeFkIdForMatch(rec.sub_task_id ?? rec.subTaskId)
    if (subTaskId != null && subTaskId === parentKey) return true
    const fkList = buildFkListForChildMatch(binding)
    for (const k of fkList) {
      const v = rec[k]
      if (v != null && v !== '' && normalizeFkIdForMatch(v) === parentKey) return true
    }
    const childId = normalizeFkIdForMatch(rec.id)
    const childIdIdw = normalizeFkIdForMatch(rec.id_idw)
    if (childIdIdw === parentKey) {
      return childId == null || childId === parentKey
    }
    if (childId === parentKey) return true
    return false
  }

  function filterRowsByMiLinkFormParent(
    parentRow: Record<string, any>,
    rows: any[],
    binding?: SubTableBinding
  ): any[] {
    if (!isMiStyleParentRowForLinkForm(parentRow) || !Array.isArray(rows) || rows.length === 0) return rows
    return rows.filter(r => miLinkFormChildRowMatchesParent(parentRow, r, binding))
  }

  function filterLinkedChildRowsByParentAssignee(parentRow: Record<string, any>, rows: any[]): any[] {
    const af = props.assigneeField
    if (!af || !isMiStyleParentRowForLinkForm(parentRow) || !Array.isArray(rows) || rows.length === 0) {
      return rows
    }
    const pa = extractUserIdFromCellValue(parentRow[af.trim()])
    if (!pa) return rows
    const matched = rows.filter(r => {
      const ca = extractUserIdFromCellValue((r as Record<string, unknown>)[af.trim()])
      return ca && ca === pa
    })
    return matched.length > 0 ? matched : rows
  }

  function scoreLinkedChildRowForParent(
    parentRow: Record<string, any>,
    childRow: unknown,
    binding?: SubTableBinding,
    fkList?: string[],
    scopedIds?: Set<string>
  ): number {
    if (!childRow || typeof childRow !== 'object') return -1
    const fkListLocal = fkList ?? buildFkListForChildMatch(binding)
    const scoped = scopedIds ?? collectSubTableScopedParentIdsForLinkMatch(parentRow as Record<string, unknown>)
    const fieldScore = scoreRowForLinkedFormFields(childRow, binding?.formFields)
    const totalFields = countLinkFormFields(binding?.formFields)
    let score = fieldScore * 10
    if (totalFields > 1 && fieldScore > 0 && fieldScore < totalFields) score -= 200
    if (rowMatchesParentFk(childRow, scoped, fkListLocal)) score += 100
    else if (shallowScalarMatchesAnyParentId(childRow, scoped)) score += 10
    const ps = String(parentRow.task_status ?? '').trim().toUpperCase()
    const cs = String((childRow as { task_status?: unknown }).task_status ?? '').trim().toUpperCase()
    if (isMiStyleParentRowForLinkForm(parentRow as Record<string, unknown>)) {
      if (ps && cs) score += ps === cs ? 500 : -1000
    } else if (ps && cs && ps === cs) {
      score += 50
    }
    const af = props.assigneeField
    if (typeof af === 'string' && af.trim()) {
      const pa = extractUserIdFromCellValue(parentRow[af.trim()])
      const ca = extractUserIdFromCellValue((childRow as Record<string, unknown>)[af.trim()])
      if (pa && ca && pa === ca) score += 80
    }
    const parentIdIdw = normalizeFkIdForMatch(parentRow.id_idw)
    const childIdIdw = normalizeFkIdForMatch((childRow as Record<string, unknown>).id_idw)
    if (parentIdIdw && childIdIdw && parentIdIdw === childIdIdw) score += 800
    return score
  }

  function pickBestLinkedChildRowsForParentRow(
    parentRow: Record<string, any>,
    rows: any[],
    binding?: SubTableBinding
  ): any[] {
    if (!Array.isArray(rows) || rows.length === 0) return []
    let candidates = filterRowsByMiLinkFormParent(parentRow, rows, binding)
    candidates = filterLinkedChildRowsByParentAssignee(parentRow, candidates)
    candidates = filterLinkedChildRowsByParentIdIdw(parentRow, candidates)
    const scoped = filterLinkedChildRowsByMiTaskStatus(parentRow, candidates)
    if (scoped.length > 0) {
      candidates = scoped
    } else if (isTerminalMiParticipantRow(parentRow)) {
      candidates = filterLinkedChildRowsByParentIdIdw(parentRow, rows)
      if (candidates.length === 0) return []
    }
    if (candidates.length === 1) return candidates
    const fkList = buildFkListForChildMatch(binding)
    const scopedIds = collectSubTableScopedParentIdsForLinkMatch(parentRow as Record<string, unknown>)
    const ranked = [...candidates].sort(
      (a, b) =>
        scoreLinkedChildRowForParent(parentRow, b, binding, fkList, scopedIds)
        - scoreLinkedChildRowForParent(parentRow, a, binding, fkList, scopedIds)
    )
    return ranked[0] != null ? [ranked[0]] : []
  }

  /** When Details uses process-level fallback rows (no row.__subTables__), narrow to this parent participant if child rows carry FKs. */
  function filterLinkedChildRowsForParentRow(
    parentRow: Record<string, any>,
    rows: any[],
    binding?: SubTableBinding
  ): any[] {
    if (!Array.isArray(rows) || rows.length === 0) return rows
    const scopedIds = collectSubTableScopedParentIdsForLinkMatch(parentRow as Record<string, unknown>)
    const fullIds = collectParentIdsForChildFkMatch(parentRow as Record<string, unknown>)
    if (scopedIds.size === 0 && fullIds.size === 0) return rows

    const fkList = buildFkListForChildMatch(binding)
    let filtered: any[] = []
    if (scopedIds.size > 0) {
      filtered = narrowRowsByParentIdSetWithFk(rows, scopedIds, fkList)
    }
    if (filtered.length === 0 && fullIds.size > 0) {
      filtered = narrowRowsByParentIdSetWithFk(rows, fullIds, fkList)
    }
    if (filtered.length === 0) {
      filtered = isMiStyleParentRowForLinkForm(parentRow) ? [] : rows
    }

    const miScoped = filterLinkedChildRowsByMiTaskStatus(parentRow, filtered.length > 0 ? filtered : rows)
    if (miScoped.length > 0 && miScoped.length < (filtered.length > 0 ? filtered : rows).length) {
      filtered = miScoped
    }

    if (isMiStyleParentRowForLinkForm(parentRow)) {
      const pool = filtered.length > 0 ? filtered : rows
      filtered = filterRowsByMiLinkFormParent(parentRow, pool, binding)
    }

    if (binding?.formFields?.length) {
      const needsPick =
        filtered.length > 1
        || linkFormRowsLackFormPayload(filtered, binding.formFields)
      if (needsPick) {
        const pickSource = filterLinkedChildRowsByMiTaskStatus(parentRow, rows)
        const best = pickBestLinkedChildRowsForParentRow(
          parentRow,
          pickSource.length > 0 ? pickSource : rows,
          binding
        )
        if (best.length > 0) {
          const bestScore = scoreRowForLinkedFormFields(best[0], binding.formFields)
          const curScore = scoreRowForLinkedFormFields(filtered[0], binding.formFields)
          if (filtered.length > 1 || bestScore > curScore) return best
        }
      }
    }
    return filtered.length > 1
      ? pickBestLinkedChildRowsForParentRow(parentRow, filtered, binding)
      : filtered
  }

  /** When several link-form child rows are concatenated, put the row keyed to {@code parentRow} first — buildLinkedFormData only uses index 0. */
  function preferLinkedChildRowMatchingParent(
    parentRow: Record<string, any>,
    rows: any[],
    binding?: SubTableBinding
  ): any[] {
    if (!Array.isArray(rows) || rows.length <= 1 || !parentRow || typeof parentRow !== 'object') return rows
    const scopedIds = collectSubTableScopedParentIdsForLinkMatch(parentRow as Record<string, unknown>)
    const fullIds = collectParentIdsForChildFkMatch(parentRow as Record<string, unknown>)
    if (scopedIds.size === 0 && fullIds.size === 0) return rows

    const fkList = buildFkListForChildMatch(binding)
    const findWith = (pid: Set<string>): number => {
      if (pid.size === 0) return -1
      let i = rows.findIndex(r => rowMatchesParentFk(r, pid, fkList))
      if (i < 0) i = rows.findIndex(r => shallowScalarMatchesAnyParentId(r, pid))
      return i
    }
    let matchIdx = findWith(scopedIds)
    if (matchIdx < 0) matchIdx = findWith(fullIds)
    if (matchIdx <= 0) return rows
    const next = [...rows]
    const [hit] = next.splice(matchIdx, 1)
    return [hit, ...next]
  }

  /**
   * When child rows expose FK columns to the parent MI row, keep rows whose FK matches any of
   * {@link collectParentIdsForChildFkMatch}. Returns {@code null} if no FK column is present (caller unchanged).
   */
  function strictChildRowsForParentByFk(
    parentRow: Record<string, any>,
    rows: any[],
    binding?: SubTableBinding
  ): any[] | null {
    if (!Array.isArray(rows) || rows.length === 0 || !parentRow || typeof parentRow !== 'object') return null
    const scopedIds = collectSubTableScopedParentIdsForLinkMatch(parentRow as Record<string, unknown>)
    const fullIds = collectParentIdsForChildFkMatch(parentRow as Record<string, unknown>)
    if (scopedIds.size === 0 && fullIds.size === 0) return null

    const fkList = buildFkListForChildMatch(binding)
    const hasAnyFk = rows.some(r => rowHasAnyFkColumn(r, fkList))

    const matchWith = (pid: Set<string>): any[] => {
      if (pid.size === 0) return []
      let m = rows.filter(r => rowMatchesParentFk(r, pid, fkList))
      if (m.length === 0) m = rows.filter(r => shallowScalarMatchesAnyParentId(r, pid))
      return m
    }

    let matched = matchWith(scopedIds)
    if (matched.length === 0) matched = matchWith(fullIds)

    if (matched.length === 0) return null
    if (!hasAnyFk && rows.length > 1 && matched.length === rows.length) return null

    return matched
  }

  /**
   * Link detail modal reads binding.data[0] only — if variables merged MI placeholders first, row 0 is empty while
   * another index holds the real payload; move the best-scoring row to the front without dropping siblings.
   */
  function promoteBestRowForLinkFormModal(
    rows: any[],
    formFields: FormField[] | undefined,
    parentRow?: Record<string, any> | null,
    binding?: SubTableBinding
  ): { rows: any[]; movedFrom: number | null } {
    if (!Array.isArray(rows) || rows.length <= 1 || !formFields?.length) return { rows, movedFrom: null }

    const fkList = binding ? buildFkListForChildMatch(binding) : []
    const scopedIds =
      parentRow && typeof parentRow === 'object'
        ? collectSubTableScopedParentIdsForLinkMatch(parentRow as Record<string, unknown>)
        : new Set<string>()
    const fullIds =
      parentRow && typeof parentRow === 'object'
        ? collectParentIdsForChildFkMatch(parentRow as Record<string, unknown>)
        : new Set<string>()
    const collectMatchingIdx = (pid: Set<string>): number[] => {
      const idx: number[] = []
      if (pid.size > 0) {
        rows.forEach((r, i) => {
          if (rowMatchesParentForLinkModal(r, pid, fkList, true)) idx.push(i)
        })
      }
      return idx
    }
    let matchingIdx = collectMatchingIdx(scopedIds)
    if (matchingIdx.length === 0) matchingIdx = collectMatchingIdx(fullIds)

    if (matchingIdx.length === 1) {
      const idx = matchingIdx[0]!
      if (idx === 0) return { rows, movedFrom: null }
      const next = [...rows]
      const [pick] = next.splice(idx, 1)
      return { rows: [pick, ...next], movedFrom: idx }
    }

    const candidateIndices =
      matchingIdx.length > 1 ? matchingIdx : rows.map((_, i) => i)

    let bestIdx = candidateIndices[0]!
    let bestScore = scoreRowForLinkedFormFields(rows[bestIdx], formFields)
    for (let k = 1; k < candidateIndices.length; k++) {
      const i = candidateIndices[k]!
      const sc = scoreRowForLinkedFormFields(rows[i], formFields)
      if (sc > bestScore) {
        bestScore = sc
        bestIdx = i
      }
    }
    if (bestIdx === 0) return { rows, movedFrom: null }
    const next = [...rows]
    const [pick] = next.splice(bestIdx, 1)
    return { rows: [pick, ...next], movedFrom: bestIdx }
  }

  function resolveMiLinkFormParentParticipantKey(
    parentRow: Record<string, unknown>,
  ): string | null {
    return (
      normalizeFkIdForMatch(parentRow.id_idw)
      ?? normalizeFkIdForMatch(resolveSubTableRowPk(parentRow))
    )
  }

  function backfillMiLinkFormModalFieldsFromParent(
    formData: Record<string, any>,
    parentRow: Record<string, unknown>,
    formFields: FormField[],
    savedRow: Record<string, unknown> | undefined,
    readonly: boolean,
  ): void {
    const parentKey = resolveMiLinkFormParentParticipantKey(parentRow)
    const parentScalar = parentRow.id_idw ?? resolveSubTableRowPk(parentRow)

    const walk = (fields: FormField[]) => {
      for (const field of fields) {
        if (field.type === 'card') {
          walk(field.children || [])
          continue
        }
        const lk = String(field.key || '').toLowerCase()
        if (lk === 'sub_task_id' || lk === 'subtaskid') {
          const cur = formData[field.key]
          if (!isPresentLinkedModalValue(cur) && parentScalar != null && String(parentScalar).trim() !== '') {
            formData[field.key] = resolveLinkFormFieldValueForModal(field, parentScalar, { readonly })
          }
        }
      }
    }
    walk(formFields)

    const idField = formFields.find(f => f.key === 'id' && f.type !== 'card')
    if (!idField) return
    const cur = formData.id
    const invalid =
      cur === null
      || cur === undefined
      || cur === ''
      || (idField.type === 'number' && typeof cur === 'string' && Number.isNaN(Number(String(cur).trim())))
    if (!invalid) return
    const rowPk = savedRow?.id
    if (!isAllocatedLinkChildBusinessId(rowPk, parentKey)) return
    formData.id = resolveLinkFormFieldValueForModal(idField, rowPk, { readonly })
  }

  return {
    collectSubTableScopedParentIdsForLinkMatch,
    collectParentIdsForChildFkMatch,
    miLinkFormChildRowMatchesParent,
    filterRowsByMiLinkFormParent,
    filterLinkedChildRowsByParentAssignee,
    pickBestLinkedChildRowsForParentRow,
    filterLinkedChildRowsForParentRow,
    preferLinkedChildRowMatchingParent,
    strictChildRowsForParentByFk,
    promoteBestRowForLinkFormModal,
    resolveMiLinkFormParentParticipantKey,
    backfillMiLinkFormModalFieldsFromParent
  }
}
