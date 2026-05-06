/**
 * Shared utility functions for task detail composables.
 * Pure helpers — no reactive state, no Vue/API dependencies.
 */

export function normalizeSubTableName(name?: string): string {
  return String(name || '').trim().toLowerCase()
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

export function cloneSubTableBindings<T extends Array<{ data: any[] }>>(bindings: T): T {
  return bindings.map(binding => ({
    ...binding,
    data: cloneSubTableRows(Array.isArray(binding.data) ? binding.data : [])
  })) as T
}

export function mergeSubTableRowsByRowId(existing: any[] | undefined, incoming: any[]): any[] {
  const byId = new Map<string, any>()
  const add = (r: any) => {
    if (!r || typeof r !== 'object') return
    const rawId = (r as Record<string, unknown>).id ?? (r as Record<string, unknown>).rowId
    if (rawId == null || String(rawId).trim() === '') return
    const k = String(rawId)
    const cur = byId.get(k)
    byId.set(k, cur ? { ...cur, ...r } : { ...r })
  }
  for (const r of existing || []) add(r)
  for (const r of incoming || []) add(r)
  return Array.from(byId.values())
}

export function getSavedSubTableRows(subTables: Record<string, any>, binding: {
  bindingId: number
  tableName: string
  physicalTableName?: string
  tableId?: number | null
}): any[] | undefined {
  const key = String(binding.bindingId)
  const byId = (subTables[key] as any[] | undefined) || (subTables[String(binding.bindingId)] as any[] | undefined)
  if (Array.isArray(byId)) return byId as any[]
  if (binding.tableName && Array.isArray(subTables[binding.tableName])) return subTables[binding.tableName] as any[]
  return undefined
}
