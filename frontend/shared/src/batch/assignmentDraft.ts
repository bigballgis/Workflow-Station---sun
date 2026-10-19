import type { BatchAssignmentConfig as AssignmentConfig, BatchAssignmentCapability as SubTableBatchCapability, SubTableBatchChange } from './workspaceTypes'
import { applyBatchAssignment, type BatchAssignmentIntent } from './draft'

export type BatchAssignmentState = {
  mode: 'person' | 'role'
  assigneeId: string
  businessUnitId: string
  businessUnitCode: string
  roleCode: string
}

export type BatchUniformAssignmentState = Omit<BatchAssignmentState, 'mode'> & { mode: 'none' | 'person' | 'role' }

export function initialBatchAssignment(row: Record<string, unknown>, config: AssignmentConfig): BatchAssignmentState {
  return {
    mode: config.allowRole && (!config.allowUser || !!(config.roleField && row[config.roleField])) ? 'role' : 'person',
    assigneeId: config.assigneeField ? String(row[config.assigneeField] ?? '') : '',
    businessUnitId: '',
    businessUnitCode: config.buField ? String(row[config.buField] ?? '') : '',
    roleCode: config.roleField ? String(row[config.roleField] ?? '') : '',
  }
}

/** Directory query IDs are not persisted assignment values. */
export function assignmentStateChanged(state: BatchAssignmentState, baseline: BatchAssignmentState): boolean {
  return state.mode !== baseline.mode || state.assigneeId !== baseline.assigneeId
    || state.businessUnitCode !== baseline.businessUnitCode || state.roleCode !== baseline.roleCode
}

function requireIntent(state: BatchUniformAssignmentState, config: AssignmentConfig): BatchAssignmentIntent {
  if (state.mode === 'person' && config.allowUser && state.assigneeId.trim()) {
    return { mode: 'person', assigneeId: state.assigneeId }
  }
  if (state.mode === 'role' && config.allowRole && state.roleCode.trim()
    && (!config.buField || state.businessUnitCode.trim())) {
    return { mode: 'role', roleCode: state.roleCode, ...(config.buField ? { businessUnitCode: state.businessUnitCode } : {}) }
  }
  throw new Error('subTable.batchAssignmentRequired')
}

/** Collect without side effects: untouched targets are not re-submitted; invalid changes reject the whole draft. */
export function collectBatchAssignments(
  rows: Array<Record<string, unknown>>,
  states: Record<string, BatchAssignmentState>,
  baselines: Record<string, BatchAssignmentState>,
  uniform: BatchUniformAssignmentState,
  selected: ReadonlySet<string>,
  capability: SubTableBatchCapability,
  rowIdentity: (row: Record<string, unknown>) => string | null,
  rowScopeKey: (row: Record<string, unknown>) => Record<string, unknown> | null,
): { rows: Array<Record<string, unknown>>; change?: SubTableBatchChange } {
  const config = capability.assignmentConfig
  if (!config) throw new Error('subTable.batchAssignmentRequired')
  const liveIds = new Set(rows.map(rowIdentity))
  if (uniform.mode !== 'none' && ![...selected].some(key => liveIds.has(key))) throw new Error('subTable.batchAssignmentRequired')
  const uniformIntent = uniform.mode === 'none' ? null : requireIntent(uniform, config)
  const rowPatches: SubTableBatchChange['rowPatches'] = []
  const fields = [config.assigneeField, config.roleField, config.buField].filter((field): field is string => !!field)
  const next = rows.map(row => {
    const rowId = rowIdentity(row)
    if (!rowId || !states[rowId] || !baselines[rowId]) throw new Error('subTable.batchStaleDraft')
    const intent = uniformIntent && selected.has(rowId) ? uniformIntent
      : assignmentStateChanged(states[rowId]!, baselines[rowId]!) ? requireIntent(states[rowId]!, config) : null
    if (!intent) return row
    const assigned = applyBatchAssignment(row, config, intent)
    if (fields.every(field => Object.is(assigned[field], row[field]))) return row
    const rowKey = rowScopeKey(row)
    if (!rowKey) throw new Error('subTable.batchStaleDraft')
    rowPatches.push({ rowId, rowKey, fields, assignment: intent.mode === 'person'
      ? { mode: 'PERSON', assigneeId: intent.assigneeId }
      : { mode: 'ROLE', roleCode: intent.roleCode, ...(intent.businessUnitCode ? { businessUnitCode: intent.businessUnitCode } : {}) } })
    return assigned
  })
  return { rows: next, ...(rowPatches.length ? { change: {
    batchControlId: capability.batchControlId, bindingId: capability.bindingId, operation: 'ASSIGN' as const, rowPatches,
  } } : {}) }
}
