/** Resolve a SUB/RELATED binding id to a human-readable table label. */
export type BindingDisplayLookup = {
  bindingId: number
  tableName?: string
  tableDisplayName?: string
  tableId?: number
}

/** Fields needed to tell two bindings of the same table apart in designer selects. */
export type SubTableBindingLabelInput = {
  tableName: string
  tableDisplayName?: string
  tableDescription?: string
  foreignKeyField?: string | null
  bindingLinkMode?: string | null
}

/**
 * Designer option / placeholder label. Same-table dual bindings share display name and
 * table description, so the filter FK (or MI link mode) must appear in the label.
 */
export function formatSubTableBindingOptionLabel(binding: SubTableBindingLabelInput): string {
  const name = (binding.tableDisplayName || binding.tableName || '').trim()
  const fk = typeof binding.foreignKeyField === 'string' ? binding.foreignKeyField.trim() : ''
  if (fk) return `${name} (${fk})`
  if (binding.bindingLinkMode === 'miParticipantRow') return `${name} (MI)`
  const desc = typeof binding.tableDescription === 'string' ? binding.tableDescription.trim() : ''
  if (desc) return `${name} (${desc})`
  return name
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
