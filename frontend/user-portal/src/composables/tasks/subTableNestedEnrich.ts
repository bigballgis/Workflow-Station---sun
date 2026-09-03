/**
 * Enrich child binding rows from MI parent rows' nested {@code __subTables__} payloads
 * (thin top-level mirror rows vs full nested rows).
 */

import { mergeMiTaskStatusPreferTerminal } from './internal'
import { cloneSubTableRows } from './subTableCore'
import { getActiveMiFieldNames } from './useMiConfig'
import { mergeSubTableRowsByRowId } from './subTableRowMerge'
import {
  isMiDashboardSubTableBinding,
  isSharedAttachmentFileBinding,
  isSubTableRowMetaField,
} from './subTableBindingKinds'
import { miChildFkConfigOfBinding, miParentRowAlignsWithChildRow } from './miLinkChildIdentity'
import type { MiChildFkConfig } from './miLinkChildIdentity'
import { collapseSubTableRowsPreferFilled } from './miLinkChildRows'
import { bindingDeclaresMiParticipantRow } from './miBindingKindFromConfig'
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
    // 归属判定要读的设计器配置：FK 列来自 fieldDefinitions，collection 由 bindingLinkMode 声明。
    // 所有调用点传的都是完整 binding，这里只是把类型放开，无需改调用方。
    foreignKeyField?: string | null
    bindingLinkMode?: string | null
    fieldDefinitions?: MiChildFkConfig['fieldDefinitions']
  },
>(bindings: T[]): void {
  const childAllowsMiMeta = new Map<number, boolean>()
  for (const child of bindings) {
    childAllowsMiMeta.set(child.bindingId, isMiDashboardSubTableBinding(child))
  }

  // MI collection 由设计器**显式声明**（Link Mode = MI Participant Row）认出来，不猜表名。
  // 它的 primaryKeyFields 就是「参与者标识存在宿主行的哪一列」—— 本 demo 是 id_idwxwc。
  const collectionBinding = bindings.find(b => bindingDeclaresMiParticipantRow(b))
  const collectionPk = collectionBinding?.primaryKeyFields ?? null
  const collectionTableId =
    collectionBinding?.tableId != null && Number.isFinite(Number(collectionBinding.tableId))
      ? Number(collectionBinding.tableId)
      : null

  // MI 镜像列名来自 Sub-Task Config，不写死；在循环外解析一次
  const { statusField, currentNodeField } = getActiveMiFieldNames()

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
      if (k === statusField || k === currentNodeField) {
        const cur = target[k]
        if (cur !== undefined && cur !== null && String(cur).trim() !== '') continue
        if (k === statusField) {
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
    // 不要窄化 binding：判据要读 fieldDefinitions（data_type='FILE' / 字段级 FK），
    // 窄化掉这些字段会让分类恒为 false —— 共享附件表于是漏进本该跳过它的这段逻辑。
    if (isSharedAttachmentFileBinding(child)) {
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
    // 子行的结构外键列同样读设计器配置（fieldDefinitions.isForeignKey + refTableId）。
    // 不传配置时 resolveMiChildStructuralParentFk 恒返回 null，归属判定会对**自己的行和
    // 别人的行一律答 false** —— 于是这段 enrich 整体空转，宿主行的嵌套字段永远补不进来。
    const childFkConfig = miChildFkConfigOfBinding(child, collectionTableId)
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
                childFkConfig,
                collectionPk,
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
