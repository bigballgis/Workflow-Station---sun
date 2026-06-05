/**
 * Sub-table add-row orchestration (PRD S5) — guard, FK fill, PK allocate, column presentation.
 */
import type { FieldDefinition } from '@/api/functionUnit'
import type { DialogColumn } from '../components/designer/subTableAddDialogHelpers'
import { buildInitialRow } from '../components/designer/subTableAddDialogHelpers'
import { normalizeFieldDefinitionForRuntime } from './formFieldMeta'
import {
  type FieldFkMeta,
  type PkGenerationConfig,
  type RowAddContext,
  applyFkToInitialRow,
  guardBeforeChildRowAdd,
  isFkHidden,
  isFkReadonly,
} from './tableFkRuntime'

export interface BindingFieldDefinition {
  fieldName: string
  isPrimaryKey?: boolean
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  pkGeneration?: PkGenerationConfig
  pkGenerationJson?: PkGenerationConfig
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

export function applyFkPresentationToDialogColumns(
  columns: DialogColumn[],
  fkMetas: FieldFkMeta[],
  fieldDefinitions?: BindingFieldDefinition[] | null,
): { visibleColumns: DialogColumn[]; allColumns: DialogColumn[] } {
  const metaByField = new Map(fkMetas.map(m => [m.fieldName, m]))
  const fieldByName = new Map((fieldDefinitions ?? []).map(f => [f.fieldName, f]))
  const allColumns = columns.map(col => {
    let next = col
    const meta = metaByField.get(col.field)
    if (meta?.isForeignKey && isFkReadonly(meta)) {
      next = { ...next, readonly: true }
    }
    const fieldDef = fieldByName.get(col.field)
    if (fieldDef) {
      next = applyAutoPkColumnPresentation(next, fieldDef)
    }
    return next
  })
  const visibleColumns = allColumns.filter(col => {
    const meta = metaByField.get(col.field)
    return !meta || !isFkHidden(meta)
  })
  return { visibleColumns, allColumns }
}

function pkNeedsAllocation(field: BindingFieldDefinition): boolean {
  const normalized = normalizeFieldDefinitionForRuntime(field as FieldDefinition)
  if (!normalized.isPrimaryKey) return false
  const pkConfig = normalized.pkGeneration ?? normalized.pkGenerationJson
  const strategy = pkConfig?.strategy ?? 'uuid'
  return strategy !== 'manual'
}

/** prefixedSequence / uuid allocate string values — inputNumber cannot bind them. */
function pkAllocationYieldsString(field: BindingFieldDefinition): boolean {
  const normalized = normalizeFieldDefinitionForRuntime(field as FieldDefinition)
  if (!normalized.isPrimaryKey) return false
  const pkConfig = normalized.pkGeneration ?? normalized.pkGenerationJson
  const strategy = pkConfig?.strategy ?? 'uuid'
  return strategy === 'uuid' || strategy === 'prefixedSequence'
}

function applyAutoPkColumnPresentation(
  col: DialogColumn,
  fieldDef: BindingFieldDefinition,
): DialogColumn {
  if (!pkNeedsAllocation(fieldDef)) return col
  let next: DialogColumn = { ...col, readonly: true }
  if (next.type === 'number' && pkAllocationYieldsString(fieldDef)) {
    next = { ...next, type: 'text' }
  }
  return next
}

function parentRowHasRequiredPk(
  parentRow: Record<string, unknown> | null | undefined,
  pkFields: string[],
): boolean {
  if (!parentRow || Object.keys(parentRow).length === 0) return false
  if (!pkFields.length) return true
  for (const pk of pkFields) {
    const v = parentRow[pk] ?? Object.entries(parentRow).find(([k]) => k.toLowerCase() === pk.toLowerCase())?.[1]
    if (v == null || String(v).trim() === '') return false
  }
  return true
}

async function allocateAutoPrimaryKeysForRow(
  fieldDefinitions: BindingFieldDefinition[],
  tableId: number,
  row: Record<string, unknown>,
  allocatePrimaryKeys: AllocatePrimaryKeysFn,
  functionUnitId?: string,
): Promise<Record<string, unknown>> {
  const next = { ...row }
  for (const field of fieldDefinitions) {
    if (!pkNeedsAllocation(field)) continue
    const existing = next[field.fieldName]
    if (existing != null && String(existing).trim() !== '') continue
    const values = await allocatePrimaryKeys({
      tableId,
      fieldName: field.fieldName,
      scopeKey: functionUnitId,
    })
    if (values?.[0] != null) {
      next[field.fieldName] = values[0]
    }
  }
  return next
}

/** When child add is blocked by missing parent PK, allocate auto PKs on ancestor rows (Form Preview). */
export async function ensureParentRowsForChildAdd(options: {
  fkMetas: FieldFkMeta[]
  rowAddContext: RowAddContext
  parentTablesById: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  allocatePrimaryKeys: AllocatePrimaryKeysFn
  functionUnitId?: string
  primaryTableId?: number | null
}): Promise<{ rowAddContext: RowAddContext; primaryFormDataPatch?: Record<string, unknown> }> {
  let primaryFormData = { ...options.rowAddContext.primaryFormData }
  let ancestorRowsByTableId = { ...(options.rowAddContext.ancestorRowsByTableId ?? {}) }
  let primaryFormDataPatch: Record<string, unknown> | undefined

  const refTableIds = [
    ...new Set(
      options.fkMetas
        .filter(m => m.isForeignKey && m.refTableId != null)
        .map(m => Number(m.refTableId)),
    ),
  ]

  for (const refTableId of refTableIds) {
    const tableMeta = options.parentTablesById[refTableId]
    if (!tableMeta?.fieldDefinitions?.length) continue

    const fkMeta = options.fkMetas.find(m => Number(m.refTableId) === refTableId)
    const pkFields = fkMeta?.refPrimaryKeyFields || []

    const existingRow =
      ancestorRowsByTableId[refTableId] != null
        ? { ...(ancestorRowsByTableId[refTableId] as Record<string, unknown>) }
        : { ...primaryFormData }

    if (parentRowHasRequiredPk(existingRow, pkFields)) continue

    const nextRow = await allocateAutoPrimaryKeysForRow(
      tableMeta.fieldDefinitions,
      refTableId,
      existingRow,
      options.allocatePrimaryKeys,
      options.functionUnitId,
    )

    if (!parentRowHasRequiredPk(nextRow, pkFields)) continue

    ancestorRowsByTableId[refTableId] = nextRow
    if (options.primaryTableId != null && refTableId === Number(options.primaryTableId)) {
      primaryFormData = nextRow
      primaryFormDataPatch = { ...nextRow }
    }
  }

  return {
    rowAddContext: { primaryFormData, ancestorRowsByTableId },
    ...(primaryFormDataPatch ? { primaryFormDataPatch } : {}),
  }
}

export async function prepareSubTableAddRow(options: {
  columns: DialogColumn[]
  fieldDefinitions?: BindingFieldDefinition[]
  rowAddContext: RowAddContext
  tableId?: number | null
  tableDisplayName?: string
  primaryTableDisplayName?: string
  primaryTableId?: number | null
  parentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  functionUnitId?: string | null
  allocatePrimaryKeys?: AllocatePrimaryKeysFn
  requireFkGuard?: boolean
  autoEnsurePrimaryRecord?: boolean
  bindingLinkMode?: BindingLinkMode | string | null
  bindingForeignKeyField?: string | null
  t?: (key: string, params?: Record<string, unknown>) => string
}): Promise<
  | { ok: true; initialRow: Record<string, unknown>; dialogColumns: DialogColumn[]; primaryFormDataPatch?: Record<string, unknown> }
  | { ok: false; message: string }
> {
  const {
    columns,
    fieldDefinitions = [],
    rowAddContext: initialRowAddContext,
    tableId,
    allocatePrimaryKeys,
    requireFkGuard = true,
    autoEnsurePrimaryRecord = false,
    parentTablesById,
    t = (k) => k,
  } = options

  let rowAddContext = initialRowAddContext

  const fkMetas = filterStructuralFkMetasForBinding(toFieldFkMetas(fieldDefinitions), {
    bindingLinkMode: options.bindingLinkMode,
    bindingForeignKeyField: options.bindingForeignKeyField,
  })

  let primaryFormDataPatch: Record<string, unknown> | undefined

  if (requireFkGuard && fkMetas.length > 0) {
    let missing = guardBeforeChildRowAdd(fkMetas, rowAddContext)
    if (
      missing.length > 0
      && autoEnsurePrimaryRecord
      && allocatePrimaryKeys
      && parentTablesById
      && Object.keys(parentTablesById).length > 0
    ) {
      const ensured = await ensureParentRowsForChildAdd({
        fkMetas,
        rowAddContext,
        parentTablesById,
        allocatePrimaryKeys,
        functionUnitId: options.functionUnitId ?? undefined,
        primaryTableId: options.primaryTableId,
      })
      rowAddContext = ensured.rowAddContext
      primaryFormDataPatch = ensured.primaryFormDataPatch
      missing = guardBeforeChildRowAdd(fkMetas, rowAddContext)
    }
    if (missing.length > 0) {
      const parentName = options.primaryTableDisplayName || t('subTable.mainTableDefault')
      const childName = options.tableDisplayName || t('subTable.childTableDefault')
      return {
        ok: false,
        message: t('subTable.fkGuardMainNotReady', { parentTableName: parentName, childTableName: childName }),
      }
    }
  }

  const { visibleColumns, allColumns } = applyFkPresentationToDialogColumns(columns, fkMetas, fieldDefinitions)
  let row = buildInitialRow(allColumns)
  row = applyFkToInitialRow(row, fkMetas, rowAddContext)

  if (tableId != null && allocatePrimaryKeys) {
    for (const field of fieldDefinitions.map(f => normalizeFieldDefinitionForRuntime(f as FieldDefinition))) {
      if (!pkNeedsAllocation(field)) continue
      const existing = row[field.fieldName]
      if (existing != null && String(existing).trim() !== '') continue
      const values = await allocatePrimaryKeys({
        tableId: Number(tableId),
        fieldName: field.fieldName,
        scopeKey: options.functionUnitId ?? undefined,
      })
      if (values?.[0] != null) {
        row[field.fieldName] = values[0]
      }
    }
  }

  return {
    ok: true,
    initialRow: row,
    dialogColumns: visibleColumns,
    ...(primaryFormDataPatch ? { primaryFormDataPatch } : {}),
  }
}
