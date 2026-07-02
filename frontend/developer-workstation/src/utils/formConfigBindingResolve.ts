import type { TableBinding } from '@/api/functionUnit'

type BindingLike = Pick<TableBinding, 'id' | 'bindingType' | 'sortOrder'>

function parseBindingKey(key: string): number | null {
  const n = Number(key)
  return Number.isFinite(n) ? n : null
}

function sortBindings<T extends BindingLike>(bindings: T[], bindingType: string): T[] {
  return bindings
    .filter((b) => b.bindingType === bindingType)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0))
}

/**
 * Resolve binding-keyed configJson maps when keys still reference stale bindingIds
 * (export/seed) while live {@link TableBinding.id} values differ after import.
 */
export function resolveBindingKeyedEntry<T>(
  map: Record<string, T> | undefined,
  bindingId: number,
  bindings: BindingLike[],
  bindingType: TableBinding['bindingType'],
): T | undefined {
  if (!map) return undefined
  const direct = map[bindingId] ?? map[String(bindingId)]
  if (direct !== undefined) return direct

  const currentIds = new Set(
    bindings.map((b) => b.id).filter((id): id is number => id != null),
  )
  const orphanKeys = Object.keys(map)
    .filter((key) => {
      const parsed = parseBindingKey(key)
      return parsed == null || !currentIds.has(parsed)
    })
    .sort((a, b) => (parseBindingKey(a) ?? Number.MAX_SAFE_INTEGER) - (parseBindingKey(b) ?? Number.MAX_SAFE_INTEGER))

  if (!orphanKeys.length) return undefined

  const typedBindings = sortBindings(bindings, bindingType)
  const index = typedBindings.findIndex((b) => b.id === bindingId)
  if (index < 0 || index >= orphanKeys.length) return undefined
  return map[orphanKeys[index]]
}
