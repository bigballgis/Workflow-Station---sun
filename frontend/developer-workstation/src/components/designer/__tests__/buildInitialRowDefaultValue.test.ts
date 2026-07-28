import { describe, expect, it } from 'vitest'
import { buildInitialRow } from '../subTableAddDialogHelpers'

describe('buildInitialRow defaultValue', () => {
  it('prefers column.defaultValue over type defaults', () => {
    const row = buildInitialRow([
      { field: 'status', label: 'Status', type: 'text', defaultValue: 'OPEN' },
      { field: 'qty', label: 'Qty', type: 'number', defaultValue: 3 },
      { field: 'flags', label: 'Flags', type: 'checkbox', defaultValue: ['a'] },
    ])
    expect(row.status).toBe('OPEN')
    expect(row.qty).toBe(3)
    expect(row.flags).toEqual(['a'])
  })

  it('clones object defaultValue so dialog edits do not mutate column meta', () => {
    const def = { nested: 1 }
    const col = { field: 'meta', label: 'Meta', type: 'text' as const, defaultValue: def }
    const row = buildInitialRow([col])
    expect(row.meta).toEqual({ nested: 1 })
    expect(row.meta).not.toBe(def)
  })
})
