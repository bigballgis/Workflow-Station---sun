/**
 * Row filters for process-level shared sub-tables (attachment.main_id etc.):
 * drop MI participant / foreign binding rows that leaked into shared bindings and finalize rows.
 */

import { pickNonEmptyAttachmentFile } from './internal'
import {
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
  isSharedAttachmentFileBinding,
  isSubTableMiDashboardRow,
  isSubTableRowMetaField,
  stripSubTableRowMetaFields,
} from './subTableBindingKinds'
import {
  miChildFkConfigOfBinding,
  resolveMiChildStructuralParentFk,
  type MiChildFkConfig,
} from './miLinkChildIdentity'
import { dropSubsumedSubTableRows } from './subTableRowNormalize'
import { resolveMiKindContext } from './miBindingKindFromConfig'

/**
 * 这一行在**本表自己的列**上有没有真实数据（主键列不算数据）。
 *
 * <p>主键列名从设计器配置来（`primaryKeyFields`，其次是 `fieldDefinitions` 里 isPrimaryKey 的字段），
 * 字面量 `id` 只作为最后的保底。曾经这里写死 `f !== 'id'`：主键改名成 `idfa` 之后，主键值被当成
 * 「本表有数据」，于是只有一个 UUID 的幽灵行重新漏进附件表格 —— 正是 #ghost-row 当初修掉的那个 bug。
 */
function sharedBindingRowHasNonIdColumnData(
  rec: Record<string, unknown>,
  colFields: Set<string>,
  pkFields?: readonly string[] | null,
): boolean {
  const pkSet = new Set(
    [...(pkFields ?? []), 'id']
      .map(f => String(f ?? '').trim().toLowerCase())
      .filter(Boolean),
  )
  const isPk = (f: string) => pkSet.has(String(f).trim().toLowerCase())
  const fields =
    colFields.size > 0
      ? [...colFields].filter(f => !isPk(f))
      : Object.keys(rec).filter(k => !isPk(k) && !isSubTableRowMetaField(k))
  if (fields.length === 0) return false
  return fields.some(f => {
    const v = rec[f]
    return v != null && v !== '' && !(typeof v === 'string' && v.trim() === '')
  })
}

function isLeakedForeignRowOnSharedAttachment(
  rec: Record<string, unknown>,
  colFields: Set<string>,
  fkConfig?: MiChildFkConfig | null,
  pkFields?: readonly string[] | null,
): boolean {
  if (isSubTableMiDashboardRow(rec)) return true

  // Genuine attachment rows are never foreign leaks — even when the same UUID was
  // incorrectly duplicated into a subtable slice and registered as "foreign".
  if (pickNonEmptyAttachmentFile(rec)) {
    if (rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
      return true
    }
    return false
  }

  /**
   * A structural FK to an MI participant ({@code sub_task_id} etc.) marks the row as a link-child of
   * some other table — an attachment row is keyed to the main record, never to a participant. This
   * 结构外键列名来自设计器配置，改名后依然有效。
   *
   * <p>（历史：这里曾配合一张「外来行 id 注册表」使用。那张表只遍历「键名看着像参与者表」的切片，
   * 而切片键早已规范化成 {@code dw:<name>}，因此它对任何 FU 都返回空集 —— 已随两个死函数一并删除。）
   */
  if (resolveMiChildStructuralParentFk(rec, fkConfig)) return true

  /**
   * An "attachment" row with no file AND no value in any of its own non-id columns carries nothing
   * this table can display — the grid renders it as a row of "-". Whatever produced it (a foreign
   * row projected down to its id, a stale placeholder), it is not an attachment.
   */
  if (!sharedBindingRowHasNonIdColumnData(rec, colFields, pkFields)) return true

  // 后端 MI overlay 的 id 信封归一化（MiOverlaySupport.normalizeVariableRowPkEnvelope）会在
  // 持久化的附件行上补写 id_idw —— 这是**后端确定写入的键名**，不是这里在猜某张表的主键，
  // 所以按字面量匹配是对的。该行不是子表泄漏行。
  if (rec.id_idw != null && String(rec.id_idw).trim() !== '' && !colFields.has('id_idw')) {
    return true
  }

  if (rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
    return true
  }

  return false
}

/** Drop attachment-shaped rows (id + file only) that leaked into an MI / subtable binding grid. */
export function filterRowsForMiParticipantSubTableBinding(
  rows: any[] | undefined | null,
  binding: {
    columns?: Array<{ field?: string }> | null
    tableName?: string
  },
): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  const colFields = new Set(
    (binding.columns ?? [])
      .map(c => (c?.field != null ? String(c.field).trim() : ''))
      .filter(Boolean),
  )
  return rows
    .filter(row => {
      if (!row || typeof row !== 'object') return false
      const rec = row as Record<string, unknown>
      if (colFields.has('file') && pickNonEmptyAttachmentFile(row)) {
        if (isSubTableMiDashboardRow(rec)) return true
        if (rec.sub_task_id != null && String(rec.sub_task_id).trim() !== '') return true
        if (rec.id_idw != null && String(rec.id_idw).trim() !== '') return true
        if (rec.name != null && String(rec.name).trim() !== '') return true
        return false
      }
      if (!pickNonEmptyAttachmentFile(row)) return true
      if (isSubTableMiDashboardRow(rec)) return true
      const name = rec.name
      if (name != null && String(name).trim() !== '') return true
      if (rec.id_idw != null && String(rec.id_idw).trim() !== '') return true
      return false
    })
}

/**
 * Drop MI participant / foreign binding rows that leaked into a process-level shared sub-table (e.g. attachment.main_id).
 */
export function filterRowsForSharedProcessSubTableBinding(
  rows: any[] | undefined | null,
  binding: {
    columns?: Array<{ field?: string }> | null
    foreignKeyField?: string | null
    bindingLinkMode?: string | null
    primaryKeyFields?: string[] | null
    fieldDefinitions?: MiChildFkConfig['fieldDefinitions']
    tableName?: string
    designerTableName?: string
    tableId?: number | null
  },
): any[] {
  if (!Array.isArray(rows) || rows.length === 0) return []
  if (isMiParticipantScopedSubTableBinding(binding)) {
    return filterRowsForMiParticipantSubTableBinding(rows, binding)
  }

  const colFields = new Set(
    (binding.columns ?? [])
      .map(c => (c?.field != null ? String(c.field).trim() : ''))
      .filter(Boolean),
  )
  const attachmentBinding = isSharedAttachmentFileBinding(binding)
  /**
   * 「这一行带着指向 **MI 参与者** 的结构外键」这条判据，必须拿到 collection 的 tableId 才成立。
   *
   * <p>不传 collection 时 {@code resolveMiChildStructuralFkColumns} 会把该表的**每一个**设计器
   * 外键都当成「指向参与者」—— 共享附件表指向主表的外键（现场 {@code main_idvab → main}）于是
   * 被误判成 link-child 标记，整行被当泄漏行丢掉：实测 assignment 任务加的附件已经写进库，
   * 刷新却渲染 0 行。
   *
   * <p>解析不到 collection（非 MI 的 FU、或注册表尚未写入）时给 {@code null}，
   * 下面据此**跳过**这条判据 —— 没有参与者概念的流程里，本就不存在「指向参与者的外键」。
   */
  const miCollectionTableId = resolveMiKindContext().miCollectionTableId ?? null
  const fkConfig =
    miCollectionTableId != null ? miChildFkConfigOfBinding(binding as never, miCollectionTableId) : null
  const pkFields =
    binding.primaryKeyFields
    ?? (binding.fieldDefinitions ?? [])
      .filter(f => (f as { isPrimaryKey?: boolean })?.isPrimaryKey)
      .map(f => String(f?.fieldName ?? '').trim())
      .filter(Boolean)

  return rows.filter(row => {
    if (!row || typeof row !== 'object') return false
    const rec = row as Record<string, unknown>

    if (attachmentBinding
        && isLeakedForeignRowOnSharedAttachment(rec, colFields, fkConfig, pkFields)) {
      return false
    }

    if (!attachmentBinding && isSubTableMiDashboardRow(rec)) return false

    // Rows carrying real data for this binding's own columns are ALWAYS kept. The id_idw /
    // name "foreign row" heuristics below only apply to rows WITHOUT own column data —
    // id_idw is the DW default sub-table PK, so a data-bearing row whose list view simply
    // doesn't display the PK must never be treated as an MI leak (it emptied whole tables).
    const hasOwnData = sharedBindingRowHasNonIdColumnData(rec, colFields)
    if (hasOwnData) return true

    if (isSubTableMiDashboardRow(rec)) return false

    if (rec.id_idw != null && String(rec.id_idw).trim() !== '' && !colFields.has('id_idw')) {
      return false
    }

    if (rec.name != null && String(rec.name).trim() !== '' && !colFields.has('name')) {
      return false
    }

    return true
  })
}

/** Strip meta, drop foreign MI rows, and collapse id-only ghosts for shared process sub-tables. */
export function finalizeSharedProcessSubTableBindingRows(
  rows: any[] | undefined | null,
  binding: {
    columns?: Array<{ field?: string }> | null
    foreignKeyField?: string | null
    bindingLinkMode?: string | null
    primaryKeyFields?: string[] | null
    fieldDefinitions?: MiChildFkConfig['fieldDefinitions']
    tableName?: string
    designerTableName?: string
    tableId?: number | null
  },
): any[] {
  const preserveMiFields = isMiDashboardSubTableBinding(binding)
  const cleaned = filterRowsForSharedProcessSubTableBinding(rows, binding).map(row => {
    if (!row || typeof row !== 'object') return row
    return preserveMiFields
      ? row
      : stripSubTableRowMetaFields(row as Record<string, unknown>)
  })
  return dropSubsumedSubTableRows(cleaned)
}
