import { describe, expect, it } from 'vitest'
import { resolveOwnedSendTaskConnectionUid } from '../resolveOwnedSendTaskConnectionUid'

describe('resolveOwnedSendTaskConnectionUid', () => {
  const owned = { connectionUid: 'new-uid' }

  it('keeps a uid that belongs to this Function Unit', () => {
    expect(resolveOwnedSendTaskConnectionUid('new-uid', [owned])).toBe('new-uid')
  })

  it('keeps the stored uid until connections have loaded', () => {
    expect(resolveOwnedSendTaskConnectionUid('source-uid', [])).toBe('source-uid')
  })

  it('binds the sole outbound connection when the stored uid is from the source FU', () => {
    expect(resolveOwnedSendTaskConnectionUid('source-uid', [owned])).toBe('new-uid')
  })

  it('clears an unmatched uid when several connections exist', () => {
    const next = resolveOwnedSendTaskConnectionUid('source-uid', [
      owned,
      { connectionUid: 'other-uid' }
    ])
    expect(next).toBe('')
  })
})
