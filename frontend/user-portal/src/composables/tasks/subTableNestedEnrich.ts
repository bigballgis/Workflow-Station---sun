/**
 * Enrich child binding rows from MI parent rows' nested {@code __subTables__} payloads
 * (thin top-level mirror rows vs full nested rows).
 */

import { mergeMiTaskStatusPreferTerminal } from './internal'
import { cloneSubTableRows } from './subTableCore'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import {
  isMiDashboardSubTableBinding,
  isSharedAttachmentFileBinding,
  isSubTableRowMetaField,
} from './subTableBindingKinds'
import { miParentRowAlignsWithChildRow } from './miLinkChildIdentity'
import { collapseSubTableRowsPreferFilled } from './miLinkChildRows'
import { pullNestedRowsForBindingFromParentRows } from './subTableNestedRows'

function buildBindingTableIdMapFromPeers<T extends { bindingId: number; tableId?: number | null }>(
  peers: T[],
): Map<number, number | null> {
  const m = new Map<number, number | null>()
  for (const b of peers) {
    const tid = b.tableId != null ? Number(b.tableId) : null
    if (tid != null && Number.isFinite(tid)) m.set(b.bindingId, tid)
  }
  return m
}

/**
 * Flow / MI often mirror a thin row at the top-level slice (name, assignee…) while lookup / id / custom fields
 * remain only under {@code parentRow.__subTables__[childBindingId|legacyKey]}. Fill missing fields on child rows.
 */
export function enrichChildBindingRowsFromParentsNestedSubTables<
  T extends {
    bindingId: number
    tableName?: string
    physicalTableName?: string
    tableId?: number | null
    data: any[]
    primaryKeyFields?: string[] | null | undefined
  },
>(bindings: T[]): void {
  const childAllowsMiMeta = new Map<number, boolean>()
  for (const child of bindings) {
    childAllowsMiMeta.set(child.bindingId, isMiDashboardSubTableBinding(child))
  }

  const mergePatchIntoRow = (
    target: Record<string, unknown>,
    patch: Record<string, unknown>,
    allowMiMeta: boolean,
  ): boolean => {
    let changed = false
    for (const [k, val] of Object.entries(patch)) {
      if (isSubTableRowMetaField(k)) {
        if (!allowMiMeta) continue
      }
      if (val === undefined || val === null || val === '') continue
      if (k === 'task_status' || k === 'task_current_node') {
        const cur = target[k]
        if (cur !== undefined && cur !== null && String(cur).trim() !== '') continue
        if (k === 'task_status') {
          const merged = mergeMiTaskStatusPreferTerminal(undefined, val)
          if (merged !== undefined) {
            target[k] = merged
            changed = true
          }
        } else {
          target[k] = val
          changed = true
        }
        continue
      }
      const cur = target[k]
      if (cur !== undefined && cur !== null && cur !== '') continue
      target[k] = val
      changed = true
    }
    return changed
  }

  const peerMap = buildBindingTableIdMapFromPeers(bindings)

  for (const child of bindings) {
    if (
      isSharedAttachmentFileBinding(
        child as {
          bindingId?: number
          tableId?: number | null
          tableName?: string
          physicalTableName?: string
          foreignKeyField?: string | null
          columns?: Array<{ field?: string }> | null
        },
      )
    ) {
      continue
    }
    if (!Array.isArray(child.data)) child.data = [] as any
    if (child.data.length === 0) {
      let incoming: any[] = []
      for (const parent of bindings) {
        if (parent.bindingId === child.bindingId) continue
        incoming.push(
          ...pullNestedRowsForBindingFromParentRows(
            {
              bindingId: child.bindingId,
              tableName: child.tableName ?? '',
              physicalTableName: child.physicalTableName,
              tableId: child.tableId ?? null,
            },
            Array.isArray(parent.data) ? parent.data : [],
            peerMap,
          ),
        )
      }
      if (incoming.length > 0) {
        child.data = cloneSubTableRows(
          mergeSubTableRowsByRowId([], incoming, child.primaryKeyFields ?? null),
        ) as any
      }
    }

    if (!Array.isArray(child.data) || child.data.length === 0) continue
    for (const parent of bindings) {
      if (parent.bindingId === child.bindingId || !Array.isArray(parent.data)) continue
      for (const parentRow of parent.data) {
        if (!parentRow || typeof parentRow !== 'object') continue
        const nested = pullNestedRowsForBindingFromParentRows(
          {
            bindingId: child.bindingId,
            tableName: child.tableName ?? '',
            physicalTableName: child.physicalTableName,
            tableId: child.tableId ?? null,
          },
          [parentRow],
          peerMap,
        )
        if (nested.length === 0) continue
        const collapsed = collapseSubTableRowsPreferFilled(nested)
        for (const patch of collapsed) {
          if (!patch || typeof patch !== 'object') continue
          for (let ci = 0; ci < child.data.length; ci++) {
            const childRow = child.data[ci]
            if (!childRow || typeof childRow !== 'object') continue
            if (
              !miParentRowAlignsWithChildRow(
                parentRow as Record<string, unknown>,
                childRow as Record<string, unknown>,
              )
            ) {
              continue
            }
            mergePatchIntoRow(
              child.data[ci] as Record<string, unknown>,
              patch as Record<string, unknown>,
              childAllowsMiMeta.get(child.bindingId) === true,
            )
          }
        }
      }
    }
  }
}
