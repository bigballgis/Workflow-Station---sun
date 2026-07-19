import { describe, expect, it } from 'vitest'
import { resolveFkDisplayAttribute } from '../mainTableViewFkDisplay'

describe('resolveFkDisplayAttribute', () => {
  const mainVars = {
    case_number: 'CASE-100',
    legal_hold: 'Yes',
    status: 'Open',
  }

  it('matches FK scalar to MAIN PK and returns attribute', () => {
    expect(resolveFkDisplayAttribute(mainVars, 'CASE-100', ['case_number'], 'legal_hold'))
      .toBe('Yes')
    expect(resolveFkDisplayAttribute(mainVars, 'CASE-100', ['case_number'], 'status'))
      .toBe('Open')
  })

  it('returns undefined when FK does not match', () => {
    expect(resolveFkDisplayAttribute(mainVars, 'CASE-999', ['case_number'], 'legal_hold'))
      .toBeUndefined()
  })

  it('falls back to id keys when PK meta empty', () => {
    expect(resolveFkDisplayAttribute(
      { id: 'abc', title: 'Doc' },
      'abc',
      [],
      'title',
    )).toBe('Doc')
  })
})
