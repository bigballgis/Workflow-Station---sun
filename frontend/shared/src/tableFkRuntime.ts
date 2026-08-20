/**
 * Shared FK/PK runtime for Form Preview (developer-workstation) and Portal runtime (PRD §7, S4).
 *
 * CANONICAL copy — consumed by user-portal and developer-workstation via the
 * @platform-shared vite alias (frontend/shared/src). Both apps keep thin re-export
 * shims at src/utils/tableFkRuntime.ts so existing import paths stay stable.
 * Backend row-key semantics live in platform-common SubTableRowKeySupport.
 */

export interface FieldFkMeta {
  fieldName: string
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  fkDisplayMode?: 'readonly' | 'hidden'
}

export interface PkGenerationConfig {
  strategy?: 'manual' | 'uuid' | 'autoIncrement' | 'prefixedSequence' | 'dailyDateSequence' | 'monthlyDateSequence' | 'customFormat' | 'datePrefixedSequence'
  scope?: 'perTable' | 'perFunctionUnit' | 'perPrefix' | 'perDay' | 'perMonth'
  startValue?: number
  padWidth?: number
  prefix?: string
  datePattern?: string
  resetPeriod?: 'none' | 'day' | 'month'
  format?: string
}

export interface RowAddContext {
  primaryFormData: Record<string, unknown>
  ancestorRowsByTableId?: Record<number, Record<string, unknown>>
}

const UNIT_SEP = '\u001f'

function rowVal(row: Record<string, unknown>, key: string): unknown {
  if (!row || !key) return undefined
  if (key in row) return row[key]
  const lower = key.toLowerCase()
  for (const k of Object.keys(row)) {
    if (k.toLowerCase() === lower) return row[k]
  }
  return undefined
}

export function encodeCompositePrimaryKey(
  refPkFields: string[],
  parentRow: Record<string, unknown>,
): string | null {
  if (!refPkFields?.length || !parentRow) return null
  const ordered = [...refPkFields].sort()
  if (ordered.length === 1) {
    const v = rowVal(parentRow, ordered[0])
    return v != null ? String(v) : null
  }
  const parts = ordered.map(k => {
    const v = rowVal(parentRow, k)
    return v != null ? `${k}=${String(v)}` : null
  }).filter(Boolean)
  return parts.length === ordered.length ? parts.join(UNIT_SEP) : null
}

export function resolveForeignKeyValues(
  fkMetas: FieldFkMeta[],
  ctx: RowAddContext,
): Record<string, string> {
  const out: Record<string, string> = {}
  if (!fkMetas?.length || !ctx) return out
  for (const meta of fkMetas) {
    if (!meta?.isForeignKey || !meta.fieldName || !meta.refTableId) continue
    const parentRow = ctx.ancestorRowsByTableId?.[meta.refTableId] ?? ctx.primaryFormData
    const encoded = encodeCompositePrimaryKey(meta.refPrimaryKeyFields || [], parentRow)
    if (encoded != null) out[meta.fieldName] = encoded
  }
  return out
}

export function guardBeforeChildRowAdd(
  fkMetas: FieldFkMeta[],
  ctx: RowAddContext,
): string[] {
  const missing: string[] = []
  if (!fkMetas?.length) return missing
  for (const meta of fkMetas) {
    if (!meta?.isForeignKey || !meta.refTableId) continue
    const parentRow = ctx.ancestorRowsByTableId?.[meta.refTableId] ?? ctx.primaryFormData
    if (!parentRow || Object.keys(parentRow).length === 0) {
      missing.push(meta.fieldName)
      continue
    }
    const pkFields = meta.refPrimaryKeyFields || []
    for (const pk of pkFields) {
      const v = rowVal(parentRow, pk)
      if (v == null || String(v).trim() === '') {
        missing.push(meta.fieldName)
        break
      }
    }
  }
  return missing
}

export function applyFkToInitialRow(
  row: Record<string, unknown>,
  fkMetas: FieldFkMeta[],
  ctx: RowAddContext,
): Record<string, unknown> {
  const fkValues = resolveForeignKeyValues(fkMetas, ctx)
  return { ...row, ...fkValues }
}

export function isFkReadonly(meta: FieldFkMeta): boolean {
  return !!meta.isForeignKey && (meta.fkDisplayMode == null || meta.fkDisplayMode === 'readonly')
}

export function isFkHidden(meta: FieldFkMeta): boolean {
  return !!meta.isForeignKey && meta.fkDisplayMode === 'hidden'
}
