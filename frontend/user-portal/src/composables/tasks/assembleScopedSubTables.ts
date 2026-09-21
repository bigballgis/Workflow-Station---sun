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
import {
  projectSavedRowsForBinding,
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
