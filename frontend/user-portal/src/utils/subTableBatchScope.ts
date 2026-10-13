import { parseSubTableBatchCapability } from './subTableBatchCapability'
import type { SubTableBatchChange } from './subTableBatchChanges'
import { rowKeyForScope } from '@/composables/tasks/subTableCanonicalStamp'

export function batchScopeIdentity(row: Record<string, unknown>, primaryKeyFields?: string[] | null): string | null {
  const key = rowKeyForScope(row, primaryKeyFields)
  return key ? JSON.stringify(Object.entries(key).map(([field, value]) => [field, String(value)])
    .sort(([left], [right]) => left!.localeCompare(right!))) : null
}

/** Freeze transport identity/FK semantics only for bindings carrying membership changes. */
export function batchMembershipBindings<T extends { bindingId?: number | string }>(
  bindings: T[], changes: SubTableBatchChange[], capabilities?: Record<string, unknown>,
): T[] {
  return bindings.map(binding => {
    const claims = changes.filter(change => change.bindingId === String(binding.bindingId)
      && (change.operation === 'ADD' || change.operation === 'DELETE'))
    if (!claims.length) return binding
    const designs = claims.map(change => parseSubTableBatchCapability(capabilities?.[change.batchControlId])?.rowDesign?.binding)
    if (designs.some(design => !design) || new Set(designs.map(design => JSON.stringify(design))).size !== 1) {
      throw new Error('Batch binding scope has no unambiguous frozen metadata')
    }
    const design = designs[0]!
    return { ...binding, ...design, bindingId: binding.bindingId, designerTableName: design.tableName }
  })
}
