import { describe, expect, it } from 'vitest'
import { isValidFieldName, slugFieldName, suggestFieldName } from '../fieldNameSlug'

describe('fieldNameSlug', () => {
  it('slugFieldName converts display names', () => {
    expect(slugFieldName('Case Number')).toBe('case_number')
    expect(slugFieldName('Card Number 1')).toBe('card_number_1')
    expect(slugFieldName('')).toBe('field')
    expect(slugFieldName('123')).toBe('f_123')
  })

  it('suggestFieldName deduplicates within table', () => {
    expect(suggestFieldName('Case ID', ['case_id'])).toBe('case_id_2')
  })

  it('isValidFieldName enforces identifier rules', () => {
    expect(isValidFieldName('case_number')).toBe(true)
    expect(isValidFieldName('1bad')).toBe(false)
  })
})
