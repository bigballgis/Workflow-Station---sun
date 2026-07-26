import { describe, expect, it } from 'vitest'
import { getHistoryAction, getHistoryStatus } from '../subTableRowHelpers'

describe('getHistoryAction / getHistoryStatus for SEND', () => {
  it('maps SEND to send action', () => {
    expect(getHistoryAction('SEND')).toBe('send')
  })

  it('maps SEND to completed status', () => {
    expect(getHistoryStatus('SEND')).toBe('completed')
  })
})
