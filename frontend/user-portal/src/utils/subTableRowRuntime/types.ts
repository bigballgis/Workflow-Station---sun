/**
 * Sub-table row runtime — binding field definitions + FK metadata helpers (PRD S5/S6).
 * Parity with developer-workstation Form Preview runtime.
 */
import {
  type FieldFkMeta,
  type PkGenerationConfig,
  type RowAddContext,
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
  subTableBindings?: Array<{ tableId?: number | null; bindingType?: string }> | null,
  parentRow?: Record<string, unknown> | null,
  parentTableId?: number | null,
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
  return { primaryFormData, ancestorRowsByTableId }
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
  }))
}

export function isFkFieldReadonly(field: BindingFieldDefinition): boolean {
  if (!field.isForeignKey) return false
  return field.fkDisplayMode == null || field.fkDisplayMode === 'readonly'
}

export function isFkFieldHidden(field: BindingFieldDefinition): boolean {
  return !!field.isForeignKey && field.fkDisplayMode === 'hidden'
}
