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
  if (host.tableId == null) return undefined
  const relations = (binding.fieldDefinitions ?? []).filter(field => field.isForeignKey
    && field.refTableId != null && Number(field.refTableId) === Number(host.tableId)
    && (!binding.filterFkFieldName || field.fieldName === binding.filterFkFieldName))
  // Multiple parent relations need an explicit filter FK; no OR matching across distinct roles.
  if (relations.length !== 1) return undefined
  const relation = relations[0]!
  if (!relation.refPrimaryKeyFields?.length
    || !relation.refPrimaryKeyFields.every(key => normalizeFkIdForMatch(parent[key]) != null)) return undefined
  const wanted = normalizeFkIdForMatch(encodeCompositePrimaryKey(relation.refPrimaryKeyFields ?? [], parent))
  if (!wanted) return undefined
  const matches = rows.filter(row => normalizeFkIdForMatch(row[relation.fieldName]) === wanted)
  return matches.length === 1 ? matches[0] : undefined
}
