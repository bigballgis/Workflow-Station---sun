/**
 * Sub-table add-row orchestration (PRD S5) — guard, FK fill, PK allocate, column presentation.
 * Parity with developer-workstation Form Preview runtime.
 */
import type { DialogColumn } from '../components/subTableAddDialogHelpers'
import { buildInitialRow } from '../components/subTableAddDialogHelpers'
import { normalizeFieldDefinitionForRuntime, type RuntimeFieldDefinition } from './formFieldMeta'
import {
  type FieldFkMeta,
  type PkGenerationConfig,
  type RowAddContext,
  applyFkToInitialRow,
  guardBeforeChildRowAdd,
  isFkHidden,
  isFkReadonly,
  resolveForeignKeyValues,
} from './tableFkRuntime'

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
  const normalized = normalizeFieldDefinitionForRuntime(field as RuntimeFieldDefinition)
  if (!normalized.isPrimaryKey) return false
  const pkConfig = normalized.pkGeneration ?? normalized.pkGenerationJson
  const strategy = (pkConfig as PkGenerationConfig | undefined)?.strategy ?? 'uuid'
  return strategy !== 'manual'
}

/** prefixedSequence / uuid allocate string values — inputNumber cannot bind them. */
function pkAllocationYieldsString(field: BindingFieldDefinition): boolean {
  const normalized = normalizeFieldDefinitionForRuntime(field as RuntimeFieldDefinition)
  if (!normalized.isPrimaryKey) return false
  const pkConfig = normalized.pkGeneration ?? normalized.pkGenerationJson
  const strategy = (pkConfig as PkGenerationConfig | undefined)?.strategy ?? 'uuid'
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

/** Clear link-child row PK values wrongly copied from parent MI id_idw (e.g. People.id), not collection id_idw. */
export function repairMisassignedPrimaryKeyFromParentId(
  row: Record<string, unknown>,
  fieldDefinitions: BindingFieldDefinition[] | undefined | null,
  parentIdIdw: string | number | null | undefined,
): Record<string, unknown> {
  if (parentIdIdw == null || String(parentIdIdw).trim() === '') return row
  const parentKey = String(parentIdIdw).trim()
  const out = { ...row }
  for (const def of fieldDefinitions ?? []) {
    if (!def.isPrimaryKey) continue
    const name = def.fieldName
    /** MI collection PK — must equal parent expansion key; never strip. */
    if (name === 'id_idw') continue
    const v = out[name]
    if (v != null && v !== '' && String(v).trim() === parentKey) {
      delete out[name]
    }
  }
  return out
}

export async function ensureAutoPrimaryKeysForRows(
  fieldDefinitions: BindingFieldDefinition[] | undefined | null,
  tableId: number | null | undefined,
  rows: Record<string, unknown>[],
  allocatePrimaryKeys: AllocatePrimaryKeysFn,
  functionUnitId?: string,
): Promise<Record<string, unknown>[]> {
  if (!fieldDefinitions?.length || tableId == null || !Number.isFinite(Number(tableId))) {
    return rows
  }
  const tid = Number(tableId)
  return Promise.all(
    rows.map(row =>
      allocateAutoPrimaryKeysForRow(
        fieldDefinitions,
        tid,
        { ...row },
        allocatePrimaryKeys,
        functionUnitId,
      ),
    ),
  )
}

/** Inline / modal subForm fields: auto-PK and string FK values must not use el-input-number. */
export function applyFieldDefinitionsToFormFields<
  T extends { key: string; type?: string; readonly?: boolean },
>(fields: T[], fieldDefinitions?: BindingFieldDefinition[] | null): T[] {
  const fieldByName = new Map((fieldDefinitions ?? []).map(f => [f.fieldName, f]))
  return fields.map(field => {
    const def = fieldByName.get(field.key)
    if (!def) return field
    let next = { ...field }
    if (next.type === 'number' && pkNeedsAllocation(def) && pkAllocationYieldsString(def)) {
      next = { ...next, type: 'text', readonly: true }
    }
    return next
  })
}

/** Seed MI link-child FK columns (designer structural FKs e.g. sub_task_id; not row PK id). */
export function seedLinkChildForeignKeysFromParentRow(
  row: Record<string, unknown>,
  fieldDefinitions: BindingFieldDefinition[] | undefined | null,
  options: {
    bindingForeignKeyField?: string | null
    bindingLinkMode?: BindingLinkMode | string | null
    primaryKeyFields?: string[] | null
    parentParticipantRow: Record<string, unknown>
    parentTableId: number | null | undefined
    legacyFkSeed?: string | number | null
  },
): Record<string, unknown> {
  const out = { ...row }
  const legacy = options.bindingForeignKeyField?.trim()
  const legacyIsRowPk = bindingForeignKeyFieldIsRowPrimaryKey(legacy, {
    primaryKeyFields: options.primaryKeyFields,
    fieldDefinitions,
  })
  if (
    legacy
    && !legacyIsRowPk
    && options.legacyFkSeed != null
    && String(options.legacyFkSeed).trim() !== ''
    && (out[legacy] == null || out[legacy] === '')
  ) {
    out[legacy] = options.legacyFkSeed
  }
  const parentTid = options.parentTableId != null ? Number(options.parentTableId) : NaN
  if (!Number.isFinite(parentTid)) return out
  const fkMetas = filterStructuralFkMetasForBinding(toFieldFkMetas(fieldDefinitions), {
    bindingLinkMode: options.bindingLinkMode,
    bindingForeignKeyField: options.bindingForeignKeyField,
  })
  if (fkMetas.length === 0) return out
  const ctx: RowAddContext = {
    primaryFormData: {},
    ancestorRowsByTableId: { [parentTid]: options.parentParticipantRow },
  }
  const fkValues = resolveForeignKeyValues(fkMetas, ctx)
  for (const [k, v] of Object.entries(fkValues)) {
    if (out[k] == null || out[k] === '') out[k] = v
  }
  return out
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

/** Allocate auto-PKs on a child row (Save path). Honors MI participant seed skip rules. */
export async function allocateChildRowAutoPrimaryKeys(options: {
  row: Record<string, unknown>
  fieldDefinitions: BindingFieldDefinition[]
  tableId: number
  allocatePrimaryKeys: AllocatePrimaryKeysFn
  functionUnitId?: string
  bindingLinkMode?: BindingLinkMode | string | null
  bindingForeignKeyField?: string | null
  primaryKeyFields?: string[] | null
  miParticipantRowId?: string | number | null
}): Promise<Record<string, unknown>> {
  const {
    row: inputRow,
    fieldDefinitions,
    tableId,
    allocatePrimaryKeys,
    functionUnitId,
  } = options
  const legacyFk = options.bindingForeignKeyField?.trim()
  const legacyFkIsRowPk =
    !!legacyFk
    && bindingForeignKeyFieldIsRowPrimaryKey(legacyFk, {
      primaryKeyFields: options.primaryKeyFields,
      fieldDefinitions,
    })
  const miParticipantSeedPresent =
    options.miParticipantRowId != null && String(options.miParticipantRowId).trim() !== ''

  let row = { ...inputRow }
  const normalizedFields = fieldDefinitions.map(f =>
    normalizeFieldDefinitionForRuntime(f as RuntimeFieldDefinition),
  )
  const pendingPk = normalizedFields.filter(field => {
    if (!pkNeedsAllocation(field)) return false
    if (
      options.bindingLinkMode === 'miParticipantRow'
      && legacyFkIsRowPk
      && field.fieldName === legacyFk
      && miParticipantSeedPresent
    ) {
      return false
    }
    const existing = row[field.fieldName]
    return existing == null || String(existing).trim() === ''
  })
  if (pendingPk.length === 0) return row

  const allocated = await Promise.all(
    pendingPk.map(async field => {
      const values = await allocatePrimaryKeys({
        tableId: Number(tableId),
        fieldName: field.fieldName,
        scopeKey: functionUnitId,
      })
      return { fieldName: field.fieldName, value: values?.[0] }
    }),
  )
  for (const { fieldName, value } of allocated) {
    if (value != null) {
      row = { ...row, [fieldName]: value }
    }
  }
  return row
}

async function allocateAutoPrimaryKeysForRow(
  fieldDefinitions: BindingFieldDefinition[],
  tableId: number,
  row: Record<string, unknown>,
  allocatePrimaryKeys: AllocatePrimaryKeysFn,
  functionUnitId?: string,
): Promise<Record<string, unknown>> {
  const next = { ...row }
  const pending: Array<{ fieldName: string }> = []
  for (const field of fieldDefinitions) {
    if (!pkNeedsAllocation(field)) continue
    const existing = next[field.fieldName]
    if (existing != null && String(existing).trim() !== '') continue
    pending.push({ fieldName: field.fieldName })
  }
  if (pending.length === 0) return next

  const allocated = await Promise.all(
    pending.map(async ({ fieldName }) => {
      const values = await allocatePrimaryKeys({
        tableId,
        fieldName,
        scopeKey: functionUnitId,
      })
      return { fieldName, value: values?.[0] }
    }),
  )
  for (const { fieldName, value } of allocated) {
    if (value != null) {
      next[fieldName] = value
    }
  }
  return next
}

/** When child add is blocked by missing parent PK, allocate auto PKs on ancestor rows (process start / preview). */
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

/** Seed MI participant link (e.g. HMDC Attachment.row_id = transaction row_id) before PK allocate. */
export function applyMiParticipantRowSeedToInitialRow(
  row: Record<string, unknown>,
  options: {
    fieldDefinitions?: BindingFieldDefinition[]
    bindingLinkMode?: BindingLinkMode | string | null
    bindingForeignKeyField?: string | null
    primaryKeyFields?: string[] | null
    miParticipantRowId?: string | number | null
    miParentParticipantRow?: Record<string, unknown> | null
    miParentTableId?: number | null
  },
): Record<string, unknown> {
  if (options.bindingLinkMode !== 'miParticipantRow') return row
  const seedId = options.miParticipantRowId
  if (seedId == null || String(seedId).trim() === '') return row
  const parentRow =
    options.miParentParticipantRow && typeof options.miParentParticipantRow === 'object'
      ? options.miParentParticipantRow
      : ({ row_id: seedId } as Record<string, unknown>)
  let next = seedLinkChildForeignKeysFromParentRow(row, options.fieldDefinitions, {
    bindingForeignKeyField: options.bindingForeignKeyField,
    bindingLinkMode: options.bindingLinkMode,
    primaryKeyFields: options.primaryKeyFields,
    parentParticipantRow: parentRow,
    parentTableId: options.miParentTableId,
    legacyFkSeed: seedId,
  })
  const legacy = options.bindingForeignKeyField?.trim()
  if (
    legacy
    && bindingForeignKeyFieldIsRowPrimaryKey(legacy, {
      primaryKeyFields: options.primaryKeyFields,
      fieldDefinitions: options.fieldDefinitions,
    })
    && (next[legacy] == null || String(next[legacy]).trim() === '')
  ) {
    next = { ...next, [legacy]: seedId }
  }
  return next
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
  /**
   * When true, all PK allocation (parent + child) is deferred until Save.
   * Add dialog opens without allocating keys; FK fill uses existing parent values only.
   */
  deferPkAllocationUntilSave?: boolean
  /** @deprecated Use deferPkAllocationUntilSave */
  deferChildPkAllocationUntilSave?: boolean
  bindingLinkMode?: BindingLinkMode | string | null
  bindingForeignKeyField?: string | null
  primaryKeyFields?: string[] | null
  miParticipantRowId?: string | number | null
  miParentParticipantRow?: Record<string, unknown> | null
  miParentTableId?: number | null
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

  const deferPkUntilSave =
    options.deferPkAllocationUntilSave === true
    || options.deferChildPkAllocationUntilSave === true

  if (requireFkGuard && fkMetas.length > 0 && !deferPkUntilSave) {
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
  row = applyMiParticipantRowSeedToInitialRow(row, {
    fieldDefinitions,
    bindingLinkMode: options.bindingLinkMode,
    bindingForeignKeyField: options.bindingForeignKeyField,
    primaryKeyFields: options.primaryKeyFields,
    miParticipantRowId: options.miParticipantRowId,
    miParentParticipantRow: options.miParentParticipantRow,
    miParentTableId: options.miParentTableId,
  })

  if (tableId != null && allocatePrimaryKeys && !deferPkUntilSave) {
    row = await allocateChildRowAutoPrimaryKeys({
      row,
      fieldDefinitions,
      tableId: Number(tableId),
      allocatePrimaryKeys,
      functionUnitId: options.functionUnitId ?? undefined,
      bindingLinkMode: options.bindingLinkMode,
      bindingForeignKeyField: options.bindingForeignKeyField,
      primaryKeyFields: options.primaryKeyFields,
      miParticipantRowId: options.miParticipantRowId,
    })
  }

  return {
    ok: true,
    initialRow: row,
    dialogColumns: visibleColumns,
    ...(primaryFormDataPatch ? { primaryFormDataPatch } : {}),
  }
}

/**
 * Save-time orchestration: ensure parent auto-PKs → fill structural FKs → allocate child auto-PKs.
 * Parent keys are always allocated before child keys when the main row is still empty.
 */
export async function finalizeSubTableRowOnSave(options: {
  row: Record<string, unknown>
  fieldDefinitions?: BindingFieldDefinition[]
  rowAddContext: RowAddContext
  tableId: number
  allocatePrimaryKeys: AllocatePrimaryKeysFn
  functionUnitId?: string
  parentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  primaryTableId?: number | null
  primaryTableDisplayName?: string
  tableDisplayName?: string
  autoEnsurePrimaryRecord?: boolean
  bindingLinkMode?: BindingLinkMode | string | null
  bindingForeignKeyField?: string | null
  primaryKeyFields?: string[] | null
  miParticipantRowId?: string | number | null
  miParentParticipantRow?: Record<string, unknown> | null
  miParentTableId?: number | null
  t?: (key: string, params?: Record<string, unknown>) => string
}): Promise<
  | { ok: true; row: Record<string, unknown>; primaryFormDataPatch?: Record<string, unknown> }
  | { ok: false; message: string }
> {
  const {
    row: inputRow,
    fieldDefinitions = [],
    allocatePrimaryKeys,
    parentTablesById,
    autoEnsurePrimaryRecord = false,
    t = (k) => k,
  } = options

  let rowAddContext = options.rowAddContext
  const fkMetas = filterStructuralFkMetasForBinding(toFieldFkMetas(fieldDefinitions), {
    bindingLinkMode: options.bindingLinkMode,
    bindingForeignKeyField: options.bindingForeignKeyField,
  })

  let primaryFormDataPatch: Record<string, unknown> | undefined

  if (fkMetas.length > 0) {
    let missing = guardBeforeChildRowAdd(fkMetas, rowAddContext)
    if (
      missing.length > 0
      && autoEnsurePrimaryRecord
      && parentTablesById
      && Object.keys(parentTablesById).length > 0
    ) {
      const ensured = await ensureParentRowsForChildAdd({
        fkMetas,
        rowAddContext,
        parentTablesById,
        allocatePrimaryKeys,
        functionUnitId: options.functionUnitId,
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

  let row = applyFkToInitialRow({ ...inputRow }, fkMetas, rowAddContext)
  row = applyMiParticipantRowSeedToInitialRow(row, {
    fieldDefinitions,
    bindingLinkMode: options.bindingLinkMode,
    bindingForeignKeyField: options.bindingForeignKeyField,
    primaryKeyFields: options.primaryKeyFields,
    miParticipantRowId: options.miParticipantRowId,
    miParentParticipantRow: options.miParentParticipantRow,
    miParentTableId: options.miParentTableId,
  })

  row = await allocateChildRowAutoPrimaryKeys({
    row,
    fieldDefinitions,
    tableId: options.tableId,
    allocatePrimaryKeys,
    functionUnitId: options.functionUnitId,
    bindingLinkMode: options.bindingLinkMode,
    bindingForeignKeyField: options.bindingForeignKeyField,
    primaryKeyFields: options.primaryKeyFields,
    miParticipantRowId: options.miParticipantRowId,
  })

  return {
    ok: true,
    row,
    ...(primaryFormDataPatch ? { primaryFormDataPatch } : {}),
  }
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
