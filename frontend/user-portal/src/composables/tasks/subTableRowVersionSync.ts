import { getSavedSubTableRows } from './subTableSliceResolve'
import { rowKeyForScope } from './subTableCanonicalStamp'

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
