/**
 * Core sub-table helpers: table-name normalization, binding identity matching, row cloning.
 * Pure helpers — no reactive state, no Vue/API dependencies.
 */

export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

/** Strip designer "ADD + …" prefix; nested {@code parentRow.__subTables__} keys often keep this label. */
export function stripLinkFormDesignerTableLabel(raw?: string): string {
  return String(raw || '').trim().replace(/^ADD\s*\+\s*/i, '').trim()
}

export function subTableBindingMatches(
  target: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null },
  source: { bindingId: number; tableName: string; physicalTableName?: string; tableId?: number | null }
): boolean {
  const targetPhysicalName = normalizeSubTableName(target.physicalTableName)
  const sourcePhysicalName = normalizeSubTableName(source.physicalTableName)
  if (targetPhysicalName && sourcePhysicalName && targetPhysicalName === sourcePhysicalName) return true
  const targetName = normalizeSubTableName(target.tableName)
  const sourceName = normalizeSubTableName(source.tableName)
  const samePhysicalTable = target.tableId != null && source.tableId != null && Number(target.tableId) === Number(source.tableId)
  return target.bindingId === source.bindingId || samePhysicalTable || (!!targetName && targetName === sourceName)
}

export function cloneSubTableRows(rows: any[]): any[] {
  try {
    return JSON.parse(JSON.stringify(rows))
  } catch {
    return rows.map(row => ({ ...row }))
  }
}

/** Strip nested {@code __subTables__} from each row to prevent Vue deep-reactivity freeze
 *  and circular traversal when rows reference each other across sub-table slices. */
export function stripNestedSubTablesFromRows(rows: any[]): any[] {
  for (const row of rows) {
    if (row && typeof row === 'object' && row.__subTables__) {
      delete row.__subTables__
    }
  }
  return rows
}

export function cloneSubTableBindings<T extends Array<{ data: any[] }>>(bindings: T): T {
  return bindings.map(binding => ({
    ...binding,
    data: cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
  })) as T
}
