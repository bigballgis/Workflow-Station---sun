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
 * table with different configured filter FKs. Missing parent → empty list, not
 * the full store.
 */

import { declaredFilterFkFields } from './miBindingKindFromConfig'
import { resolveMiChildPrimaryKeyColumns } from './miLinkChildIdentity'

export interface FilterProjectionBinding {
  bindingId?: number | string
  tableId?: number | null
  tableName?: string | null
  designerTableName?: string | null
  bindingType?: string | null
  primaryKeyFields?: string[] | null
  fieldDefinitions?: Array<{
    fieldName?: string
    isPrimaryKey?: boolean
    isForeignKey?: boolean
    refTableId?: number | null
    refTableName?: string | null
  }> | null
  foreignKeyField?: string | null
  filterFkRefTableId?: number | null
  filterFkRefTableName?: string | null
  filterFkFieldName?: string | null
  data?: unknown[]
}

export interface FilterProjectionFormContext {
  formData?: Record<string, unknown> | null
  primaryTableId?: number | null
  primaryTableName?: string | null
  primaryPkFields?: string[] | null
  primaryFieldDefinitions?: Array<{ fieldName?: string; isPrimaryKey?: boolean }> | null
}

export function shouldProjectByFilter(
  binding: FilterProjectionBinding,
  siblings: readonly FilterProjectionBinding[],
): boolean {
  const mine = effectiveFilterDeclaration(binding)
  if (mine.fieldName == null || mine.refIdentity == null) return false
  const filters = new Set<string>()
  for (const sibling of siblings) {
    if (!isProjectableSibling(sibling, binding)) continue
    const declared = effectiveFilterDeclaration(sibling)
    if (declared.refIdentity != null && declared.fieldName != null) {
      filters.add(declared.fieldName.toLowerCase())
    }
  }
  return filters.size > 1
}

export function projectSavedRowsForBinding<T>(
  rows: T[] | undefined,
  binding: FilterProjectionBinding,
  siblings: readonly FilterProjectionBinding[],
  form: FilterProjectionFormContext,
): T[] | undefined {
  if (!rows) return rows
  if (!shouldProjectByFilter(binding, siblings)) return rows
  const rowsByTable = snapshotRowsByTable(siblings)
  const parentValues = resolveFilterParentValues(binding, form, rowsByTable, siblings)
  // Multi-filter table whose parent key is not on the form: claim nothing.
  // Returning the whole store lets this binding write sibling rows.
  if (parentValues == null) return []
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
  const field = effectiveFilterDeclaration(binding).fieldName
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
  const rowsByTable = snapshotRowsByTable(siblings)
  const parentValues = resolveFilterParentValues(binding, form, rowsByTable, siblings)
  if (parentValues == null) {
    return Array.isArray(binding.data) ? binding.data : []
  }
  const canonical = Array.isArray(binding.data) ? binding.data : []
  const next = mergeFilterSliceIntoCanonical(canonical, incoming, binding, parentValues)
  for (const sibling of siblings) {
    if (sameTable(binding, sibling)) sibling.data = next
  }
  return next
}

export function sliceRowsByDeclaredFilter<T>(
  rows: readonly T[],
  binding: FilterProjectionBinding,
  parentValues: readonly unknown[],
): T[] {
  const field = effectiveFilterDeclaration(binding).fieldName
  if (field == null) return [...rows]
  if (parentValues.length === 0) return []
  return rows.filter(row => rowMatchesAnyParent(row, field, parentValues))
}

/**
 * Remove rows whose configured filter FK points at a parent row that no longer exists in the
 * current form. This is the submit-time half of parent deletion: projection already makes those
 * rows disappear from the child widget, so retaining them in the canonical store would submit an
 * invisible orphan that no binding can claim.
 *
 * Rows without this binding's FK belong to sibling bindings and are preserved. Unknown parent
 * context ({@code null}) also preserves V1 behavior instead of guessing.
 */
export function removeRowsWithMissingDeclaredParent<T>(
  rows: readonly T[],
  binding: FilterProjectionBinding,
  siblings: readonly FilterProjectionBinding[],
  form: FilterProjectionFormContext,
): T[] {
  if (!shouldProjectByFilter(binding, siblings)) return [...rows]
  const field = effectiveFilterDeclaration(binding).fieldName
  if (field == null) return [...rows]
  const parentValues = resolveFilterParentValues(
    binding,
    form,
    snapshotRowsByTable(siblings),
    siblings,
  )
  if (parentValues == null) return [...rows]
  return rows.filter(row => {
    const value = rowField(asRecord(row), field)
    return !isPresent(value) || parentValues.some(parent => sameValue(parent, value))
  })
}

/** `null` = parent identity is unknown. Callers must not show or claim the whole store. */
export function resolveFilterParentValues(
  binding: FilterProjectionBinding,
  form: FilterProjectionFormContext,
  rowsByTable: ReadonlyMap<string, readonly unknown[]>,
  siblings: readonly FilterProjectionBinding[],
): unknown[] | null {
  const declared = effectiveFilterDeclaration(binding)
  const ref = declared.refIdentity
  if (declared.fieldName == null || ref == null) return null

  if (sameTableIdentity(ref, primaryTableIdentity(form))) {
    const pkFields = primaryPkFields(form)
    if (pkFields.length === 0) return null
    const values = pkValuesOf(form.formData ?? null, pkFields)
    return values.length > 0 ? values : null
  }

  const currentItem = currentItemOf(form.formData)
  if (currentItem) {
    const fromItem = valuesFromCurrentItem(currentItem, pkFieldsOfParent(ref, siblings, form))
    if (fromItem.length > 0) return fromItem
  }

  const siblingRows = rowsByTable.get(ref) ?? []
  const pkFields = pkFieldsOfParent(ref, siblings, form)
  const fromSiblings: unknown[] = []
  for (const row of siblingRows) {
    fromSiblings.push(...pkValuesOf(asRecord(row), pkFields))
  }
  return uniquePresent(fromSiblings)
}

function isProjectableSibling(
  binding: FilterProjectionBinding,
  target: FilterProjectionBinding,
): boolean {
  if (isActionOrPrimary(binding)) return false
  return sameTable(binding, target)
}

function isActionOrPrimary(binding: FilterProjectionBinding): boolean {
  const type = String(binding.bindingType ?? '').trim().toUpperCase()
  return type === 'ACTION' || type === 'PRIMARY'
}

function snapshotRowsByTable(
  bindings: readonly FilterProjectionBinding[],
): Map<string, readonly unknown[]> {
  const out = new Map<string, readonly unknown[]>()
  for (const binding of bindings) {
    if (!Array.isArray(binding.data)) continue
    const identity = tableIdentity(binding)
    if (identity == null || out.has(identity)) continue
    out.set(identity, binding.data)
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
  ref: string,
  siblings: readonly FilterProjectionBinding[],
  form: FilterProjectionFormContext,
): string[] {
  if (sameTableIdentity(ref, primaryTableIdentity(form))) {
    return primaryPkFields(form)
  }
  const parent = siblings.find(s => sameTableIdentity(ref, tableIdentity(s)))
  if (!parent) return []
  return resolveMiChildPrimaryKeyColumns({
    primaryKeyFields: parent.primaryKeyFields ?? null,
    fieldDefinitions: parent.fieldDefinitions ?? null,
  })
}

function sameTable(a: FilterProjectionBinding, b: FilterProjectionBinding): boolean {
  return sameTableIdentity(tableIdentity(a), tableIdentity(b))
}

function tableIdentity(binding: FilterProjectionBinding): string | null {
  const id = numericIdentity(binding.tableId)
  if (id != null) return `id:${id}`
  const name = normalizedTableName(binding.designerTableName ?? binding.tableName)
  return name == null ? null : `name:${name}`
}

function effectiveFilterDeclaration(binding: FilterProjectionBinding): {
  fieldName: string | null
  refIdentity: string | null
} {
  const explicit = declaredFilterFkFields(binding)
  const explicitRef = tableReferenceIdentity(
    explicit.filterFkRefTableId,
    binding.filterFkRefTableName,
  )
  if (explicit.filterFkFieldName != null) {
    return { fieldName: explicit.filterFkFieldName, refIdentity: explicitRef }
  }
  const configuredField = String(binding.foreignKeyField ?? '').trim()
  if (!configuredField) return { fieldName: null, refIdentity: null }
  const field = binding.fieldDefinitions?.find(definition =>
    definition.isForeignKey === true
      && String(definition.fieldName ?? '').trim().toLowerCase() === configuredField.toLowerCase(),
  )
  if (!field) return { fieldName: null, refIdentity: null }
  return {
    fieldName: configuredField,
    refIdentity: tableReferenceIdentity(field.refTableId, field.refTableName),
  }
}

function tableReferenceIdentity(
  tableId: number | null | undefined,
  tableName: string | null | undefined,
): string | null {
  const id = numericIdentity(tableId)
  if (id != null) return `id:${id}`
  const name = normalizedTableName(tableName)
  return name == null ? null : `name:${name}`
}

function primaryTableIdentity(form: FilterProjectionFormContext): string | null {
  const id = numericIdentity(form.primaryTableId)
  if (id != null) return `id:${id}`
  const name = normalizedTableName(form.primaryTableName)
  return name == null ? null : `name:${name}`
}

function sameTableIdentity(a: string | null, b: string | null): boolean {
  return a != null && b != null && a === b
}

function numericIdentity(value: number | null | undefined): number | null {
  if (value == null || String(value).trim() === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function normalizedTableName(value: string | null | undefined): string | null {
  const name = String(value ?? '').trim().toLowerCase()
  return name || null
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
