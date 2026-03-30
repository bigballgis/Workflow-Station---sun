import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { BusinessLogicEngine } from '../businessLogicEngine'
import type { FormBusinessLogicConfig } from '../formRendererHelpers'

// ─── Helpers ────────────────────────────────────────────────────────────────

/**
 * Build a minimal FormBusinessLogicConfig with a single field that has
 * a visibility control rule.
 */
function buildConfig(
  handle: boolean,
  conditionField: string,
  conditionValue: unknown,
): FormBusinessLogicConfig {
  return {
    rule: [
      { type: 'input', field: conditionField, title: 'Trigger' },
      {
        type: 'input',
        field: 'target',
        title: 'Target',
        control: [
          {
            handle,
            rule: [{ field: conditionField, value: conditionValue }],
          },
        ],
      },
    ],
    options: {},
    subForms: {},
  }
}

// ─── Property 1: evaluateVisibility handle semantic correctness ─────────────
// **Validates: Requirements 1.1, 1.2, 1.3, 1.4**

describe('Property 1: evaluateVisibility handle semantic correctness', () => {
  it('result === (handle ? allMatch : !allMatch) for any handle + condition combination', () => {
    const handleArb = fc.boolean()
    const conditionValueArb = fc.constantFrom('a', 'b', 'c', '1', '2')
    const actualValueArb = fc.constantFrom('a', 'b', 'c', '1', '2', 'other')

    fc.assert(
      fc.property(handleArb, conditionValueArb, actualValueArb, (handle, conditionValue, actualValue) => {
        const config = buildConfig(handle, 'trigger', conditionValue)
        const engine = new BusinessLogicEngine()
        engine.init(config)

        const formData = { trigger: actualValue, target: '' }
        // Trigger evaluation by changing the trigger field
        engine.onFieldChange('trigger', actualValue, formData)

        const visible = engine.isFieldVisible('target')
        // eslint-disable-next-line eqeqeq
        const allMatch = actualValue == conditionValue
        const expected = handle ? allMatch : !allMatch

        expect(visible).toBe(expected)
      }),
      { numRuns: 100 },
    )
  })
})

// ─── Unit Tests: evaluateVisibility handle=true/false × match/no-match ──────
// **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**

describe('evaluateVisibility handle unit tests — 4 combinations', () => {
  it('handle=true, condition matches → visible (true)', () => {
    const config = buildConfig(true, 'status', 'active')
    const engine = new BusinessLogicEngine()
    engine.init(config)

    const formData = { status: 'active', target: '' }
    engine.onFieldChange('status', 'active', formData)

    expect(engine.isFieldVisible('target')).toBe(true)
  })

  it('handle=true, condition does NOT match → hidden (false)', () => {
    const config = buildConfig(true, 'status', 'active')
    const engine = new BusinessLogicEngine()
    engine.init(config)

    const formData = { status: 'inactive', target: '' }
    engine.onFieldChange('status', 'inactive', formData)

    expect(engine.isFieldVisible('target')).toBe(false)
  })

  it('handle=false, condition matches → hidden (false)', () => {
    const config = buildConfig(false, 'status', 'active')
    const engine = new BusinessLogicEngine()
    engine.init(config)

    const formData = { status: 'active', target: '' }
    engine.onFieldChange('status', 'active', formData)

    expect(engine.isFieldVisible('target')).toBe(false)
  })

  it('handle=false, condition does NOT match → visible (true)', () => {
    const config = buildConfig(false, 'status', 'active')
    const engine = new BusinessLogicEngine()
    engine.init(config)

    const formData = { status: 'inactive', target: '' }
    engine.onFieldChange('status', 'inactive', formData)

    expect(engine.isFieldVisible('target')).toBe(true)
  })
})
