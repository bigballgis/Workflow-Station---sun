import { describe, expect, it } from 'vitest'
import {
  dropSubsumedSubTableRows,
  normalizeSubTableRowsForBinding,
  subTableVariablesIncludeMiRows,
} from '../shared'

describe('dropSubsumedSubTableRows', () => {
  it('removes a thin row that is a field subset of a fuller row (procurement RequestItems ghost row)', () => {
    const full = {
      item_name: '5',
      unit_price: 5,
      total_price: 5,
      description: '5',
      category: 'electronics',
    }
    const ghost = { description: '5' }
    expect(dropSubsumedSubTableRows([full, ghost])).toEqual([full])
  })

  it('keeps two rows when neither is a subset of the other', () => {
    const a = { item_name: 'A', description: '1' }
    const b = { item_name: 'B', description: '2' }
    expect(dropSubsumedSubTableRows([a, b])).toEqual([a, b])
  })

  it('drops vacuous rows in normalizeSubTableRowsForBinding', () => {
    const full = { item_name: 'x', description: 'y' }
    expect(normalizeSubTableRowsForBinding([{}, full, { description: 'y' }])).toEqual([full])
  })
})

describe('subTableVariablesIncludeMiRows', () => {
  it('returns false for plain initiator sub-table JSON', () => {
    expect(
      subTableVariablesIncludeMiRows({
        '12': [{ item_name: 'a', description: 'b' }],
        RequestItems: [{ item_name: 'a', description: 'b' }],
      }),
    ).toBe(false)
  })

  it('returns true when any slice row has MI dashboard columns', () => {
    expect(
      subTableVariablesIncludeMiRows({
        '64': [{ id: 1, assignee_user_id: 'u1', task_status: 'IN_PROGRESS' }],
      }),
    ).toBe(true)
  })
})
