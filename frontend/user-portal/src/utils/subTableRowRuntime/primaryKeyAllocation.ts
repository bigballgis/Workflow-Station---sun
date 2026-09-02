/**
 * Sub-table row runtime — auto primary-key allocation (FK/PK hot path).
 * Parity with developer-workstation Form Preview runtime.
 */
import {
  type FieldFkMeta,
  type RowAddContext,
} from '../tableFkRuntime'
import { normalizeFieldDefinitionForRuntime, type RuntimeFieldDefinition } from '../formFieldMeta'
import { pkNeedsAllocation } from './pkPredicates'
import {
  type AllocatePrimaryKeysFn,
  type BindingFieldDefinition,
  type BindingLinkMode,
  bindingForeignKeyFieldIsRowPrimaryKey,
} from './types'

/**
 * Clear link-child row PK values wrongly copied from the parent MI participant key
 * (e.g. People.id), while never stripping the MI collection's own PK.
 *
 * @param parentPrimaryKeyFields 父（MI collection）表在设计器里配置的主键列名。该表自己的
 *   主键必须等于父展开键，不能当成「误copy」删掉。此前这里写死 `'id_idw'`：主键叫别的名字
 *   （实测 ATM_Transaction 是 `row_id`）时守卫失效，会把 collection 自己的主键删掉。
 */
export function repairMisassignedPrimaryKeyFromParentId(
  row: Record<string, unknown>,
  fieldDefinitions: BindingFieldDefinition[] | undefined | null,
  parentIdIdw: string | number | null | undefined,
  parentPrimaryKeyFields?: string[] | null,
): Record<string, unknown> {
  if (parentIdIdw == null || String(parentIdIdw).trim() === '') return row
  const parentKey = String(parentIdIdw).trim()
  // 父表主键**不猜**（不写死 'id_idw'）：猜错时保护失效，会把 MI collection 自己的主键
  // 当成「误copy」删掉。
  // 解析不出来时**什么都不删**（直接返回原行）—— 这个函数是删值的，无从判断该保护谁时
  // 保持原样是安全的一侧；抛错则会中断整个 Save。
  const parentPks = (parentPrimaryKeyFields ?? []).map(f => String(f ?? '').trim()).filter(Boolean)
  if (parentPks.length === 0) return row
  const protectedPks = new Set(parentPks.map(n => n.toLowerCase()))
  const out = { ...row }
  for (const def of fieldDefinitions ?? []) {
    if (!def.isPrimaryKey) continue
    const name = def.fieldName
    /** MI collection PK — must equal parent expansion key; never strip. */
    if (protectedPks.has(String(name).trim().toLowerCase())) continue
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
    if (!pkNeedsAllocation(field as BindingFieldDefinition)) return false
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
