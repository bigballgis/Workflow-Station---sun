import { describe, expect, it } from 'vitest'
import { mergeSubTableRowsByRowId } from '../shared'

describe('mergeSubTableRowsByRowId MI dashboard columns', () => {
  it('does not let later IN_PROGRESS overwrite COMPLETED for the same PK row', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ id: 2323, task_status: 'COMPLETED', task_current_node: 'end' }],
      [{ id: 2323, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
      ['id'],
    )
    expect(merged).toHaveLength(1)
    expect(merged[0].task_status).toBe('COMPLETED')
    expect(merged[0].task_current_node).toBe('end')
  })

  it('upgrades IN_PROGRESS to COMPLETED when a later slice completes', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ id: 1, task_status: 'IN_PROGRESS', task_current_node: 'sub form1' }],
      [{ id: 1, task_status: 'COMPLETED', task_current_node: 'end' }],
      ['id'],
    )
    expect(merged[0].task_status).toBe('COMPLETED')
    expect(String(merged[0].task_current_node).toLowerCase()).toBe('end')
  })

  it('for same status rank prefers the incoming node label', () => {
    const merged = mergeSubTableRowsByRowId(
      [{ id: 1, task_status: 'IN_PROGRESS', task_current_node: 'a' }],
      [{ id: 1, task_status: 'IN_PROGRESS', task_current_node: 'b' }],
      ['id'],
    )
    expect(merged[0].task_status).toBe('IN_PROGRESS')
    expect(merged[0].task_current_node).toBe('b')
  })
})
