/**
 * Shared FK/PK runtime for Form Preview (developer-workstation) and Portal runtime (PRD §7, S4).
 *
 * CANONICAL copy — consumed by user-portal and developer-workstation via the
 * @platform-shared vite alias (frontend/shared/src). Both apps keep thin re-export
 * shims at src/utils/tableFkRuntime.ts so existing import paths stay stable.
 * Backend row-key semantics live in platform-common SubTableRowKeySupport.
 */
export type {
  BindingContextInput,
  ContextFrame,
  ContextFrameRole,
  RowAddContext,
} from './tableFkContext'
export {
  ancestorMapFromUniqueFrames,
  buildRowAddContext,
  contextHasScopedAncestors,
  framesMatchingTable,
  hasAmbiguousAncestorTable,
  replaceAncestorRow,
  sameAncestorRow,
  uniqueAncestorRow,
} from './tableFkContext'

import type { FieldFkMeta } from './tableFkMeta'
import {
  contextHasScopedAncestors,
  framesMatchingTable,
  rowFieldValue,
  uniqueAncestorRow,
  type ContextFrame,
  type RowAddContext,
} from './tableFkContext'

export type { FieldFkMeta, FkFillKind, FkFillSourceConfig } from './tableFkMeta'
export { applyFkFillSources, declaredFkFillSources, isFkHidden, isFkReadonly } from './tableFkMeta'

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

const UNIT_SEP = '\u001f'

export function encodeCompositePrimaryKey(
  refPkFields: string[],
  parentRow: Record<string, unknown>,
): string | null {
  if (!refPkFields?.length || !parentRow) return null
  const ordered = [...refPkFields].sort()
  if (ordered.length === 1) {
    const v = rowFieldValue(parentRow, ordered[0])
    return v != null ? String(v) : null
  }
  const parts = ordered.map(k => {
    const v = rowFieldValue(parentRow, k)
    return v != null ? `${k}=${String(v)}` : null
  }).filter(Boolean)
  return parts.length === ordered.length ? parts.join(UNIT_SEP) : null
}

function ancestorRowForFk(
  meta: FieldFkMeta,
  ctx: RowAddContext,
): Record<string, unknown> | null {
  const kind = meta.fkFillKind
  if (kind === 'PRIMARY') {
    return primaryRowForFk(meta, ctx)
  }
  if (kind === 'PARENT') {
    return parentRowForFk(meta, ctx)
  }
  if (kind === 'ANCESTOR') {
    return ancestorBindingRowForFk(meta, ctx)
  }
  const unique = uniqueAncestorRow(ctx, meta.refTableId)
  if (unique) return unique
  if (contextHasScopedAncestors(ctx)) return null
  return ctx.primaryFormData ?? null
}

function primaryRowForFk(
  meta: FieldFkMeta,
  ctx: RowAddContext,
): Record<string, unknown> | null {
  const frames = (ctx.contextFrames ?? []).filter(f => f.role === 'PRIMARY')
  if (frames.length === 1) return frames[0].row
  if (meta.refTableId != null) {
    const matched = frames.filter(f => Number(f.tableId) === Number(meta.refTableId))
    if (matched.length === 1) return matched[0].row
  }
  return ctx.primaryFormData ?? null
}

function parentRowForFk(
  meta: FieldFkMeta,
  ctx: RowAddContext,
): Record<string, unknown> | null {
  const parentFrames = (ctx.contextFrames ?? []).filter(f => f.role === 'PARENT')
  if (meta.refTableId != null) {
    const matched = parentFrames.filter(f => Number(f.tableId) === Number(meta.refTableId))
    if (matched.length === 1) return matched[0].row
    if (matched.length > 1) return null
  }
  if (parentFrames.length === 1) return parentFrames[0].row
  return null
}

function ancestorBindingRowForFk(
  meta: FieldFkMeta,
  ctx: RowAddContext,
): Record<string, unknown> | null {
  const frames = framesForDeclaredAncestor(meta, ctx)
  if (frames.length !== 1) return null
  if (meta.refTableId != null && Number(frames[0].tableId) !== Number(meta.refTableId)) return null
  return frames[0].row
}

function framesForDeclaredAncestor(meta: FieldFkMeta, ctx: RowAddContext): ContextFrame[] {
  const frames = ctx.contextFrames ?? []
  if (meta.ancestorBindingId != null && String(meta.ancestorBindingId).trim() !== '') {
    return frames.filter(f => f.bindingId != null && String(f.bindingId) === String(meta.ancestorBindingId))
  }
  return []
}

export function resolveForeignKeyValues(
  fkMetas: FieldFkMeta[],
  ctx: RowAddContext,
): Record<string, string> {
  const out: Record<string, string> = {}
  if (!fkMetas?.length || !ctx) return out
  for (const meta of fkMetas) {
    if (!meta?.isForeignKey || !meta.fieldName || !meta.refTableId) continue
    const parentRow = ancestorRowForFk(meta, ctx)
    if (!parentRow) continue
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
  const scoped = contextHasScopedAncestors(ctx)
  for (const meta of fkMetas) {
    if (!meta?.isForeignKey || !meta.refTableId) continue
    if (scoped && uniqueAncestorRow(ctx, meta.refTableId) == null
      && framesMatchingTable(ctx, Number(meta.refTableId)).length === 0
      && meta.fkFillKind == null) {
      continue
    }
    const parentRow = ancestorRowForFk(meta, ctx)
    if (!parentRow || Object.keys(parentRow).length === 0) {
      missing.push(meta.fieldName)
      continue
    }
    const pkFields = meta.refPrimaryKeyFields || []
    for (const pk of pkFields) {
      const v = rowFieldValue(parentRow, pk)
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
