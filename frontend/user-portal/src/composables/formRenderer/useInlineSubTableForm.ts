import { ref } from 'vue'
import type { ComputedRef } from 'vue'
import {
  mergeSubTableRowsByRowId,
  collectNestedChildRowsFromPeerBindings,
  pullNestedRowsForBindingFromParentRows,
  findMiIsolatedParentRow,
  pickMiLinkChildRowsForParent,
  scoreMiLinkChildRowQuality,
  collapseMiLinkChildRowsToOnePerParticipant,
  getSavedSubTableRows,
  miParentRowAlignsWithChildRow,
  miLinkChildRowBelongsToParticipant,
} from '../tasks/shared'
import { resolveMiLinkIsolateInlineRow } from '../../utils/inlineFormBelowTableRuntime'
import type { FormField } from '../../components/formRendererHelpers'
import type { SubTableBinding } from './useSubTableBindings'
import {
  seedMiLinkInlineRowFkFromParent,
  resolveLinkFkCandidates,
  buildBindingTableIdMap,
  findInlineRowIndexForMi,
} from './inlineSubTableFormHelpers'

interface InlineSubTableFormDeps {
  currentMiRowId: () => number | string | null | undefined
  suppressLinkFormInitialData: () => boolean
  previewSubTables: () => boolean
  modelValue: () => Record<string, any>
  effectiveReadonly: ComputedRef<boolean>
  linkableSubTableBindings: ComputedRef<SubTableBinding[] | undefined>
  resolveBinding: (id?: number) => SubTableBinding | undefined
  resolveInlineFormSourceBinding: (field: FormField) => SubTableBinding | null
  resolveInlineFormFields: (field: FormField) => FormField[]
  handleSubTableUpdate: (bindingId: number, rows: any[]) => void
  emitSave: () => void
}

export function useInlineSubTableForm(deps: InlineSubTableFormDeps) {
  const { resolveBinding, resolveInlineFormSourceBinding, resolveInlineFormFields } = deps

  /** Manually selected row per binding (click on the sub-table) — drives the form-below-table. */
  const inlineFormSelectedRows = ref<Record<number, Record<string, unknown> | null>>({})

  function setInlineFormSelectedRow(bindingId: number | undefined, row: Record<string, unknown> | null) {
    if (bindingId == null || !Number.isFinite(Number(bindingId))) return
    // el-table emits current-change(null) whenever its data is replaced (inline-edit
    // round-trip re-sets binding.data) — keep the last real selection so the form
    // doesn't snap back to the first row.
    if (!row) return
    inlineFormSelectedRows.value = { ...inlineFormSelectedRows.value, [Number(bindingId)]: row }
  }

  /** Match by designer PK first (survives row edits), then id_idw/id, then structural equality. */
  function rowsMatchForSelection(
    a: Record<string, unknown> | null | undefined,
    b: Record<string, unknown> | null | undefined,
    pkFields: string[] | null,
  ): boolean {
    if (a === b) return a != null
    if (!a || !b) return false
    const candidates = [...(pkFields ?? []), 'id_idw', 'id']
    for (const k of candidates) {
      const av = a[k]
      if (av == null || String(av).trim() === '') continue
      if (b[k] == null) continue
      return String(av) === String(b[k])
    }
    try {
      return JSON.stringify(a) === JSON.stringify(b)
    } catch {
      return false
    }
  }

  function resolveTopLevelRowsForInlineTarget(target: SubTableBinding): any[] {
    const fromBinding = Array.isArray(target.data) ? target.data : []
    if (fromBinding.length > 0) return fromBinding
    const st = deps.modelValue()?.__subTables__
    if (!st || typeof st !== 'object') return []
    const saved = getSavedSubTableRows(st as Record<string, unknown>, target)
    return Array.isArray(saved) ? saved.map(r => ({ ...(r as Record<string, any>) })) : []
  }

  function mergeRowsForInlineFormTarget(field: FormField): {
    target: SubTableBinding
    rows: any[]
    isLinkTarget: boolean
  } | null {
    const own = resolveBinding(field._bindingId)
    if (!own) return null
    const target = resolveInlineFormSourceBinding(field) ?? own
    const isLinkTarget = target.bindingId !== own.bindingId
    const peers = deps.linkableSubTableBindings.value ?? []
    const pk = target.primaryKeyFields ?? own.primaryKeyFields ?? null
    const parentId = deps.currentMiRowId()
    const miIsolate =
      deps.suppressLinkFormInitialData()
      && parentId != null
      && String(parentId).trim() !== ''
      && isLinkTarget

    if (miIsolate) {
      const parentRow = findMiIsolatedParentRow(
        Array.isArray(own.data) ? own.data : [],
        parentId
      )
      if (parentRow) {
        const peerMap = buildBindingTableIdMap(peers)
        const nestedOnly = pullNestedRowsForBindingFromParentRows(
          {
            bindingId: target.bindingId,
            tableName: target.tableName,
            physicalTableName: target.physicalTableName,
            tableId: target.tableId ?? null
          },
          [parentRow],
          peerMap
        )
        let rows = pickMiLinkChildRowsForParent(
          parentRow,
          nestedOnly,
          pk,
        ).map(r => ({ ...(r as Record<string, any>) }))
        const topLevel = resolveTopLevelRowsForInlineTarget(target)
        const topForParent = pickMiLinkChildRowsForParent(
          parentRow,
          topLevel,
          pk,
        )
        if (topForParent.length > 0) {
          rows = mergeSubTableRowsByRowId(rows, topForParent, pk).map(r => ({
            ...(r as Record<string, any>)
          }))
        }
        rows = collapseMiLinkChildRowsToOnePerParticipant(rows)
        // Nested __subTables__ copy may lag behind the top-level slice and lack auto-PK id even when
        // binding.data / variables already hold the allocated UUID — prefer the richer top-level row.
        if (
          topForParent.length > 0
          && (
            rows.length === 0
            || (
              rows.length === 1
              && (rows[0]?.id == null || String(rows[0]?.id ?? '').trim() === '')
            )
          )
        ) {
          rows = collapseMiLinkChildRowsToOnePerParticipant([
            ...rows,
            ...topForParent.map(r => ({ ...(r as Record<string, any>) })),
          ])
        }
        return {
          target,
          isLinkTarget,
          rows
        }
      }
      return { target, isLinkTarget, rows: [] }
    }

    const nestedFromTarget = collectNestedChildRowsFromPeerBindings(target, peers, null)
    /** Table grid uses `own.data`; link-form inline uses `target` — merge both so the row list matches the grid. */
    let merged = mergeSubTableRowsByRowId(
      Array.isArray(own.data) ? own.data : [],
      Array.isArray(target.data) ? target.data : [],
      pk,
    )
    merged = mergeSubTableRowsByRowId(merged, nestedFromTarget, pk)
    return {
      target,
      isLinkTarget,
      rows: merged.map(r => ({ ...(r as Record<string, any>) })),
    }
  }

  /** Prefer "fat" snapshot rows when many duplicates exist (preview/read-only diagram clicks often defaulted to rows[0] thin placeholders). */
  function scoreInlineRowCompleteness(row: unknown, field: FormField): number {
    if (!row || typeof row !== 'object') return 0
    const rec = row as Record<string, unknown>
    const layoutKeys = resolveInlineFormFields(field).map(f => f.key).filter((k): k is string => typeof k === 'string' && k.length > 0)
    const keys =
      layoutKeys.length > 0
        ? layoutKeys
        : Object.keys(rec).filter(k => !k.startsWith('__'))
    let score = 0
    for (const k of keys) {
      const v = rec[k]
      if (v === undefined || v === null) continue
      if (typeof v === 'string' && v.trim() === '') continue
      score++
    }
    return score
  }

  function pickPreferredInlineRow(rows: any[], field: FormField): any | null {
    if (!rows.length) return null
    if (rows.length === 1) return rows[0]
    const useQualityScore =
      deps.suppressLinkFormInitialData()
      || (deps.effectiveReadonly.value && deps.previewSubTables())
    if (!useQualityScore) return rows[0]
    let best = rows[0]
    let bestScore = scoreInlineRowCompleteness(best, field) + scoreMiLinkChildRowQuality(best as Record<string, unknown>)
    for (let i = 1; i < rows.length; i++) {
      const r = rows[i]
      const s = scoreInlineRowCompleteness(r, field) + scoreMiLinkChildRowQuality(r as Record<string, unknown>)
      if (s > bestScore) {
        best = r
        bestScore = s
      }
    }
    return best
  }

  /**
   * Find the "current row" for inline form-below-table binding.
   *
   * For `subForm` source (own binding):
   *   1. If `currentMiRowId` is provided, prefer the matching row (handles MI sub-task).
   *   2. Else fall back to the single available row (普通单任务 single-row table).
   *
   * For `linkForm` source (target binding, e.g. subtable2):
   *   1. If `currentMiRowId` is provided, find the target row whose FK === parent rowId.
   *   2. Else if target has a single row, use it.
   *   3. Else `null` — host renders with empty defaults; first edit creates a new row.
   */
  function getCurrentRowForInlineForm(field: FormField): Record<string, any> | null {
    const pack = mergeRowsForInlineFormTarget(field)
    if (!pack) return null
    const { rows, isLinkTarget } = pack
    const parentId = deps.currentMiRowId()

    let result: Record<string, any> | null = null
    let pickReason = 'none'

    const miLinkIsolate =
      deps.suppressLinkFormInitialData()
      && parentId != null
      && String(parentId).trim() !== ''
      && isLinkTarget

    if (miLinkIsolate) {
      const isolated = resolveMiLinkIsolateInlineRow(
        rows,
        parentId,
        (list, pid) => findInlineRowIndexForMi(list as any[], pack, pid),
        list => pickPreferredInlineRow(list as any[], field),
      )
      if (isolated != null) {
        result = isolated as Record<string, any>
        pickReason =
          rows.length === 1
            ? 'mi-nested-only'
            : rows.length === 0
              ? 'mi-nested-empty'
              : 'mi-nested-pick'
      }
    } else if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
      const own = resolveBinding(field._bindingId)
      const parentRow = own
        ? findMiIsolatedParentRow(Array.isArray(own.data) ? own.data : [], parentId)
        : null
      if (parentRow) {
        const aligned = pickMiLinkChildRowsForParent(
          parentRow,
          rows,
          pack.target.primaryKeyFields ?? null
        )
        if (aligned.length > 0) {
          result = { ...(aligned[0] as Record<string, any>) }
          pickReason = 'mi-link-parent-align'
        }
      }
      if (!result) {
        const fkList = resolveLinkFkCandidates(pack.target)
        const match = rows.find(r => {
          if (!r || typeof r !== 'object') return false
          const rec = r as Record<string, unknown>
          return fkList.some(k => {
            const v = rec[k]
            return v != null && v !== '' && String(v) === String(parentId)
          })
        })
        if (match) {
          result = { ...(match as Record<string, any>) }
          pickReason = 'link-fk'
        } else if (!deps.suppressLinkFormInitialData()) {
          const pick = pickPreferredInlineRow(rows, field)
          result = pick ? { ...(pick as Record<string, any>) } : null
          pickReason = 'link-fallback-pick'
        }
      }
    } else if (parentId != null && String(parentId).trim() !== '') {
      // subForm path: MI element id often matches a *parent* FK on this row, not the child row PK (e.g. id=999).
      const idx = findInlineRowIndexForMi(rows, pack, parentId)
      if (idx >= 0) {
        result = { ...(rows[idx] as Record<string, any>) }
        pickReason = 'mi-idx'
      }
    }

    // Manual row selection (row click on the sub-table) — plain single-task path only;
    // MI / link-target paths keep their participant-scoped picking above.
    if (
      !result
      && !miLinkIsolate
      && !isLinkTarget
      && (parentId == null || String(parentId).trim() === '')
      && field._bindingId != null
    ) {
      const sel = inlineFormSelectedRows.value[Number(field._bindingId)]
      if (sel) {
        const hit = rows.find(r =>
          r && typeof r === 'object' && rowsMatchForSelection(r as Record<string, unknown>, sel, pack.target.primaryKeyFields ?? null),
        )
        if (hit) {
          result = { ...(hit as Record<string, any>) }
          pickReason = 'manual-select'
        }
      }
    }

    if (!result && !miLinkIsolate) {
      const pick = pickPreferredInlineRow(rows, field)
      result = pick ? { ...(pick as Record<string, any>) } : null
      pickReason = pickReason === 'none' ? 'pickPreferred' : `${pickReason}+pickPreferred`
    }

    if (result && isLinkTarget && parentId != null && String(parentId).trim() !== '') {
      const own = resolveBinding(field._bindingId)
      const parentRow = own
        ? findMiIsolatedParentRow(Array.isArray(own.data) ? own.data : [], parentId)
        : null
      const fkSeed =
        parentRow?.id_idw != null && parentRow.id_idw !== ''
          ? (parentRow.id_idw as string | number)
          : parentId
      result = seedMiLinkInlineRowFkFromParent(
        result,
        pack.target,
        fkSeed,
        isLinkTarget,
        own ?? undefined,
        parentRow,
      )
    }

    // Last-resort: nested stub may still lack id while another merged row carries the allocated PK.
    if (
      result
      && (result.id == null || String(result.id ?? '').trim() === '')
      && Array.isArray(pack.rows)
      && pack.rows.length > 1
    ) {
      const own = resolveBinding(field._bindingId)
      const parentRow =
        parentId != null && String(parentId).trim() !== '' && own
          ? findMiIsolatedParentRow(Array.isArray(own.data) ? own.data : [], parentId)
          : null
      const scopedRows =
        parentRow != null
          ? pack.rows.filter(
              r =>
                r &&
                typeof r === 'object' &&
                miParentRowAlignsWithChildRow(parentRow, r as Record<string, unknown>),
            )
          : parentId != null && String(parentId).trim() !== ''
            ? pack.rows.filter(
                r =>
                  r &&
                  typeof r === 'object' &&
                  miLinkChildRowBelongsToParticipant(r as Record<string, unknown>, parentId),
              )
            : pack.rows
      let best = result
      let bestScore = scoreMiLinkChildRowQuality(best as Record<string, unknown>)
      for (const r of scopedRows) {
        if (!r || typeof r !== 'object') continue
        const s = scoreMiLinkChildRowQuality(r as Record<string, unknown>)
        if (s > bestScore) {
          best = { ...best, ...(r as Record<string, any>) }
          bestScore = s
        }
      }
      result = best
    }

    return result
  }

  /**
   * When the inline form below is edited, merge the new values back into the matching
   * row in the EFFECTIVE source binding (own binding for `subForm`, link target for
   * `linkForm`) and emit `update:subTableData` so the host (tasks/detail or
   * applications/detail) can persist it via the existing data flow.
   *
   * When no matching child row exists in the link-target binding yet, a fresh row is
   * appended and the FK column is populated with `currentMiRowId` so persistence stays
   * within the existing dw_table_data → child-rows pipeline.
   */
  function handleInlineFormUpdate(field: FormField, mergedRow: Record<string, any>) {
    const pack = mergeRowsForInlineFormTarget(field)
    if (!pack) return
    const { target, rows, isLinkTarget } = pack
    const parentId = deps.currentMiRowId()

    const resolveLinkParentContext = () => {
      if (parentId == null || String(parentId).trim() === '') {
        return { fkSeed: parentId, own: null as SubTableBinding | null, parentRow: null as Record<string, unknown> | null }
      }
      const own = resolveBinding(field._bindingId)
      const parentRow = own
        ? findMiIsolatedParentRow(Array.isArray(own.data) ? own.data : [], parentId)
        : null
      const fkSeed =
        parentRow?.id_idw != null && parentRow.id_idw !== ''
          ? (parentRow.id_idw as string | number)
          : parentId
      return { fkSeed, own, parentRow }
    }

    let idx = -1
    if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
      const fkList = resolveLinkFkCandidates(target)
      idx = rows.findIndex(r => {
        if (!r || typeof r !== 'object') return false
        const rec = r as Record<string, unknown>
        return fkList.some(k => {
          const v = rec[k]
          return v != null && v !== '' && String(v) === String(parentId)
        })
      })
      if (idx < 0 && rows.length === 1) idx = 0
    } else if (isLinkTarget && rows.length === 1) {
      idx = 0
    } else if (parentId != null && String(parentId).trim() !== '') {
      idx = findInlineRowIndexForMi(rows, pack, parentId)
    } else if (rows.length === 1) {
      idx = 0
    }

    // Manual row selection: write back to the clicked row (matches getCurrentRowForInlineForm).
    if (
      idx < 0
      && !isLinkTarget
      && (parentId == null || String(parentId).trim() === '')
      && field._bindingId != null
    ) {
      const sel = inlineFormSelectedRows.value[Number(field._bindingId)]
      if (sel) {
        idx = rows.findIndex(r =>
          r && typeof r === 'object' && rowsMatchForSelection(r as Record<string, unknown>, sel, target.primaryKeyFields ?? null),
        )
      }
    }

    if (idx < 0 && rows.length > 0) {
      idx = 0
    }

    if (idx >= 0) {
      let updated: Record<string, any> = { ...rows[idx], ...mergedRow }
      if (isLinkTarget) {
        const { fkSeed, own, parentRow } = resolveLinkParentContext()
        const seeded = seedMiLinkInlineRowFkFromParent(
          updated,
          target,
          fkSeed,
          true,
          own ?? undefined,
          parentRow,
        )
        if (seeded) updated = seeded
      }
      rows[idx] = updated
    } else {
      const fresh: Record<string, any> = { ...mergedRow }
      if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
        const { fkSeed, own, parentRow } = resolveLinkParentContext()
        const explicit = (target as any).foreignKeyField
        const fkField = explicit && String(explicit).trim() ? String(explicit) : 'parent_id'
        if (fresh[fkField] == null || fresh[fkField] === '') fresh[fkField] = fkSeed ?? parentId
        const seeded = seedMiLinkInlineRowFkFromParent(
          fresh,
          target,
          fkSeed ?? parentId,
          true,
          own ?? undefined,
          parentRow,
        )
        if (seeded) Object.assign(fresh, seeded)
      }
      if (!isLinkTarget && parentId != null && String(parentId).trim() !== '') {
        const fkList = resolveLinkFkCandidates(target)
        for (const k of fkList) {
          if (fresh[k] == null || fresh[k] === '') {
            fresh[k] = parentId
            break
          }
        }
      }
      rows.push(fresh)
    }
    deps.handleSubTableUpdate(target.bindingId, rows)
  }

  function handleInlineFormSave() {
    deps.emitSave()
  }

  return {
    getCurrentRowForInlineForm,
    handleInlineFormUpdate,
    handleInlineFormSave,
    setInlineFormSelectedRow,
  }
}
