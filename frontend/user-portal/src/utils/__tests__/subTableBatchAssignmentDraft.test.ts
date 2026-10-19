import { describe, expect, it } from 'vitest'
import { collectBatchAssignments, initialBatchAssignment, type BatchUniformAssignmentState } from '../subTableBatchAssignmentDraft'
import type { SubTableBatchCapability } from '../subTableBatchCapability'

const config = { allowUser: true, allowRole: true, assigneeField: 'handler_code', roleField: 'pool_code', buField: 'unit_code' }
const capability: SubTableBatchCapability = {
  batchControlId: 'grid', bindingId: '19', originalCrud: {}, assignmentConfig: config,
  capabilities: { allowBatchAdd: false, allowBatchEdit: true, allowBatchDelete: false, allowBatchAssign: true },
  batchForm: { schemaVersion: 1, rule: [{ type: 'miAssignment' }] },
}
const rows = [
  { line_key: 'L1', label: 'first', handler_code: 'user-old', pool_code: null, unit_code: null, hidden: 'keep' },
  { line_key: 'L2', label: 'second', handler_code: null, pool_code: 'pool-old', unit_code: 'unit-old', hidden: 'keep-2' },
]
function draft() {
  const baseline = Object.fromEntries(rows.map(row => [row.line_key, initialBatchAssignment(row, config)]))
  const states = structuredClone(baseline)
  const uniform: BatchUniformAssignmentState = { mode: 'none', assigneeId: '', businessUnitId: '', businessUnitCode: '', roleCode: '' }
  const selected = new Set<string>()
  const collect = (current = rows, caps = capability) => collectBatchAssignments(current, states, baseline, uniform, selected, caps,
    row => String(row.line_key), row => ({ line_key: row.line_key }))
  return { baseline, states, uniform, selected, collect }
}
describe('assignment draft collection', () => {
  it('does not declare untouched existing targets or write any values', () => {
    const { collect } = draft()
    expect(collect()).toEqual({ rows })
  })
  it('merges ordinary field drafts with only changed assignment targets, preserving hidden data', () => {
    const { states, collect } = draft()
    states.L1!.assigneeId = 'user-new'
    const result = collect([{ ...rows[0]!, label: 'edited' }, rows[1]!])
    expect(result.rows[0]).toEqual({ ...rows[0], label: 'edited', handler_code: 'user-new' })
    expect(result.rows[1]).toBe(rows[1])
    expect(result.change?.rowPatches).toHaveLength(1)
    expect(result.change?.rowPatches[0]?.assignment).toEqual({ mode: 'PERSON', assigneeId: 'user-new' })
    expect(rows[0]?.handler_code).toBe('user-old')
  })
  it('rejects explicitly cleared targets instead of silently omitting them', () => {
    const { states, collect } = draft()
    states.L1!.assigneeId = ''
    expect(() => collect()).toThrow('subTable.batchAssignmentRequired')
    expect(rows[0]?.handler_code).toBe('user-old')
  })
  it('uses a final uniform intent for selected rows only and clears inactive mapped fields', () => {
    const { states, uniform, selected, collect } = draft()
    states.L2!.roleCode = ''
    uniform.mode = 'person'; uniform.assigneeId = 'user-new'; selected.add('L2')
    expect(collect().rows).toEqual([rows[0], { ...rows[1], handler_code: 'user-new', pool_code: null, unit_code: null }])
  })
  it('refuses a role without the configured BU code, never treating a query ID as its code', () => {
    const { uniform, selected, collect } = draft()
    uniform.mode = 'role'; uniform.roleCode = 'pool-new'; uniform.businessUnitId = 'query-id'; selected.add('L1')
    expect(() => collect()).toThrow('subTable.batchAssignmentRequired')
  })
  it('refuses modes not enabled by the frozen contract', () => {
    const { states, collect } = draft()
    states.L1!.assigneeId = 'user-new'
    expect(() => collect(rows, { ...capability, assignmentConfig: { ...config, allowUser: false } })).toThrow('subTable.batchAssignmentRequired')
  })
  it('ignores removed rows but refuses a stale uniform selection with no remaining target', () => {
    const { states, uniform, selected, collect } = draft()
    states.L1!.assigneeId = ''
    expect(collect([rows[1]!]).change).toBeUndefined()
    uniform.mode = 'person'; uniform.assigneeId = 'user-new'; selected.add('L1')
    expect(() => collect([rows[1]!])).toThrow('subTable.batchAssignmentRequired')
  })
  it('initializes an empty role-only row in its allowed role mode', () => {
    expect(initialBatchAssignment({}, { ...config, allowUser: false }).mode).toBe('role')
  })
})
