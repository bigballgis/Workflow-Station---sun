import { describe, it, expect } from 'vitest'
import { seedTaskFormFromProcessValues } from '../seedTaskFormFromProcessValues'

describe('seedTaskFormFromProcessValues', () => {
  it('seeds empty lookup fields from process form values including multi rows', () => {
    const rows = [
      { status_id: '4', status_name: 'A llll' },
      { status_id: '5', status_name: 'A d dddd' },
    ]
    const { next, patched } = seedTaskFormFromProcessValues(
      { stage: null, test_status: null, Host: null },
      {
        stage: { id: 'CAST-1', code: 'A' },
        test_status: rows,
        I: 'meeting',
      },
      ['stage', 'test_status', 'Host', 'I'],
    )
    expect(patched).toBe(true)
    expect(next.stage).toEqual({ id: 'CAST-1', code: 'A' })
    expect(next.test_status).toEqual(rows)
    expect(next.I).toBe('meeting')
    expect(next.Host).toBeNull()
  })

  it('does not overwrite non-empty task form values', () => {
    const { next, patched } = seedTaskFormFromProcessValues(
      { stage: { id: 'keep' }, test_status: [{ status_id: '1' }] },
      { stage: { id: 'proc' }, test_status: [{ status_id: '9' }] },
      ['stage', 'test_status'],
    )
    expect(patched).toBe(false)
    expect(next.stage).toEqual({ id: 'keep' })
  })

  it('re-seeds after MI isolate wiped lookup slots to null/empty array', () => {
    const rows = [{ status_id: '4', status_name: 'A llll' }]
    const { next, patched } = seedTaskFormFromProcessValues(
      { stage: null, test_status: [] },
      { stage: { id: 'CAST-1', code: 'A' }, test_status: rows },
      ['stage', 'test_status'],
    )
    expect(patched).toBe(true)
    expect(next.stage).toEqual({ id: 'CAST-1', code: 'A' })
    expect(next.test_status).toEqual(rows)
  })
})
