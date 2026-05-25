/** Resolve a SUB/RELATED binding id to a human-readable table label. */
export type BindingDisplayLookup = {
  bindingId: number
  tableName?: string
  tableDisplayName?: string
  tableId?: number
}

export function normalizeBindingId(raw: unknown): number | null {
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}

export function resolveBindingDisplayName(
  bindingId: unknown,
  bindings: BindingDisplayLookup[],
  tableResolver?: (tableId: number) => string | undefined
): string {
  const id = normalizeBindingId(bindingId)
  if (id == null) return ''
  const b = bindings.find(x => x.bindingId === id)
  if (b?.tableDisplayName) return b.tableDisplayName
  if (b?.tableName) return b.tableName
  if (b?.tableId != null && tableResolver) {
    const resolved = tableResolver(b.tableId)
    if (resolved) return resolved
  }
  return ''
}
