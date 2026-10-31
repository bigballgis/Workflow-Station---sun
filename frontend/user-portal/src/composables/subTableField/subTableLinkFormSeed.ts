import { encodeCompositePrimaryKey } from '@/utils/tableFkRuntime'
import { PLATFORM_ROW_UUID_FIELD } from '@/utils/subTableRowIdentity'
import type { SubTableBinding, SubTableFieldProps } from './subTableFieldTypes'
import { normalizeFkIdForMatch } from './subTableLinkFormRowMatch'

/** A self link uses row identity; a child link uses its declared parent relationship. Never seed from array position. */
export function linkedFormSeedRow(
  host: Pick<SubTableFieldProps, 'bindingId' | 'tableId'>,
  binding: SubTableBinding | undefined,
  parent: Record<string, unknown> | null | undefined,
): Record<string, unknown> | undefined {
  if (!binding || !parent) return undefined
  const rows = binding.data as Record<string, unknown>[]
  if (!Array.isArray(rows)) return undefined
  const self = (host.bindingId != null && Number(host.bindingId) === Number(binding.bindingId))
    || (host.tableId != null && binding.tableId != null && Number(host.tableId) === Number(binding.tableId))
  if (self) {
    const identity = (row: Record<string, unknown>) => {
      const keys = binding.primaryKeyFields ?? []
      const configured = keys.length && keys.every(key => normalizeFkIdForMatch(row[key]) != null)
        ? encodeCompositePrimaryKey(keys, row) : null
      return normalizeFkIdForMatch(configured) ?? normalizeFkIdForMatch(row[PLATFORM_ROW_UUID_FIELD])
    }
    const wanted = identity(parent)
    if (!wanted) return undefined
    const matches = rows.filter(row => identity(row) === wanted)
    return matches.length === 1 ? matches[0] : undefined
  }
  const scope = linkedFormChildScope(host, binding, parent)
  return scope?.rows.length === 1 ? scope.rows[0] : undefined
}

interface ChildScope { rows: Record<string, unknown>[]; seed: Record<string, unknown> }

// SQL text keys preserve case and whitespace; numeric JSON keys can still match their serialized values.
function referenceKey(value: unknown): string | null {
  return (typeof value === 'string' || typeof value === 'number') && String(value).trim() !== ''
    ? String(value) : null
}

/** A declared parent relationship also constrains heuristic candidates used by the modal's open path. */
export function linkedFormChildScope(
  host: Pick<SubTableFieldProps, 'bindingId' | 'tableId'>,
  binding: SubTableBinding | undefined,
  parent: Record<string, unknown> | null | undefined,
  candidates?: Record<string, unknown>[],
): ChildScope | undefined {
  if (!binding || !parent || host.tableId == null || Number(binding.tableId) === Number(host.tableId)) return undefined
  const references = (binding.fieldDefinitions ?? []).filter(field => field.isForeignKey
    && field.refTableId != null && Number(field.refTableId) === Number(host.tableId))
  if (!references.length) return undefined
  const relations = references.filter(field =>
    !binding.filterFkFieldName || field.fieldName === binding.filterFkFieldName)
  // Multiple parent relations need an explicit filter FK; no OR matching across distinct roles.
  if (relations.length !== 1) return { rows: [], seed: {} }
  const relation = relations[0]!
  if (!relation.refPrimaryKeyFields?.length
    || !relation.refPrimaryKeyFields.every(key => referenceKey(parent[key]) != null)) return { rows: [], seed: {} }
  const wanted = referenceKey(encodeCompositePrimaryKey(relation.refPrimaryKeyFields ?? [], parent))
  if (!wanted) return { rows: [], seed: {} }
  const rows = candidates ?? binding.data
  return { rows: Array.isArray(rows) ? rows.filter(row => referenceKey(row[relation.fieldName]) === wanted) : [],
    seed: { [relation.fieldName]: encodeCompositePrimaryKey(relation.refPrimaryKeyFields, parent) } }
}
