import { describe, expect, it } from 'vitest'
import { extractFieldsRecursive } from '../formRendererHelpers/formRendererRuleParsing'

/**
 * Record Note deletion is opt-in: forms designed before the switch existed (and forms where
 * the designer left it off) must reach the portal with allowDelete false, so the panel hides
 * the Delete button instead of letting authors erase their own audit trail.
 */
describe('recordNote allowDelete extraction', () => {
  function recordNoteRule(props: Record<string, unknown>) {
    return [{ type: 'recordNote', props }] as unknown as Record<string, unknown>[]
  }

  it('defaults to false when the designer never set the switch (legacy forms)', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'TABLE' }))
    expect(field._recordNote?.allowDelete).toBe(false)
  })

  it('stays false when the switch is explicitly off', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'TABLE', allowDelete: false }))
    expect(field._recordNote?.allowDelete).toBe(false)
  })

  it('is enabled only by an explicit true', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'RECORD', allowDelete: true }))
    expect(field._recordNote?.allowDelete).toBe(true)
  })

  it('does not disturb the other note switches, which stay default-on', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'TABLE' }))
    expect(field._recordNote?.allowEditOwn).toBe(true)
    expect(field._recordNote?.allowAttachment).toBe(true)
  })
})
