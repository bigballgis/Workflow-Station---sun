import { describe, expect, it } from 'vitest'
import {
  collectSubTableBindingMatchKeys,
  subTableRowsLackSavedFieldPayload,
} from '../subTableRowHelpers'
import {
  collectSubTableBindingMatchKeys as collectTaskDetailMatchKeys,
} from '../../taskDetail/subTableRowUtils'

/**
 * Regression: HMDC Attachment (columns: file + 4 audit fields) must NOT fuzzy-claim an
 * HMDC Transaction slice just because both carry created_at/created_by/updated_at/updated_by —
 * Table Design auto-appends those to every table, so they are non-discriminative.
 */
describe('collectSubTableBindingMatchKeys excludes system audit fields', () => {
  // Real HMDC Attachment shape: file + FK to main (case_id) + runtime row key + audit columns.
  const binding = {
    foreignKeyField: 'case_id',
    columns: [
      { field: 'file' },
      { field: 'case_id' },
      { field: 'row_id' },
      { field: 'created_at' },
      { field: 'created_by' },
      { field: 'updated_at' },
      { field: 'updated_by' },
    ],
  }

  it('applicationDetail: only business fields remain', () => {
    expect([...collectSubTableBindingMatchKeys(binding)]).toEqual(['file'])
  })

  it('taskDetail parity: only business fields remain', () => {
    expect([...collectTaskDetailMatchKeys(binding)]).toEqual(['file'])
  })

  it('a foreign slice with only audit-key overlap scores zero against the match keys', () => {
    const fieldKeys = collectSubTableBindingMatchKeys(binding)
    const transactionRow = {
      row_id: 'x',
      case_id: '6107f719',
      card_number: '88',
      created_at: '2026-07-03 18:34:25',
      created_by: 'Developer Tester',
      updated_at: '2026-07-03 18:34:25',
      updated_by: 'Developer Tester',
    }
    const rowKeysLower = new Set(Object.keys(transactionRow).map(k => k.toLowerCase()))
    let score = 0
    for (const k of fieldKeys) {
      if (rowKeysLower.has(k.toLowerCase())) score++
    }
    expect(score).toBe(0)
  })

  it('rows carrying only audit values count as lacking saved payload', () => {
    const fieldKeys = collectSubTableBindingMatchKeys(binding)
    const rows = [{ created_at: '2026-07-03 18:34:25', created_by: 'Developer Tester' }]
    expect(subTableRowsLackSavedFieldPayload(rows, fieldKeys)).toBe(true)
    expect(subTableRowsLackSavedFieldPayload([{ file: 'https://x/y.pdf' }], fieldKeys)).toBe(false)
  })
})
