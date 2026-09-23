import { readSubTableRows, subTableStoreKey } from '@/composables/tasks/subTableStore'
import {
  flattenNestedSubTableRowsIntoPayload,
  flattenSliceMapsFromBindings,
  normalizeSubTableRowsForBinding,
} from '@/composables/tasks/shared'
import {
  buildBindingScopeFromBaseline,
  stampCanonicalStoreRows,
  storeKeysSharedByMultipleBindings,
  type CanonicalStampBinding,
  type SubTableBindingScope,
} from '@/composables/tasks/subTableCanonicalStamp'
import { stampAuthoritativeRowVersions } from '@/composables/tasks/subTableRowVersionSync'
import {
  projectSavedRowsForBinding,
  removeRowsWithMissingDeclaredParent,
  shouldProjectByFilter,
  type FilterProjectionBinding,
  type FilterProjectionFormContext,
} from '@/composables/tasks/subTableFilterProjection'

export interface ScopedSubTablesSubmit {
  subTables: Record<string, unknown>
  emptiedSubTableKeys: string[]
  subTableBindingScopes: SubTableBindingScope[]
}

export type ScopedSubTableBinding = CanonicalStampBinding & FilterProjectionBinding

/** Apply parent-row deletion to a table-keyed canonical store before it is submitted. */
export function removeRowsWithMissingParentsFromCanonicalStore(
  subTables: Record<string, unknown>,
  bindings: readonly ScopedSubTableBinding[],
  form: FilterProjectionFormContext = {},
  sharedKeys: ReadonlySet<string> = storeKeysSharedByMultipleBindings(bindings),
): void {
  for (const binding of bindings) {
    const storeKey = subTableStoreKey(binding)
    if (!storeKey || !sharedKeys.has(storeKey) || !shouldProjectByFilter(binding, bindings)) continue
    const rows = readSubTableRows(subTables, binding) ?? []
    subTables[storeKey] = removeRowsWithMissingDeclaredParent(rows, binding, bindings, form)
  }
}

/**
 * Table-keyed {@code __subTables__} plus per-binding write claims.
 * Shared by process start and Return_To_Requester Process Form submit.
 */
export function assembleScopedSubTablesSubmit(
  bindings: readonly ScopedSubTableBinding[],
  form: FilterProjectionFormContext = {},
  baseline: Record<string, unknown> = {},
): ScopedSubTablesSubmit {
  const list: ScopedSubTableBinding[] = [...bindings]
  const subTables: Record<string, unknown> = {}
  const { primaryKeyFieldsBySliceKey, parentLink } = flattenSliceMapsFromBindings(list)
  const sharedKeys = storeKeysSharedByMultipleBindings(list)
  const stamped: Record<string, Array<Record<string, unknown>>> = {}
  for (const binding of list) {
    const rows = normalizeSubTableRowsForBinding(Array.isArray(binding.data) ? binding.data : [])
    stampCanonicalStoreRows(subTables, stamped, binding, rows, sharedKeys)
  }
  flattenNestedSubTableRowsIntoPayload(subTables, 8, primaryKeyFieldsBySliceKey, parentLink)
  stampAuthoritativeRowVersions(subTables, baseline, list)
  // A parent deletion immediately hides its children from the projected widget. Remove those
  // now-invisible orphans from the canonical submit store as well; otherwise the backend sees a
  // changed row that no surviving binding scope can legitimately claim.
  removeRowsWithMissingParentsFromCanonicalStore(subTables, list, form, sharedKeys)
  const emptiedSubTableKeys: string[] = []
  const subTableBindingScopes: SubTableBindingScope[] = []
  for (const binding of list) {
    const storeKey = subTableStoreKey(binding)
    if (!storeKey || !sharedKeys.has(storeKey)) continue
    if (!shouldProjectByFilter(binding, list)) continue
    const rows = readSubTableRows(subTables, binding) ?? []
    const scoped = projectSavedRowsForBinding(rows, binding, list, form) ?? rows
    const scope = buildBindingScopeFromBaseline(binding, scoped, false, baseline, list, form)
    if (scope) subTableBindingScopes.push(scope)
  }
  return { subTables, emptiedSubTableKeys, subTableBindingScopes }
}

/** PUT /processes/{id}/form body: form fields plus transport metadata (stripped server-side). */
export function buildProcessFormUpdateBody(
  values: Record<string, unknown>,
  bindings: readonly ScopedSubTableBinding[],
  form: FilterProjectionFormContext = {},
  baseline: Record<string, unknown> = {},
): Record<string, unknown> {
  const assembled = assembleScopedSubTablesSubmit(bindings, form, baseline)
  return {
    ...values,
    __subTables__: assembled.subTables,
    emptiedSubTableKeys: assembled.emptiedSubTableKeys,
    subTableBindingScopes: assembled.subTableBindingScopes,
  }
}
