import { describe, expect, it } from 'vitest'
import {
  filterRoleIdsForAssigneeType,
  parseRoleIdsFromExt,
  sanitizePersistedRoleIds,
  serializeRoleIds,
} from '../assigneeRoleIds'

describe('assigneeRoleIds', () => {
  describe('parseRoleIdsFromExt', () => {
    it('parses comma-separated roleIds', () => {
      expect(parseRoleIdsFromExt({ roleIds: 'a, b ,c' })).toEqual(['a', 'b', 'c'])
    })

    it('falls back to legacy roleId when roleIds empty', () => {
      expect(parseRoleIdsFromExt({ roleId: 'role-1' })).toEqual(['role-1'])
      expect(parseRoleIdsFromExt({ roleIds: '', roleId: 'role-1' })).toEqual(['role-1'])
    })

    it('returns empty when no role ids', () => {
      expect(parseRoleIdsFromExt({})).toEqual([])
    })
  })

  describe('serializeRoleIds', () => {
    it('joins trimmed non-empty ids', () => {
      expect(serializeRoleIds(['a', ' b ', '', 'c'])).toBe('a,b,c')
    })
  })

  describe('filterRoleIdsForAssigneeType', () => {
    const eligible = new Set(['r1', 'r2'])
    const bounded = new Set(['r2', 'r3'])

    it('FIXED_BU_ROLE keeps only eligible roles for selected BU', () => {
      const out = filterRoleIdsForAssigneeType(
        ['r1', 'foreign', 'r2'],
        {
          assigneeType: 'FIXED_BU_ROLE',
          businessUnitId: 'bu-1',
          eligibleRoleIds: eligible,
          boundedRoleIds: bounded,
        },
      )
      expect(out).toEqual(['r1', 'r2'])
    })

    it('FIXED_BU_ROLE without BU does not filter to eligible set', () => {
      const out = filterRoleIdsForAssigneeType(
        ['r1', 'foreign'],
        {
          assigneeType: 'FIXED_BU_ROLE',
          businessUnitId: '',
          eligibleRoleIds: eligible,
          boundedRoleIds: bounded,
        },
      )
      expect(out).toEqual(['r1', 'foreign'])
    })

    it('INITIATOR_BU_ROLE keeps only bounded catalog roles', () => {
      const out = filterRoleIdsForAssigneeType(
        ['r2', 'foreign', 'r3'],
        {
          assigneeType: 'INITIATOR_BU_ROLE',
          eligibleRoleIds: eligible,
          boundedRoleIds: bounded,
        },
      )
      expect(out).toEqual(['r2', 'r3'])
    })

    it('HIERARCHY_ROLE passes through normalized ids', () => {
      const out = filterRoleIdsForAssigneeType(
        ['  x ', 'y'],
        {
          assigneeType: 'HIERARCHY_ROLE',
          eligibleRoleIds: eligible,
          boundedRoleIds: bounded,
        },
      )
      expect(out).toEqual(['x', 'y'])
    })
  })

  describe('sanitizePersistedRoleIds', () => {
    it('returns null when not multi-select type', () => {
      expect(
        sanitizePersistedRoleIds(
          ['a', 'b'],
          {
            assigneeType: 'HIERARCHY_ROLE',
            eligibleRoleIds: [],
            boundedRoleIds: [],
          },
          { needsMultiRoleSelect: false },
        ),
      ).toBeNull()
    })

    it('returns null when BU-scoped type has no BU yet', () => {
      expect(
        sanitizePersistedRoleIds(
          ['a', 'foreign'],
          {
            assigneeType: 'FIXED_BU_ROLE',
            eligibleRoleIds: ['a'],
            boundedRoleIds: [],
          },
          { needsMultiRoleSelect: true },
        ),
      ).toBeNull()
    })

    it('returns sanitized list when foreign ids present', () => {
      expect(
        sanitizePersistedRoleIds(
          ['r1', 'foreign'],
          {
            assigneeType: 'FIXED_BU_ROLE',
            businessUnitId: 'bu-1',
            eligibleRoleIds: ['r1'],
            boundedRoleIds: [],
          },
          { needsMultiRoleSelect: true },
        ),
      ).toEqual(['r1'])
    })

    it('returns null when all ids already valid', () => {
      expect(
        sanitizePersistedRoleIds(
          ['r1'],
          {
            assigneeType: 'INITIATOR_BU_ROLE',
            eligibleRoleIds: [],
            boundedRoleIds: ['r1'],
          },
          { needsMultiRoleSelect: true },
        ),
      ).toBeNull()
    })
  })
})
