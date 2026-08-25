import { describe, expect, it } from 'vitest'
import {
  ensureRowIdentity,
  ensureSliceRowIdentities,
  ensureSubTableMapIdentities,
  readRowIdentityToken,
  rowHasIdentity,
} from '../subTableRowIdentity'

describe('subTableRowIdentity', () => {
  it('leaves rows that already have row_id untouched', () => {
    const row = { row_id: 'existing', channel: 'Email' }
    expect(ensureRowIdentity(row)).toBe(false)
    expect(row.row_id).toBe('existing')
  })

  it('prefers existing id over generating row_id', () => {
    const row = { id: 'pk-1', channel: 'Email' }
    expect(rowHasIdentity(row)).toBe(true)
    expect(ensureRowIdentity(row)).toBe(false)
    expect(row).not.toHaveProperty('row_id')
  })

  it('assigns distinct identities to anonymous rows in one slice', () => {
    const rows = [{ channel: 'Email' }, { channel: 'SMS' }] as Record<string, unknown>[]
    expect(ensureSliceRowIdentities(rows)).toBe(2)
    expect(rows[0]!.row_id).toBeTruthy()
    expect(rows[1]!.row_id).toBeTruthy()
    expect(rows[0]!.row_id).not.toBe(rows[1]!.row_id)
  })

  it('does not stamp a second uuid on name aliases when a numeric slice exists', () => {
    const canonical = { channel: 'Email' } as Record<string, unknown>
    const aliasCopy = { channel: 'Email' } as Record<string, unknown>
    expect(ensureSubTableMapIdentities({
      '1301': [canonical],
      'ACQ Correspondence': [aliasCopy],
    })).toBe(1)
    expect(canonical.row_id).toBeTruthy()
    expect(aliasCopy).not.toHaveProperty('row_id')
  })

  it('reads the first identity token in field-priority order', () => {
    expect(readRowIdentityToken({ row_id: 'r1', id: 'pk-9' })).toBe('r1')
    expect(readRowIdentityToken({ id: 'pk-9' })).toBe('pk-9')
    expect(readRowIdentityToken({ channel: 'Email' })).toBeNull()
  })
})
