/**
 * Designer tableId / fieldDefinitions maps for nested flatten.
 * Membership ("this child row belongs to this parent") must use structural FK
 * columns, never a scan of every scalar field.
 */
import {
  subTableStoreKey,
  storeKeysAddressSameTable,
  type SubTableStoreBindingLike,
} from './subTableStore'
import {
  resolveMiChildStructuralFkColumns,
  type MiChildFkConfig,
} from './miLinkChildIdentity'

export type FlattenBindingLike = SubTableStoreBindingLike & {
  primaryKeyFields?: string[] | null
  fieldDefinitions?: MiChildFkConfig['fieldDefinitions']
  foreignKeyField?: string | null
  bindingLinkMode?: string | null
}

export interface FlattenParentLinkMaps {
  tableIdBySliceKey?: Readonly<Record<string, number | null | undefined>>
  fieldDefinitionsBySliceKey?: Readonly<
    Record<string, MiChildFkConfig['fieldDefinitions'] | undefined>
  >
  foreignKeyFieldBySliceKey?: Readonly<Record<string, string | null | undefined>>
  bindingLinkModeBySliceKey?: Readonly<Record<string, string | null | undefined>>
}

function mapValueForSliceKey<T>(
  map: Readonly<Record<string, T>> | undefined,
  sliceKey: string,
): T | undefined {
  if (!map) return undefined
  if (Object.prototype.hasOwnProperty.call(map, sliceKey)) return map[sliceKey]
  for (const [k, v] of Object.entries(map)) {
    if (storeKeysAddressSameTable(k, sliceKey)) return v as T
  }
  return undefined
}

/**
 * Build PK + parent-link maps from the form's sub-table bindings.
 * Call sites that hold bindings must pass these into flatten so delete-to-empty
 * and partial nested delete can drop only rows whose structural FK points at
 * the parent. Missing maps → flatten must not drop.
 */
export function flattenSliceMapsFromBindings(
  bindings: Iterable<FlattenBindingLike>,
): {
  primaryKeyFieldsBySliceKey: Record<string, readonly string[] | undefined>
  parentLink: FlattenParentLinkMaps
} {
  const primaryKeyFieldsBySliceKey: Record<string, readonly string[] | undefined> = {}
  const tableIdBySliceKey: Record<string, number> = {}
  const fieldDefinitionsBySliceKey: Record<string, NonNullable<MiChildFkConfig['fieldDefinitions']>> = {}
  const foreignKeyFieldBySliceKey: Record<string, string> = {}
  const bindingLinkModeBySliceKey: Record<string, string> = {}
  for (const b of bindings) {
    const key = subTableStoreKey(b)
    if (!key) continue
    primaryKeyFieldsBySliceKey[key] = b.primaryKeyFields ?? undefined
    const tid = b.tableId != null ? Number(b.tableId) : NaN
    if (Number.isFinite(tid)) tableIdBySliceKey[key] = tid
    if (Array.isArray(b.fieldDefinitions)) {
      fieldDefinitionsBySliceKey[key] = b.fieldDefinitions
    }
    const fk = String(b.foreignKeyField ?? '').trim()
    if (fk) foreignKeyFieldBySliceKey[key] = fk
    const mode = String(b.bindingLinkMode ?? '').trim()
    if (mode) bindingLinkModeBySliceKey[key] = mode
  }
  return {
    primaryKeyFieldsBySliceKey,
    parentLink: {
      tableIdBySliceKey,
      fieldDefinitionsBySliceKey,
      foreignKeyFieldBySliceKey,
      bindingLinkModeBySliceKey,
    },
  }
}

/**
 * Child-table columns that are designer FKs pointing at the parent table.
 * `null` = no binding metadata for this child (cannot tell → caller must not drop).
 * `[]` = metadata present and no FK to this parent (also do not drop).
 */
export function resolveChildFkColumnsPointingAtParent(
  childKey: string,
  parentSliceKey: string,
  parentLink?: FlattenParentLinkMaps | null,
): string[] | null {
  if (!parentLink) return null
  const parentTidRaw = mapValueForSliceKey(parentLink.tableIdBySliceKey, parentSliceKey)
  if (parentTidRaw == null) return null
  const parentTid = Number(parentTidRaw)
  if (!Number.isFinite(parentTid)) return null

  const defs = mapValueForSliceKey(parentLink.fieldDefinitionsBySliceKey, childKey)
  const fkField = mapValueForSliceKey(parentLink.foreignKeyFieldBySliceKey, childKey)
  const hasDefs = Array.isArray(defs)
  const hasFkField = String(fkField ?? '').trim() !== ''
  if (!hasDefs && !hasFkField) return null

  return resolveMiChildStructuralFkColumns({
    fieldDefinitions: hasDefs ? defs : null,
    miCollectionTableId: parentTid,
    bindingForeignKeyField: hasFkField ? String(fkField).trim() : null,
    bindingLinkMode: mapValueForSliceKey(parentLink.bindingLinkModeBySliceKey, childKey) ?? null,
  })
}
