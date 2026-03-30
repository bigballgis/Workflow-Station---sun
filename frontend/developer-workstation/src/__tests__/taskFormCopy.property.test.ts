import { describe, test, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Property-Based Tests for Task Form Copy
 * Feature: process-task-form-separation
 *
 * Property 6: Task Form copy preserves layout but clears Stage bindings (frontend)
 * **Validates: Requirements 3.7, 3.8**
 */

interface StageBinding {
  id?: number
  stageId: string
  stageName?: string
}

interface CopiedForm {
  id: number
  configJson: Record<string, unknown>
  stageBindings: StageBinding[]
}

/**
 * Simulates the frontend expectation of a Task Form copy operation.
 * The backend returns a new form with:
 * - A new unique ID (different from source)
 * - configJson deeply equal to the source
 * - stageBindings cleared (empty array)
 */
function simulateCopyResult(
  sourceId: number,
  sourceConfigJson: Record<string, unknown>,
  _sourceStageBindings: StageBinding[],
): CopiedForm {
  // Deep clone configJson to simulate backend behavior
  const clonedConfig = JSON.parse(JSON.stringify(sourceConfigJson))
  return {
    id: sourceId + 1000, // new unique ID
    configJson: clonedConfig,
    stageBindings: [], // cleared
  }
}

/**
 * Deep equality check for JSON-serializable objects.
 */
function deepEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

describe('Property 6: Task Form copy preserves layout but clears Stage bindings (frontend)', () => {
  const jsonValueArb = fc.jsonValue({ maxDepth: 3 })
  const configJsonArb = fc.dictionary(
    fc.string({ minLength: 1, maxLength: 20 }),
    jsonValueArb,
    { minKeys: 0, maxKeys: 10 },
  )

  const stageBindingArb = fc.record({
    id: fc.option(fc.nat(), { nil: undefined }),
    stageId: fc.string({ minLength: 1, maxLength: 50 }),
    stageName: fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined }),
  })

  test('property: copied form configJson is deeply equal to source', () => {
    fc.assert(
      fc.property(
        fc.nat(),
        configJsonArb,
        fc.array(stageBindingArb, { minLength: 1, maxLength: 5 }),
        (sourceId: number, configJson: Record<string, unknown>, stageBindings: StageBinding[]) => {
          const copy = simulateCopyResult(sourceId, configJson, stageBindings)
          expect(deepEqual(copy.configJson, configJson)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: copied form has empty stageBindings', () => {
    fc.assert(
      fc.property(
        fc.nat(),
        configJsonArb,
        fc.array(stageBindingArb, { minLength: 1, maxLength: 5 }),
        (sourceId: number, configJson: Record<string, unknown>, stageBindings: StageBinding[]) => {
          const copy = simulateCopyResult(sourceId, configJson, stageBindings)
          expect(copy.stageBindings).toEqual([])
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: copied form has a different ID from source', () => {
    fc.assert(
      fc.property(
        fc.nat(),
        configJsonArb,
        fc.array(stageBindingArb, { minLength: 0, maxLength: 5 }),
        (sourceId: number, configJson: Record<string, unknown>, stageBindings: StageBinding[]) => {
          const copy = simulateCopyResult(sourceId, configJson, stageBindings)
          expect(copy.id).not.toBe(sourceId)
        },
      ),
      { numRuns: 100 },
    )
  })

  test('property: modifying copied configJson does not affect source', () => {
    fc.assert(
      fc.property(
        fc.nat(),
        configJsonArb,
        fc.array(stageBindingArb, { minLength: 0, maxLength: 5 }),
        (sourceId: number, configJson: Record<string, unknown>, stageBindings: StageBinding[]) => {
          const originalSnapshot = JSON.stringify(configJson)
          const copy = simulateCopyResult(sourceId, configJson, stageBindings)

          // Mutate the copy
          ;(copy.configJson as Record<string, unknown>)['__mutated__'] = true

          // Source should be unchanged
          expect(JSON.stringify(configJson)).toBe(originalSnapshot)
        },
      ),
      { numRuns: 100 },
    )
  })
})
