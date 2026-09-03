/**
 * Hydrating binding rows from process variables when {@code __subTables__} keys use older /
 * sibling binding ids that share the same designer relation table.
 */

import { cloneSubTableRows } from './subTableCore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import { isSharedAttachmentFileBinding } from './subTableBindingKinds'
import type { MiKindFieldDef } from './miBindingKindFromConfig'
import { rowIsSelfOwnedByStructuralFk,
  miChildFkConfigOfBinding,
} from './miLinkChildIdentity'
import {
  assignRowsPerBindingForSharedMetadataTid,
  claimedNumericSubTableSliceKeys,
  inferOrphanRelationTableId,
  mergeRowsFromRichestUnclaimedNumericSlice,
  mergeRowsFromSoleUnclaimedNumericSlice,
  metadataRelationTableId,
} from './subTableSliceAssignment'

/**
 * Across BPMN steps the same RelationTable may keep variables under an older {@code bindingId} (e.g. initiator slice "66")
 * while the current Task Form uses a copied binding id ("90"). Keys in {@code __subTables__} are still keyed by the old id,
 * so hydrate empty bindings by matching {@code tableId} from designer {@code tableBindings} metadata across all FU forms.
 */
export function buildBindingIdToRelationTableIdMap(contentForms: any[] | undefined): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (const f of contentForms || []) {
    const tbs = (f as { tableBindings?: unknown }).tableBindings as unknown[] | undefined
    if (!Array.isArray(tbs)) continue
    for (const tb of tbs) {
      const raw = tb as { bindingId?: unknown; tableId?: unknown }
      if (raw?.bindingId == null) continue
      const bid = Number(raw.bindingId)
      if (!Number.isFinite(bid)) continue
      if (m.has(bid)) continue
      const tid = raw.tableId != null ? Number(raw.tableId) : null
      m.set(bid, tid != null && Number.isFinite(tid) ? tid : null)
    }
  }
  return m
}

export function hydrateBindingsRowsFromVariablesBySharedRelationTableId<
  T extends {
    bindingId: number
    tableId?: number | null
    data: any[]
    primaryKeyFields?: string[] | null | undefined
    // 共享附件分类要读这几项配置；漏在类型里会逼调用点窄化，把配置藏起来 → 分类恒 false。
    tableName?: string
    physicalTableName?: string
    foreignKeyField?: string | null
    bindingLinkMode?: string | null
    fieldDefinitions?: MiKindFieldDef[] | null
    columns?: Array<{ field?: string }> | null
  },
>(
  bindings: T[],
  savedSubTables: Record<string, unknown>,
  bindingTableById: Map<number, number | null>,
  options?: { excludeBinding?: (b: T) => boolean },
): void {
  const strictAssignmentByTid = new Map<number, Map<number, any[]>>()

  for (const b of bindings) {
    // Caller-controlled opt-out: e.g. MI participant-scoped bindings hydrate their own slice (filtered to the
    // current participant) elsewhere; pulling sibling same-tableId slices here would re-introduce other
    // participants' rows that collide by PK and overwrite the current participant's data.
    if (options?.excludeBinding?.(b)) continue
    // attachment (main_id / table 74): shared across bindings 103+104 — handled by applySharedAttachmentFinalizeAndMaterialize.
    if (isSharedAttachmentFileBinding(b)) {
      continue
    }

    /**
     * Same failure mode as {@link hydrateChildSubTablesFromParentsNestedRows}: “thin” rows loaded from a wrong
     * binding key still count as {@code length > 0}, so we skipped relation-table hydration and never merged the
     * rich slice keyed by the initiator/copied binding id.
     */
    const existing = Array.isArray(b.data) ? b.data : []

    const claimedKeys = claimedNumericSubTableSliceKeys(bindings, savedSubTables)

    let selfTidRaw = b.tableId != null ? Number(b.tableId) : bindingTableById.get(b.bindingId)
    if (selfTidRaw == null || Number.isNaN(selfTidRaw)) {
      const inferred = inferOrphanRelationTableId(b, bindings, bindingTableById, savedSubTables)
      if (inferred != null && Number.isFinite(inferred)) {
        selfTidRaw = inferred
      }
    }

    const metaTid = metadataRelationTableId(b, bindingTableById)
    const peersWithMetaTid =
      metaTid != null
        ? bindings.filter(bb => metadataRelationTableId(bb, bindingTableById) === metaTid).length
        : 0
    const multiPlacementSameTid =
      metaTid != null &&
      peersWithMetaTid > 1 &&
      selfTidRaw != null &&
      !Number.isNaN(selfTidRaw) &&
      metaTid === selfTidRaw

    const chunks: any[] = []
    if (selfTidRaw != null && !Number.isNaN(selfTidRaw)) {
      if (multiPlacementSameTid) {
        let mp = strictAssignmentByTid.get(selfTidRaw)
        if (!mp) {
          mp = assignRowsPerBindingForSharedMetadataTid(selfTidRaw, bindings, bindingTableById, savedSubTables)
          strictAssignmentByTid.set(selfTidRaw, mp)
        }
        const rowsForB = mp.get(b.bindingId)
        if (rowsForB && rowsForB.length > 0) {
          chunks.push(...rowsForB)
        }
      } else {
        for (const [key, val] of Object.entries(savedSubTables)) {
          if (!Array.isArray(val) || val.length === 0) continue
          const kid = Number(key)
          if (!Number.isFinite(kid) || kid === b.bindingId) continue
          const otherTid = bindingTableById.get(kid)
          if (otherTid == null || Number.isNaN(Number(otherTid))) continue
          if (Number(otherTid) !== selfTidRaw) continue
          chunks.push(...val)
        }
      }
    }

    if (chunks.length === 0) {
      if (!multiPlacementSameTid) {
        chunks.push(
          ...mergeRowsFromSoleUnclaimedNumericSlice(
            b,
            savedSubTables,
            claimedKeys,
            bindingTableById,
            selfTidRaw != null && !Number.isNaN(Number(selfTidRaw)) ? Number(selfTidRaw) : null,
          ),
        )
      }
    }

    if (chunks.length === 0 && existing.length === 0) {
      if (!multiPlacementSameTid) {
        chunks.push(
          ...mergeRowsFromRichestUnclaimedNumericSlice(
            b,
            savedSubTables,
            claimedKeys,
            bindingTableById,
            selfTidRaw != null && !Number.isNaN(Number(selfTidRaw)) ? Number(selfTidRaw) : null,
          ),
        )
      }
    }

    if (chunks.length === 0) continue
    /**
     * `chunks` is gathered from every OTHER binding sharing this table_id (line ~119-127) — for a
     * table read by several MI form bindings (Assign Task / Sub task / Main), those are snapshot
     * copies unless a chunk row carries a structural self-reference FK (sub_task_id / … === its own
     * PK), which only the binding that actually owns the row's writes ever stamps. Merge order is
     * "later wins for non-empty fields", so: non-self-owned chunk rows merge in BEFORE `existing`
     * (existing wins, preserving this function's original gap-filling behavior for thin/empty
     * existing data); self-owned chunk rows merge in AFTER `existing` (they win, since they reflect
     * the row's real current value even when `existing` already has non-empty fields for it).
     */
    const selfOwnedChunkRows = chunks.filter(
      r => r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r as Record<string, unknown>, b.primaryKeyFields ?? null, miChildFkConfigOfBinding(b as never)),
    )
    const restChunkRows = chunks.filter(
      r => !(r && typeof r === 'object' && rowIsSelfOwnedByStructuralFk(r as Record<string, unknown>, b.primaryKeyFields ?? null, miChildFkConfigOfBinding(b as never))),
    )
    let mergedRows = mergeSubTableRowsByRowId(restChunkRows, existing, b.primaryKeyFields ?? null)
    if (selfOwnedChunkRows.length > 0) {
      mergedRows = mergeSubTableRowsByRowId(mergedRows, selfOwnedChunkRows, b.primaryKeyFields ?? null)
    }
    b.data = cloneSubTableRows(mergedRows)
  }
}
