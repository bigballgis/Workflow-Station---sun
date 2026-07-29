import { describe, it, expect } from 'vitest'
import {
  promote,
  parseStored,
  serialize,
  mergeMetadata,
  MAX_RECENT,
  type RecentFunctionUnit,
} from '../useRecentFunctionUnits'

function entry(id: number, overrides: Partial<RecentFunctionUnit> = {}): RecentFunctionUnit {
  return { id, name: `FU ${id}`, iconId: null, status: 'DRAFT', visitedAt: id, ...overrides }
}

describe('useRecentFunctionUnits — recent list rules', () => {
  describe('promote', () => {
    it('puts the visited function unit first', () => {
      const result = promote([entry(1), entry(2)], entry(3))
      expect(result.map(e => e.id)).toEqual([3, 1, 2])
    })

    it('moves an already-listed function unit to the front instead of duplicating it', () => {
      const result = promote([entry(1), entry(2), entry(3)], entry(2, { visitedAt: 99 }))
      expect(result.map(e => e.id)).toEqual([2, 1, 3])
      expect(result[0]!.visitedAt).toBe(99)
    })

    it('caps the list at MAX_RECENT, dropping the oldest', () => {
      const full = Array.from({ length: MAX_RECENT }, (_, i) => entry(i + 1))
      const result = promote(full, entry(100))
      expect(result).toHaveLength(MAX_RECENT)
      expect(result[0]!.id).toBe(100)
      expect(result.map(e => e.id)).not.toContain(MAX_RECENT)
    })
  })

  describe('parseStored', () => {
    it('round-trips what serialize writes', () => {
      const list = [entry(1), entry(2)]
      expect(parseStored(serialize(list))).toEqual(list)
    })

    it('returns an empty list for null, malformed JSON, and a stale schema version', () => {
      expect(parseStored(null)).toEqual([])
      expect(parseStored('not json')).toEqual([])
      expect(parseStored(JSON.stringify({ version: 999, items: [entry(1)] }))).toEqual([])
      expect(parseStored(JSON.stringify({ version: 1, items: 'nope' }))).toEqual([])
    })

    it('drops entries missing the fields the sidebar renders', () => {
      const raw = JSON.stringify({ version: 1, items: [entry(1), { id: 'x', name: 'bad' }, { name: 'no id' }] })
      expect(parseStored(raw).map(e => e.id)).toEqual([1])
    })
  })

  describe('mergeMetadata', () => {
    it('refreshes name, icon and status from the freshly loaded list', () => {
      const result = mergeMetadata(
        [entry(1, { name: 'Old name', status: 'DRAFT' })],
        [{ id: 1, name: 'New name', iconId: 7, status: 'PUBLISHED' }]
      )
      expect(result[0]).toMatchObject({ name: 'New name', iconId: 7, status: 'PUBLISHED' })
    })

    it('keeps the visit order and timestamp untouched', () => {
      const result = mergeMetadata(
        [entry(2, { visitedAt: 20 }), entry(1, { visitedAt: 10 })],
        [{ id: 1, name: 'One', status: 'DRAFT' }, { id: 2, name: 'Two', status: 'DRAFT' }]
      )
      expect(result.map(e => e.id)).toEqual([2, 1])
      expect(result.map(e => e.visitedAt)).toEqual([20, 10])
    })

    it('keeps entries the list did not return — filters and team scope hide function units that still exist', () => {
      const result = mergeMetadata([entry(1), entry(2)], [{ id: 1, name: 'One', status: 'DRAFT' }])
      expect(result.map(e => e.id)).toEqual([1, 2])
    })

    it('leaves the list alone when the source is empty', () => {
      const list = [entry(1), entry(2)]
      expect(mergeMetadata(list, [])).toEqual(list)
    })
  })
})
