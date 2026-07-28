import { describe, expect, it } from 'vitest'
import { buildInitialRow } from '../rowInit'
import type { DialogColumn } from '../types'

describe('buildInitialRow defaultValue (portal)', () => {
  it('prefers column.defaultValue over type defaults', () => {
    const columns: DialogColumn[] = [
      { field: 'card_number', label: 'Card', type: 'text', defaultValue: 'X' },
      { field: 'amount', label: 'Amount', type: 'number', defaultValue: 10 },
    ]
    const row = buildInitialRow(columns)
    expect(row.card_number).toBe('X')
    expect(row.amount).toBe(10)
  })
})
