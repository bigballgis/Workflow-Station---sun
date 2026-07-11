import { describe, expect, it } from 'vitest'
import dayjs from 'dayjs'
import { useChangeHistoryFormatting } from '../useChangeHistoryFormatting'
const t = ((key: string, params?: Record<string, unknown>) => {
  if (key === 'changeHistory.batchChanges') return `${params?.count ?? 0} changes`
  return key
}) as never
describe('useChangeHistoryFormatting', () => {
  it('formats JSON object diffs as changed field list', () => {
    const { formatDisplayValue } = useChangeHistoryFormatting(t, dayjs)
    const value = JSON.stringify({
      row_id: 'ATM-DC-PW-TRANS-000010',
      card_number: '12',
      merchant_name: '23',
    })
    expect(formatDisplayValue(value)).toBe('card_number: 12; merchant_name: 23')
  })
  it('formats nested dropdown and uploaded file values compactly', () => {
    const { formatDisplayValue } = useChangeHistoryFormatting(t, dayjs)
    const value = JSON.stringify({
      incoming_channel: { row_id: 'd1', dropdown_name: 'Branch' },
      file: '/api/v1/upload/files/336fd6f4.jpg?originalName=lilong.JPG',
    })
    expect(formatDisplayValue(value)).toBe('incoming_channel: Branch; file: lilong.JPG')
  })
})