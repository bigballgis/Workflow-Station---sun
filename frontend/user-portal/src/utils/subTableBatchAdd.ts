import { finalizeSubTableRowOnSave, type AllocatePrimaryKeysFn } from './subTableRowRuntime'
import { pkNeedsAllocation } from './subTableRowRuntime/pkPredicates'
import { ensureRowIdentity } from './subTableRowIdentity'
import { replaceAncestorRow } from './tableFkRuntime'
import { rowKeyForScope } from '@/composables/tasks/subTableCanonicalStamp'

type FinalizeOptions = Parameters<typeof finalizeSubTableRowOnSave>[0]
export type BatchAddOptions = Omit<FinalizeOptions, 'row'> & { rows: Array<Record<string, unknown>> }
export interface PreparedBatchRows {
  rows: Array<Record<string, unknown>>
  primaryFormDataPatch?: Record<string, unknown>
  parentRowPatch?: Record<string, unknown>
}

/** Reserves each child PK field in one allocation request; no business rows are written. */
async function reserveKeys(options: BatchAddOptions): Promise<Map<string, string[]>> {
  const queues = new Map<string, string[]>()
  for (const field of options.fieldDefinitions ?? []) {
    if (!pkNeedsAllocation(field)) continue
    const count = options.rows.filter(row => row[field.fieldName] == null || row[field.fieldName] === '').length
    if (!count) continue
    const values = await options.allocatePrimaryKeys({ tableId: options.tableId, fieldName: field.fieldName, count })
    if (values.length !== count) throw new Error('Batch primary-key allocation returned an incomplete result')
    queues.set(field.fieldName, [...values])
  }
  return queues
}

/** All prepared rows and parent patches remain private until the entire batch succeeds. */
export async function prepareBatchAdd(options: BatchAddOptions): Promise<PreparedBatchRows> {
  if (!options.rows.length || options.rows.length > 200) throw new Error('Batch add requires between 1 and 200 rows')
  if (options.bindingLinkMode === 'miParticipantRow') throw new Error('Batch add cannot change MI participant membership')
  validateManualKeys(options)
  // Form data is a JSON contract and may arrive through Vue reactive proxies.
  let context = JSON.parse(JSON.stringify(options.rowAddContext)) as FinalizeOptions['rowAddContext']
  const result: PreparedBatchRows = { rows: [] }
  let queues: Map<string, string[]> | undefined
  const allocate: AllocatePrimaryKeysFn = async payload => {
    if (payload.tableId !== options.tableId) return options.allocatePrimaryKeys(payload)
    queues ??= await reserveKeys(options)
    const value = queues.get(payload.fieldName)?.shift()
    if (value == null) throw new Error('Batch primary-key allocation has no reserved value')
    return [value]
  }
  for (const row of options.rows) {
    const prepared = await finalizeSubTableRowOnSave({ ...options, row: { ...row }, rowAddContext: context, allocatePrimaryKeys: allocate })
    if (!prepared.ok) throw new Error(prepared.message)
    ensureRowIdentity(prepared.row, options.primaryKeyFields)
    result.rows.push(prepared.row)
    if (prepared.primaryFormDataPatch) {
      result.primaryFormDataPatch = prepared.primaryFormDataPatch
      context = { ...context, primaryFormData: prepared.primaryFormDataPatch }
      if (options.primaryTableId != null) context = replaceAncestorRow(context, options.primaryTableId, prepared.primaryFormDataPatch)
    }
    if (prepared.parentRowPatch) {
      result.parentRowPatch = prepared.parentRowPatch
      if (options.parentTableId != null) context = replaceAncestorRow(context, options.parentTableId, prepared.parentRowPatch)
    }
  }
  return result
}

function validateManualKeys(options: BatchAddOptions): void {
  const fields = (options.fieldDefinitions ?? []).filter(field => field.isPrimaryKey && !pkNeedsAllocation(field))
  for (const row of options.rows) {
    for (const field of fields) {
      if (row[field.fieldName] == null || row[field.fieldName] === '') throw new Error(`Primary key ${field.fieldName} is required`)
    }
  }
  if (!fields.length || fields.length !== options.primaryKeyFields?.length) return
  const keys = options.rows.map(row => JSON.stringify(rowKeyForScope(row, options.primaryKeyFields)))
  if (new Set(keys).size !== keys.length) throw new Error('Batch add contains duplicate manual primary keys')
}
