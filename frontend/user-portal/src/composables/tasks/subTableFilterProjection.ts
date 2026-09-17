/**
 * Dual-binding read projection: same table, different declared filter FKs.
 *
 * Storage and `binding.data` stay the canonical table store (`dw:<name>`).
 * Widgets display a slice by declared filter FK: MAIN → primary-record PK;
 * nested SUB → `_currentItem` or sibling rows of the referenced table.
 * Intersection, not exclusive partition: a row with both FKs appears in both
 * widgets. Widget edits merge the slice back into the canonical array.
 *
 * V1 is unchanged unless the form actually has two SUB bindings on the same
 * table with different `filterFkRefTableId`. Missing parent → empty list, not
 * the full store.
 */

import { declaredFilterFkFields } from './miBindingKindFromConfig'
import { resolveMiChildPrimaryKeyColumns } from './miLinkChildIdentity'

export interface FilterProjectionBinding {
  bindingId?: number | string
  tableId?: number | null
  bindingType?: string | null
  primaryKeyFields?: string[] | null
  fieldDefinitions?: Array<{ fieldName?: string; isPrimaryKey?: boolean }> | null
  filterFkRefTableId?: number | null
  filterFkFieldName?: string | null
  data?: unknown[]
}

export interface FilterProjectionFormContext {
  formData?: Record<string, unknown> | null
  primaryTableId?: number | null
  primaryPkFields?: string[] | null
  primaryFieldDefinitions?: Array<{ fieldName?: string; isPrimaryKey?: boolean }> | null
}

export function shouldProjectByFilter(
  binding: FilterProjectionBinding,
  siblings: readonly FilterProjectionBinding[],
): boolean {
  const mine = declaredFilterFkFields(binding)
  if (mine.filterFkFieldName == null || mine.filterFkRefTableId == null) return false
  const tid = Number(binding.tableId)
  if (!Number.isFinite(tid)) return false
  const refs = new Set<number>()
  for (const sibling of siblings) {
    if (!isProjectableSibling(sibling, tid)) continue
    const ref = declaredFilterFkFields(sibling).filterFkRefTableId
    if (ref != null) refs.add(ref)
  }
  return refs.size > 1
}

export function projectSavedRowsForBinding<T>(
  rows: T[] | undefined,
  binding: FilterProjectionBinding,
  siblings: readonly FilterProjectionBinding[],
  form: FilterProjectionFormContext,
): T[] | undefined {
  if (!rows) return rows
  if (!shouldProjectByFilter(binding, siblings)) return rows
  const rowsByTableId = snapshotRowsByTableId(siblings)
  const parentValues = resolveFilterParentValues(binding, form, rowsByTableId, siblings)
  if (parentValues == null) return rows
  return sliceRowsByDeclaredFilter(rows, binding, parentValues) as T[]
}

/**
 * Replace the rows this widget owns (matching its declared parent) and keep
 * every other canonical row, including rows that belong to a sibling widget.
 */
export function mergeFilterSliceIntoCanonical<T>(
  canonical: readonly T[],
  slice: readonly T[],
  binding: FilterProjectionBinding,
  parentValues: readonly unknown[],
): T[] {
  const field = declaredFilterFkFields(binding).filterFkFieldName
  if (field == null || parentValues.length === 0) return [...canonical]
  const kept = canonical.filter(row => !rowMatchesAnyParent(row, field, parentValues))
  return [...kept, ...slice]
}

/**
 * Widget emit is a display slice. Write it back onto every same-table binding
 * as the unsliced store. MI participant forms skip merge (`skipMerge`) so their
 * own-row sync keeps a participant-scoped array.
 */
export function applyDisplayedSliceToCanonical(
  bindings: FilterProjectionBinding[],
  bindingId: number | string,
  slice: unknown[],
  form: FilterProjectionFormContext,
  options?: { skipMerge?: boolean },
): unknown[] {
  const incoming = Array.isArray(slice) ? slice : []
  if (options?.skipMerge) return incoming
  const binding = bindings.find(b => String(b.bindingId) === String(bindingId))
  if (!binding) return incoming
  const siblings = bindings.filter(b => !isActionOrPrimary(b))
  if (!shouldProjectByFilter(binding, siblings)) {
    binding.data = incoming
    return incoming
  }
  const rowsByTableId = snapshotRowsByTableId(siblings)
  const parentValues = resolveFilterParentValues(binding, form, rowsByTableId, siblings)
  if (parentValues == null) {
    binding.data = incoming
    return incoming
  }
  const canonical = Array.isArray(binding.data) ? binding.data : []
  const next = mergeFilterSliceIntoCanonical(canonical, incoming, binding, parentValues)
  const tid = Number(binding.tableId)
  for (const sibling of siblings) {
    if (Number(sibling.tableId) === tid) sibling.data = next
  }
  return next
}

export function sliceRowsByDeclaredFilter<T>(
  rows: readonly T[],
  binding: FilterProjectionBinding,
  parentValues: readonly unknown[],
): T[] {
  const field = declaredFilterFkFields(binding).filterFkFieldName
  if (field == null) return [...rows]
  if (parentValues.length === 0) return []
  return rows.filter(row => rowMatchesAnyParent(row, field, parentValues))
}

/** `null` = parent identity is unknown, keep V1 (do not slice). */
export function resolveFilterParentValues(
  binding: FilterProjectionBinding,
  form: FilterProjectionFormContext,
  rowsByTableId: ReadonlyMap<number, readonly unknown[]>,
  siblings: readonly FilterProjectionBinding[],
): unknown[] | null {
  const declared = declaredFilterFkFields(binding)
  const refTid = declared.filterFkRefTableId
  if (declared.filterFkFieldName == null || refTid == null) return null

  if (form.primaryTableId != null && Number(form.primaryTableId) === Number(refTid)) {
    const pkFields = primaryPkFields(form)
    if (pkFields.length === 0) return null
    const values = pkValuesOf(form.formData ?? null, pkFields)
    return values.length > 0 ? values : null
  }

  const currentItem = currentItemOf(form.formData)
  if (currentItem) {
    const fromItem = valuesFromCurrentItem(currentItem, pkFieldsOfParent(refTid, siblings, form))
    if (fromItem.length > 0) return fromItem
  }

  const siblingRows = rowsByTableId.get(Number(refTid)) ?? []
  const pkFields = pkFieldsOfParent(refTid, siblings, form)
  const fromSiblings: unknown[] = []
  for (const row of siblingRows) {
    fromSiblings.push(...pkValuesOf(asRecord(row), pkFields))
  }
  return uniquePresent(fromSiblings)
}

function isProjectableSibling(binding: FilterProjectionBinding, tableId: number): boolean {
  if (isActionOrPrimary(binding)) return false
  return Number(binding.tableId) === tableId
}

function isActionOrPrimary(binding: FilterProjectionBinding): boolean {
  const type = String(binding.bindingType ?? '').trim().toUpperCase()
  return type === 'ACTION' || type === 'PRIMARY'
}

function snapshotRowsByTableId(
  bindings: readonly FilterProjectionBinding[],
): Map<number, readonly unknown[]> {
  const out = new Map<number, readonly unknown[]>()
  for (const binding of bindings) {
    if (binding.tableId == null || !Array.isArray(binding.data)) continue
    const tid = Number(binding.tableId)
    if (!Number.isFinite(tid) || out.has(tid)) continue
    out.set(tid, binding.data)
  }
  return out
}

function primaryPkFields(form: FilterProjectionFormContext): string[] {
  return resolveMiChildPrimaryKeyColumns({
    primaryKeyFields: form.primaryPkFields ?? null,
    fieldDefinitions: form.primaryFieldDefinitions ?? null,
  })
}

function pkFieldsOfParent(
  refTid: number,
  siblings: readonly FilterProjectionBinding[],
  form: FilterProjectionFormContext,
): string[] {
  if (form.primaryTableId != null && Number(form.primaryTableId) === Number(refTid)) {
    return primaryPkFields(form)
  }
  const parent = siblings.find(s => Number(s.tableId) === Number(refTid))
  if (!parent) return []
  return resolveMiChildPrimaryKeyColumns({
    primaryKeyFields: parent.primaryKeyFields ?? null,
    fieldDefinitions: parent.fieldDefinitions ?? null,
  })
}

function pkValuesOf(record: Record<string, unknown> | null, pkFields: readonly string[]): unknown[] {
  if (!record) return []
  const out: unknown[] = []
  for (const field of pkFields) {
    const value = rowField(record, field)
    if (isPresent(value)) out.push(value)
  }
  return uniquePresent(out)
}

function valuesFromCurrentItem(
  item: Record<string, unknown>,
  pkFields: readonly string[],
): unknown[] {
  const rowKey = item.rowKey
  if (rowKey && typeof rowKey === 'object' && !Array.isArray(rowKey)) {
    const vals = uniquePresent(Object.values(rowKey as Record<string, unknown>))
    if (vals.length > 0) return vals
  }
  if (isPresent(item.rowId)) return [item.rowId]
  return pkValuesOf(item, pkFields)
}

function currentItemOf(formData: Record<string, unknown> | null | undefined): Record<string, unknown> | null {
  if (!formData) return null
  const raw = formData._currentItem ?? formData.currentItem
  return raw && typeof raw === 'object' && !Array.isArray(raw)
    ? raw as Record<string, unknown>
    : null
}

function rowMatchesAnyParent(row: unknown, field: string, parentValues: readonly unknown[]): boolean {
  const actual = rowField(asRecord(row), field)
  if (!isPresent(actual)) return false
  return parentValues.some(expected => sameValue(expected, actual))
}

function rowField(row: Record<string, unknown> | null, field: string): unknown {
  if (!row || !field) return undefined
  if (Object.prototype.hasOwnProperty.call(row, field)) return row[field]
  const lower = field.toLowerCase()
  for (const [key, value] of Object.entries(row)) {
    if (key.toLowerCase() === lower) return value
  }
  return undefined
}

function asRecord(row: unknown): Record<string, unknown> | null {
  return row && typeof row === 'object' && !Array.isArray(row)
    ? row as Record<string, unknown>
    : null
}

function isPresent(value: unknown): boolean {
  return value != null && String(value).trim() !== ''
}

function sameValue(a: unknown, b: unknown): boolean {
  return String(a).trim() === String(b).trim()
}

function uniquePresent(values: readonly unknown[]): unknown[] {
  const seen = new Set<string>()
  const out: unknown[] = []
  for (const value of values) {
    if (!isPresent(value)) continue
    const key = String(value).trim()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(value)
  }
  return out
}
