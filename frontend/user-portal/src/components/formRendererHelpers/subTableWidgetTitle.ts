/**
 * A form can place the same physical table more than once. The canvas label is
 * therefore the binding-specific title; the table display name is only a legacy fallback.
 */
export function resolveSubTableWidgetTitle(
  field: { label?: string | null } | null | undefined,
  binding: {
    bindingId?: number | null
    tableId?: number | null
    tableDisplayName?: string | null
    tableName?: string | null
    designerTableName?: string | null
    filterFkFieldName?: string | null
  } | null | undefined,
  bindingPool?: unknown,
): string {
  const widgetLabel = String(field?.label ?? '').trim()
  const tableDisplayName = String(binding?.tableDisplayName ?? '').trim()
  const baseTitle = widgetLabel || tableDisplayName || String(binding?.tableName ?? '').trim()

  const poolValue = bindingPool && typeof bindingPool === 'object' && 'value' in bindingPool
    ? (bindingPool as { value?: unknown }).value
    : bindingPool
  const peers = Array.isArray(poolValue) ? poolValue as Array<Record<string, unknown>> : []
  const tableId = binding?.tableId == null ? null : Number(binding.tableId)
  const tableKey = String(binding?.designerTableName ?? binding?.tableName ?? '').trim().toLowerCase()
  const sameTableCount = peers.filter(peer => {
    if (tableId != null && Number.isFinite(tableId) && peer.tableId != null) {
      return Number(peer.tableId) === tableId
    }
    return tableKey !== ''
      && String(peer.designerTableName ?? peer.tableName ?? '').trim().toLowerCase() === tableKey
  }).length
  const filterFk = String(binding?.filterFkFieldName ?? '').trim()
  if (sameTableCount > 1 && filterFk && !baseTitle.toLowerCase().includes(filterFk.toLowerCase())) {
    return `${baseTitle} (${filterFk})`
  }
  return baseTitle
}
