/**
 * Sub-table row runtime — binding field definitions + FK metadata helpers (PRD S5/S6).
 * Parity with developer-workstation Form Preview runtime.
 */
import {
  type FieldFkMeta,
  type PkGenerationConfig,
  type RowAddContext,
  isFkHidden,
  isFkReadonly,
} from '../tableFkRuntime'

export interface BindingFieldDefinition {
  fieldName: string
  isPrimaryKey?: boolean
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  pkGeneration?: PkGenerationConfig
  pkGenerationJson?: PkGenerationConfig | Record<string, unknown>
  fkDisplayMode?: 'readonly' | 'hidden'
  isComputed?: boolean
  computedField?: Record<string, unknown>
}

export type AllocatePrimaryKeysFn = (payload: {
  tableId: number
  fieldName: string
  count?: number
  scopeKey?: string
}) => Promise<string[]>

export type BindingLinkMode = 'structuralFk' | 'miParticipantRow'

export function toFieldFkMetas(fields: BindingFieldDefinition[] | undefined | null): FieldFkMeta[] {
  if (!fields?.length) return []
  return fields
    .filter(f => f.isForeignKey)
    .map(f => ({
      fieldName: f.fieldName,
      isForeignKey: true,
      refTableId: f.refTableId,
      refPrimaryKeyFields: f.refPrimaryKeyFields,
      fkDisplayMode: f.fkDisplayMode,
    }))
}

/** MI participant bindings keep legacy foreignKeyField for slice logic — exclude from structural FK runtime (PRD S6). */
export function filterStructuralFkMetasForBinding(
  fkMetas: FieldFkMeta[],
  options?: {
    bindingLinkMode?: BindingLinkMode | string | null
    bindingForeignKeyField?: string | null
  },
): FieldFkMeta[] {
  if (options?.bindingLinkMode !== 'miParticipantRow') return fkMetas
  const legacy = options.bindingForeignKeyField?.trim()
  if (!legacy) return fkMetas
  return fkMetas.filter(m => m.fieldName !== legacy)
}

export function buildRowAddContext(
  primaryFormData: Record<string, unknown>,
  subTableBindings?: Array<{
    bindingId?: number | string
    tableId?: number | null
    bindingType?: string
    filterFkRefTableId?: number | null
    data?: unknown[]
  }> | null,
  parentRow?: Record<string, unknown> | null,
  parentTableId?: number | null,
  currentBinding?: {
    bindingId?: number | string
    tableId?: number | null
    filterFkRefTableId?: number | null
  } | null,
): RowAddContext {
  const ancestorRowsByTableId: Record<number, Record<string, unknown>> = {}
  for (const b of subTableBindings ?? []) {
    if (b.tableId != null && b.bindingType === 'PRIMARY') {
      ancestorRowsByTableId[Number(b.tableId)] = primaryFormData
    }
  }
  if (parentRow && parentTableId != null) {
    ancestorRowsByTableId[Number(parentTableId)] = parentRow
  }
  attachUniqueFilterParent(ancestorRowsByTableId, subTableBindings, currentBinding)
  return { primaryFormData, ancestorRowsByTableId }
}

function attachUniqueFilterParent(
  ancestorRowsByTableId: Record<number, Record<string, unknown>>,
  subTableBindings: Array<{
    bindingId?: number | string
    tableId?: number | null
    data?: unknown[]
  }> | null | undefined,
  currentBinding: {
    bindingId?: number | string
    tableId?: number | null
    filterFkRefTableId?: number | null
  } | null | undefined,
): void {
  const refTid = currentBinding?.filterFkRefTableId
  if (refTid == null || !Number.isFinite(Number(refTid))) return
  if (ancestorRowsByTableId[Number(refTid)]) return
  const currentId = currentBinding?.bindingId
  const rows: Record<string, unknown>[] = []
  for (const sibling of subTableBindings ?? []) {
    if (currentId != null && String(sibling.bindingId) === String(currentId)) continue
    if (Number(sibling.tableId) !== Number(refTid)) continue
    for (const raw of Array.isArray(sibling.data) ? sibling.data : []) {
      if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
        rows.push(raw as Record<string, unknown>)
      }
    }
  }
  if (rows.length === 1) ancestorRowsByTableId[Number(refTid)] = rows[0]
}

/** True when binding.foreignKeyField names the child row's own PK (e.g. People.id), not the MI parent link. */
export function bindingForeignKeyFieldIsRowPrimaryKey(
  bindingForeignKeyField: string | null | undefined,
  options?: {
    primaryKeyFields?: string[] | null
    fieldDefinitions?: BindingFieldDefinition[] | null
  },
): boolean {
  const fk = bindingForeignKeyField?.trim()
  if (!fk) return false
  if (options?.primaryKeyFields?.some(p => String(p).trim() === fk)) return true
  const def = options?.fieldDefinitions?.find(f => f.fieldName === fk)
  return def?.isPrimaryKey === true
}

/** Map table / relation field DTOs to binding field definitions. */
export function relationFieldsToBindingDefs(
  fields: Array<{
    fieldName: string
    isPrimaryKey?: boolean
    isForeignKey?: boolean
    refTableId?: number
    refPrimaryKeyFields?: string[]
    pkGeneration?: PkGenerationConfig
    pkGenerationJson?: PkGenerationConfig | Record<string, unknown>
    fkDisplayMode?: string
    isComputed?: boolean
    computedField?: Record<string, unknown>
  }>,
): BindingFieldDefinition[] {
  return fields.map(f => ({
    fieldName: f.fieldName,
    isPrimaryKey: f.isPrimaryKey,
    isForeignKey: f.isForeignKey,
    refTableId: f.refTableId,
    refPrimaryKeyFields: f.refPrimaryKeyFields,
    pkGeneration: f.pkGeneration ?? (f.pkGenerationJson as PkGenerationConfig | undefined),
    pkGenerationJson: f.pkGenerationJson,
    fkDisplayMode: f.fkDisplayMode === 'hidden' ? 'hidden' : f.fkDisplayMode === 'readonly' ? 'readonly' : undefined,
    isComputed: f.isComputed,
    computedField: f.computedField,
  }))
}

/** Delegates to tableFkRuntime — the single FK readonly/hidden decision in this app. */
export function isFkFieldReadonly(field: BindingFieldDefinition): boolean {
  return isFkReadonly(field)
}

/** Delegates to tableFkRuntime — the single FK readonly/hidden decision in this app. */
export function isFkFieldHidden(field: BindingFieldDefinition): boolean {
  return isFkHidden(field)
}
