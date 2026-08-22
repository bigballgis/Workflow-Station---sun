import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import {
  ownerChips,
  parseOwnerSource,
  parseStoredUserIds,
} from '../useOwnerFieldModel'

describe('parseStoredUserIds', () => {
  it('parses one and many user: tokens', () => {
    expect(parseStoredUserIds('user:u-a,user:u-b')).toEqual(['u-a', 'u-b'])
    expect(parseStoredUserIds('user:u-bob')).toEqual(['u-bob'])
    expect(parseStoredUserIds('group:bu|role')).toEqual([])
    expect(parseStoredUserIds('')).toEqual([])
  })
})

describe('ownerChips', () => {
  it('splits an unclaimed pool into one chip per stored user', () => {
    expect(ownerChips('user:u-a,user:u-b', 'Ann, Ben')).toEqual([
      { kind: 'user', label: 'Ann' },
      { kind: 'user', label: 'Ben' },
    ])
  })

  it('uses the stored id when display is missing', () => {
    expect(ownerChips('user:u-bob', undefined)).toEqual([
      { kind: 'user', label: 'u-bob' },
    ])
  })

  it('falls back to comma-separated display when the main value is empty', () => {
    expect(ownerChips('', 'Ann, Ben')).toEqual([
      { kind: 'user', label: 'Ann' },
      { kind: 'user', label: 'Ben' },
    ])
  })
})

describe('parseOwnerSource', () => {
  it('reads CURRENT_ASSIGNEE from ownerConfig', () => {
    const configError = ref(false)
    expect(parseOwnerSource('{"source":"CURRENT_ASSIGNEE"}', configError)).toBe('CURRENT_ASSIGNEE')
    expect(configError.value).toBe(false)
  })
})
