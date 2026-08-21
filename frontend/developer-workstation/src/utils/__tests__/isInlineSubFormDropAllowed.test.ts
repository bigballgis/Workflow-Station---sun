import { describe, it, expect } from 'vitest'
import { isInlineSubFormDropAllowed } from '../formDesigner'

describe('isInlineSubFormDropAllowed', () => {
  it('allows inlineSubForm onto the main-form canvas', () => {
    expect(isInlineSubFormDropAllowed('inlineSubForm', 'main')).toBe(true)
  })

  it('rejects inlineSubForm onto a sub-binding designer canvas', () => {
    expect(isInlineSubFormDropAllowed('inlineSubForm', '50539')).toBe(false)
  })

  it('never restricts any other drag-rule type, regardless of active tab', () => {
    expect(isInlineSubFormDropAllowed('subTable', '50539')).toBe(true)
    expect(isInlineSubFormDropAllowed('input', '50539')).toBe(true)
    expect(isInlineSubFormDropAllowed(undefined, '50539')).toBe(true)
  })
})
