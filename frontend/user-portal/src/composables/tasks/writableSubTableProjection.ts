import { subTableStoreKey, type SubTableStoreBindingLike } from './subTableStore'

export interface WritableSubTableBinding extends SubTableStoreBindingLike {
  bindingId?: string | number
  bindingMode?: string
}

/** Render-only stores are not write intent. Keep explicit [] for delete-all. */
export function projectWritableSubTables<T>(
  tables: Record<string, T>, bindings: WritableSubTableBinding[],
): Record<string, T> {
  const projected: Record<string, T> = {}
  for (const binding of bindings) {
    if (binding.bindingMode === 'READONLY') continue
    const key = subTableStoreKey(binding)
    if (key && Object.prototype.hasOwnProperty.call(tables, key)) projected[key] = tables[key]
  }
  return projected
}
