/**
 * Property 7: onFieldChange debounce merging
 * **Validates: Requirements 13.1, 13.2**
 *
 * Tests that N consecutive calls within 50ms result in at most 2 actual
 * evaluateAffectedRules executions (leading edge + trailing edge).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import * as fc from 'fast-check'
import { BusinessLogicEngine } from '../businessLogicEngine'
import type { FormBusinessLogicConfig } from '../formRendererHelpers'

describe('Property 7: onFieldChange debounce merging', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('N consecutive calls within 50ms result in at most 2 evaluateAffectedRules executions', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 20 }),
        (callCount) => {
          const engine = new BusinessLogicEngine()
          const config: FormBusinessLogicConfig = {
            rule: [],
            options: {},
            subForms: {},
            formulas: [
              { targetField: 'total', expression: 'a + b', dependsOn: ['a', 'b'] },
            ],
          }
          engine.init(config)

          // Spy on the private evaluateAffectedRules method
          let evalCount = 0
          const originalEval = (engine as any).evaluateAffectedRules.bind(engine)
          ;(engine as any).evaluateAffectedRules = (...args: any[]) => {
            evalCount++
            return originalEval(...args)
          }

          // Make N calls without advancing time (all within debounce window)
          for (let i = 0; i < callCount; i++) {
            engine.onFieldChange('a', i, { a: i, b: 10 })
          }

          // First call executes immediately (leading edge) = 1 execution
          // Subsequent calls are merged, not yet executed
          expect(evalCount).toBe(1)

          // Advance past debounce window to trigger trailing execution
          vi.advanceTimersByTime(60)

          // Should have at most 2 executions: leading + trailing
          expect(evalCount).toBeLessThanOrEqual(2)
          // With N >= 2, there should be exactly 2 (leading + trailing)
          expect(evalCount).toBe(2)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('single call results in exactly 1 evaluation (leading edge only)', () => {
    const engine = new BusinessLogicEngine()
    const config: FormBusinessLogicConfig = {
      rule: [],
      options: {},
      subForms: {},
      formulas: [
        { targetField: 'total', expression: 'a + b', dependsOn: ['a', 'b'] },
      ],
    }
    engine.init(config)

    let evalCount = 0
    const originalEval = (engine as any).evaluateAffectedRules.bind(engine)
    ;(engine as any).evaluateAffectedRules = (...args: any[]) => {
      evalCount++
      return originalEval(...args)
    }

    engine.onFieldChange('a', 5, { a: 5, b: 10 })
    expect(evalCount).toBe(1)

    // Advance past debounce — no pending, so no trailing execution
    vi.advanceTimersByTime(60)
    expect(evalCount).toBe(1)
  })

  it('calls separated by > 50ms each execute immediately (no merging)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),
        (callCount) => {
          const engine = new BusinessLogicEngine()
          const config: FormBusinessLogicConfig = {
            rule: [],
            options: {},
            subForms: {},
            formulas: [
              { targetField: 'total', expression: 'a + b', dependsOn: ['a', 'b'] },
            ],
          }
          engine.init(config)

          let evalCount = 0
          const originalEval = (engine as any).evaluateAffectedRules.bind(engine)
          ;(engine as any).evaluateAffectedRules = (...args: any[]) => {
            evalCount++
            return originalEval(...args)
          }

          for (let i = 0; i < callCount; i++) {
            engine.onFieldChange('a', i, { a: i, b: 10 })
            // Advance past debounce window between each call
            vi.advanceTimersByTime(60)
          }

          // Each call should execute as leading edge
          expect(evalCount).toBe(callCount)
        },
      ),
      { numRuns: 100 },
    )
  })
})
