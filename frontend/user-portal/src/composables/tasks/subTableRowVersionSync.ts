import { getSavedSubTableRows } from './subTableSliceResolve'
import { rowKeyForScope } from './subTableCanonicalStamp'
import { subTableStoreKey } from './subTableStore'

/**
 * Copy `_wsRowVersion` from the server snapshot onto the rows still on the page.
 *
 * Autosave persists the sub-table and bumps the version on the server. The open
 * task keeps the previous number until a full reload, so Submit is rejected with
 * "modified by another save". Field values on the page stay; only the version
 * number is replaced, matched by the same row identity the scope guard uses.
 */
export function copyWsRowVersions(
  targetRows: unknown,
  sourceRows: unknown,
  pkFields: string[] | null | undefined,
): void {
  if (!Array.isArray(targetRows) || !Array.isArray(sourceRows)) return
  const versions = indexRowVersions(sourceRows, pkFields)
  for (const raw of targetRows) {
    if (!isRow(raw)) continue
    const key = rowKeyForScope(raw, pkFields)
    if (!key) continue
    const version = versions.get(versionIndexKey(key))
    if (version === undefined) continue
    raw._wsRowVersion = version
  }
}

/**
 * The last server snapshot is the only version authority. Nested `__subTables__`
 * copies are a second writable image of the same rows and must not be what the
 * next save sends. New rows, which the baseline does not know, stay without a version.
 */
export function stampAuthoritativeRowVersions(
  subTables: Record<string, unknown>,
  baseline: Record<string, unknown> | null | undefined,
  bindings: ReadonlyArray<VersionedBinding>,
): void {
  if (!baseline) return
  for (const binding of versionSources(baseline, bindings)) {
    copyWsRowVersions(
      getSavedSubTableRows(subTables, binding.resolved),
      binding.rows,
      binding.pk,
    )
  }
  copyVersionsIntoNestedCopies(subTables, baseline, bindings)
}

/** Put the raw top-level snapshot's versions back onto binding rows after slice merges. */
export function restoreAuthoritativeRowVersions(
  authority: Record<string, unknown> | null | undefined,
  bindings: ReadonlyArray<VersionedBinding & { data?: unknown }>,
): void {
  if (!authority) return
  for (const binding of bindings) {
    const resolved = resolvableBinding(binding)
    if (!resolved) continue
    const rows = getSavedSubTableRows(authority, resolved)
    if (!Array.isArray(rows)) continue
    copyWsRowVersions(binding.data, rows, binding.primaryKeyFields)
  }
  const holder: Record<string, unknown> = {}
  bindings.forEach((binding, index) => {
    holder[String(index)] = binding.data
  })
  copyVersionsIntoNestedCopies(holder, authority, bindings)
}

export function applyServerRowVersionsToLiveRows(args: {
  bindings: Array<{
    bindingId: number
    tableName: string
    designerTableName?: string
    tableId?: number | null
    primaryKeyFields?: string[] | null
    data?: unknown
  }>
  formSubTables: Record<string, unknown> | undefined
  baseline: Record<string, unknown> | undefined
  serverSubTables: Record<string, unknown>
}): void {
  for (const binding of args.bindings) {
    const serverRows = getSavedSubTableRows(args.serverSubTables, binding)
    if (!Array.isArray(serverRows)) continue
    const pk = binding.primaryKeyFields
    copyWsRowVersions(binding.data, serverRows, pk)
    if (args.formSubTables) {
      copyWsRowVersions(getSavedSubTableRows(args.formSubTables, binding), serverRows, pk)
    }
    if (args.baseline) {
      copyWsRowVersions(getSavedSubTableRows(args.baseline, binding), serverRows, pk)
    }
  }
  if (args.formSubTables) {
    copyVersionsIntoNestedCopies(args.formSubTables, args.serverSubTables, args.bindings)
  }
  for (const binding of args.bindings) {
    if (!Array.isArray(binding.data)) continue
    copyVersionsIntoNestedCopies({ rows: binding.data }, args.serverSubTables, args.bindings)
  }
}

interface VersionedBinding {
  bindingId?: number | string
  tableName?: string | null
  designerTableName?: string | null
  tableId?: number | null
  primaryKeyFields?: string[] | null
}

function versionSources(
  baseline: Record<string, unknown>,
  bindings: ReadonlyArray<VersionedBinding>,
) {
  return bindings.flatMap(binding => {
    const resolved = resolvableBinding(binding)
    if (!resolved) return []
    const storeKey = subTableStoreKey(resolved)
    const rows = getSavedSubTableRows(baseline, resolved)
    return Array.isArray(rows) && storeKey
      ? [{ resolved, storeKey, rows, pk: binding.primaryKeyFields }]
      : []
  })
}

function resolvableBinding(binding: VersionedBinding): {
  bindingId: number
  tableName: string
  designerTableName?: string
  tableId?: number | null
  primaryKeyFields?: string[] | null
} | null {
  const bindingId = Number(binding.bindingId)
  const tableName = binding.tableName ?? binding.designerTableName
  if (!Number.isFinite(bindingId) || !tableName) return null
  return {
    bindingId,
    tableName,
    designerTableName: binding.designerTableName ?? undefined,
    tableId: binding.tableId,
    primaryKeyFields: binding.primaryKeyFields,
  }
}

function copyVersionsIntoNestedCopies(
  subTables: Record<string, unknown>,
  baseline: Record<string, unknown>,
  bindings: ReadonlyArray<VersionedBinding>,
): void {
  const sources = versionSources(baseline, bindings)
  for (const value of Object.values(subTables)) {
    copyVersionsOnNestedRows(value, sources, 0)
  }
}

function copyVersionsOnNestedRows(
  rows: unknown,
  sources: ReadonlyArray<{ storeKey: string; rows: unknown[]; pk: string[] | null | undefined }>,
  depth: number,
): void {
  if (depth > 8 || !Array.isArray(rows)) return
  for (const raw of rows) {
    if (!isRow(raw) || !isRow(raw.__subTables__)) continue
    for (const [storeKey, child] of Object.entries(raw.__subTables__)) {
      for (const source of sources) {
        if (source.storeKey !== storeKey) continue
        copyWsRowVersions(child, source.rows, source.pk)
      }
      copyVersionsOnNestedRows(child, sources, depth + 1)
    }
  }
}

function indexRowVersions(
  sourceRows: unknown[],
  pkFields: string[] | null | undefined,
): Map<string, unknown> {
  const versions = new Map<string, unknown>()
  for (const raw of sourceRows) {
    if (!isRow(raw) || raw._wsRowVersion == null || raw._wsRowVersion === '') continue
    const key = rowKeyForScope(raw, pkFields)
    if (!key) continue
    versions.set(versionIndexKey(key), raw._wsRowVersion)
  }
  return versions
}

function versionIndexKey(key: Record<string, unknown>): string {
  return Object.keys(key).sort().map(field => `${field}=${String(key[field])}`).join('\u001f')
}

function isRow(raw: unknown): raw is Record<string, unknown> {
  return !!raw && typeof raw === 'object' && !Array.isArray(raw)
}
