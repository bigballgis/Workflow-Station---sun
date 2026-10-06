import { describe, expect, it } from 'vitest'
import type { SlaConfig } from '@/api/functionUnit'
import { followRenamedSlaFields } from '@/utils/slaConfigFields'

const cfg: SlaConfig = { startDateSource: 'FIELD', startDateField: 'received', dueDateField: 'due' }

describe('followRenamedSlaFields', () => {
  it('follows a rename of the due and start fields by row identity', () => {
    const next = followRenamedSlaFields(cfg,
      [[1, 'received'], [2, 'due'], [3, 'title']],
      [[1, 'received_on'], [2, 'due_on'], [3, 'title']])
    expect(next).toEqual({ startDateSource: 'FIELD', startDateField: 'received_on', dueDateField: 'due_on' })
  })

  it('returns the same object when no mapped field changed', () => {
    const next = followRenamedSlaFields(cfg, [[1, 'received'], [3, 'title']], [[1, 'received'], [3, 'name']])
    expect(next).toBe(cfg)
  })

  it('does not guess when a mapped field was removed', () => {
    const next = followRenamedSlaFields(cfg, [[1, 'received'], [2, 'due']], [[1, 'received']])
    expect(next).toBe(cfg)
  })

  it('ignores rows without identity and a different table (all identities new)', () => {
    expect(followRenamedSlaFields(cfg, [[undefined, 'due']], [[undefined, 'other']])).toBe(cfg)
    expect(followRenamedSlaFields(cfg, [[1, 'due']], [[9, 'other']])).toBe(cfg)
  })

  it('passes a missing config through', () => {
    expect(followRenamedSlaFields(null, [[1, 'a']], [[1, 'b']])).toBeNull()
  })
})
