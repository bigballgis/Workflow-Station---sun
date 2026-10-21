/** Table identity is not binding scope: two bindings can filter one table through different FKs. */
export interface LinkFormBindingIdentity {
  bindingId?: number | null
  tableId?: number | null
  tableName?: string
  designerTableName?: string
  filterFkFieldName?: string | null
  filterFkRefTableId?: number | null
}

export function isSameLinkFormTable(target: LinkFormBindingIdentity, source: LinkFormBindingIdentity): boolean {
  if (target.tableId != null && source.tableId != null) return Number(target.tableId) === Number(source.tableId)
  const targetName = target.designerTableName || target.tableName
  const sourceName = source.designerTableName || source.tableName
  return !!targetName && !!sourceName && targetName.trim().toLowerCase() === sourceName.trim().toLowerCase()
}

export function isSameLinkFormScope(target: LinkFormBindingIdentity, source: LinkFormBindingIdentity): boolean {
  if (target.bindingId != null && source.bindingId != null && Number(target.bindingId) === Number(source.bindingId)) return true
  if (!isSameLinkFormTable(target, source)) return false
  // No filter on either binding denotes the same unfiltered table scope. An explicit
  // filter on only one side is NOT equivalent to that scope, including copied MI forms.
  if ((target.filterFkFieldName || null) !== (source.filterFkFieldName || null)) return false
  return (target.filterFkRefTableId ?? null) === (source.filterFkRefTableId ?? null)
}
