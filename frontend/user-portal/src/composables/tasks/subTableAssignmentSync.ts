import { getCurrentInstance, inject, provide, type InjectionKey, type Ref } from 'vue'
import type { AssignSubTableRowResponse } from '@/api/task'
import { getSavedSubTableRows } from './subTableSliceResolve'
import { subTableStoreKey } from './subTableStore'

type Row = Record<string, unknown>
type Binding = { bindingId: number; tableName: string; primaryKeyFields?: string[] | null; data?: unknown }
type Request = () => Promise<AssignSubTableRowResponse | null>
type AssignmentSync = (task: string, row: Row, request: Request) => Promise<AssignSubTableRowResponse | null>
const KEY: InjectionKey<AssignmentSync> = Symbol('task-sub-table-assignment-sync')
export class AssignmentReceiptConflict extends Error {}

export function useSubTableAssignmentSync(): AssignmentSync {
  const sync = getCurrentInstance() ? inject(KEY, null) : null
  return sync ?? (async (_task, _row, request) => {
    const result = await request()
    if (result?.rowVersion !== undefined) throw new AssignmentReceiptConflict()
    return result
  })
}

export function provideSubTableAssignmentSync(deps: {
  effectiveTaskId: Ref<string>; subTableBindings: Ref<Binding[]>; formData: Ref<Row>
  loadedSubTableBaseline: Ref<Row>; pauseSubTableAutosave: () => Promise<void>
  resumeSubTableAutosave: () => void; subTableSaveError: Ref<unknown>; assignmentTracked?: Ref<boolean>
}): void {
  if (!getCurrentInstance()) return
  let running = false
  provide(KEY, async (task, row, request) => {
    if (running) throw new Error('Assignment already in progress')
    running = true
    let conflict = false
    try {
      await deps.pauseSubTableAutosave()
      if (task !== deps.effectiveTaskId.value) throw new Error('Task changed before assignment')
      const captured = { ...row }
      const result = await request()
      if (result?.success !== false && result?.assigneeId && (deps.assignmentTracked?.value
        || result.rowVersion !== undefined || result.previousRowVersion !== undefined)) {
        if (task !== deps.effectiveTaskId.value) throw new AssignmentReceiptConflict()
        applyAssignmentReceipt(result, row, captured, deps)
      }
      return result
    } catch (error) {
      if (error instanceof AssignmentReceiptConflict && task === deps.effectiveTaskId.value) {
        conflict = true; deps.subTableSaveError.value = error
      }
      throw error
    } finally { running = false; if (!conflict) deps.resumeSubTableAutosave() }
  })
}

/** Validate every affected copy before changing any version; other rows stay untouched. */
function applyAssignmentReceipt(result: AssignSubTableRowResponse, live: Row, captured: Row, deps: {
  subTableBindings: Ref<Binding[]>; formData: Ref<Row>; loadedSubTableBaseline: Ref<Row>
}): void {
  const { rowVersion: next, previousRowVersion: previous, rowKey: key, storeKey, assigneeField: field } = result
  if (!Number.isInteger(next) || next! < 1 || !(previous === null || Number.isInteger(previous) && previous! >= 0)
    || next !== (previous ?? 0) + 1 || !key || !storeKey || !field) throw new AssignmentReceiptConflict()
  const bindings = deps.subTableBindings.value.filter(binding => subTableStoreKey(binding) === storeKey)
  const pk = bindings[0]?.primaryKeyFields
  if (!pk?.length || Object.keys(key).length !== pk.length || !pk.every(name => validKeyValue(key[name]))
    || !sameKey(captured, key) || !sameKey(live, key)) throw new AssignmentReceiptConflict()
  const baseline = getSavedSubTableRows(deps.loadedSubTableBaseline.value, bindings[0]!)
  const saved = Array.isArray(baseline) ? baseline.filter(row => sameKey(row, key)) : []
  if (saved.length !== 1 || !sameVersion(saved[0], previous) || !sameVersion(captured, previous)) throw new AssignmentReceiptConflict()
  const targets = new Set<Row>([live])
  for (const binding of bindings) collectRows(binding.data, key, storeKey, targets)
  for (const map of [deps.formData.value.__subTables__, deps.loadedSubTableBaseline.value]) {
    if (!isRow(map)) continue
    for (const binding of bindings) collectRows(getSavedSubTableRows(map, binding), key, storeKey, targets)
    for (const rows of Object.values(map)) collectNested(rows, key, storeKey, targets, 0)
  }
  if ([...targets].some(row => !sameVersion(row, previous))) throw new AssignmentReceiptConflict()
  for (const row of targets) { row._wsRowVersion = next; row[field] = result.assigneeId }
}

function sameVersion(row: Row, previous: number | null | undefined) {
  return previous === null ? row._wsRowVersion == null : row._wsRowVersion === previous
}
function sameKey(row: unknown, key: Row): row is Row {
  return isRow(row) && Object.entries(key).every(([name, value]) => validKeyValue(row[name])
    && (typeof value === 'boolean' || typeof row[name] === 'boolean' ? row[name] === value : String(row[name]) === String(value)))
}
function validKeyValue(value: unknown): boolean {
  return typeof value === 'boolean' || typeof value === 'number' && Number.isFinite(value)
    || typeof value === 'string' && value.trim().length > 0
}
function isRow(value: unknown): value is Row {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}
function collectRows(rows: unknown, key: Row, store: string, targets: Set<Row>): void {
  if (!Array.isArray(rows)) return
  for (const row of rows) if (sameKey(row, key)) targets.add(row)
  collectNested(rows, key, store, targets, 0)
}
function collectNested(rows: unknown, key: Row, store: string, targets: Set<Row>, depth: number): void {
  if (!Array.isArray(rows) || depth > 8) return
  for (const row of rows) {
    const nested = isRow(row) ? row.__subTables__ : null
    if (!isRow(nested)) continue
    if (Array.isArray(nested[store])) for (const child of nested[store]) if (sameKey(child, key)) targets.add(child)
    for (const child of Object.values(nested)) collectNested(child, key, store, targets, depth + 1)
  }
}
