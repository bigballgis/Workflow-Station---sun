import { describe, expect, it } from 'vitest'
import { resolveRowStableId } from '../recordNoteFields'

describe('Record Note row identity', () => {
  it('uses an arbitrary declared primary key instead of a familiar alias', () => {
    expect(resolveRowStableId({ custom_pk: 12, id: 'wrong' }, ['custom_pk'])).toBe('12')
  })
  it('uses the persisted UUID for composite and keyless rows', () => {
    const row = { a: 12, b: 'x', platformRowUuid: 'stable', id: 'wrong' }
    expect(resolveRowStableId(row, ['a', 'b'])).toBe('stable')
    expect(resolveRowStableId(row, [])).toBe('stable')
  })
  it('does not manufacture identity from business aliases', () => {
    expect(resolveRowStableId({ id: 'some-id', rowId: 'some-row' }, [])).toBeNull()
    expect(resolveRowStableId({ platformRowUuid: 'uuid' }, ['missing_pk'])).toBeNull()
  })
})
