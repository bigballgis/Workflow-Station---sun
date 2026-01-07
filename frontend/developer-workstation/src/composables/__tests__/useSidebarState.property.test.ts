import { describe, it, expect, beforeEach } from 'vitest'
import * as fc from 'fast-check'
import { toggleState, parseStoredState, serializeState } from '../useSidebarState'

/**
 * Property-Based Tests for Sidebar Collapse Feature
 * Feature: sidebar-collapse
 */
describe('Sidebar State Property Tests', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  /**
   * Property 1: Toggle State Consistency
   * For any sidebar state (collapsed or expanded), clicking the toggle button
   * SHALL result in the opposite state.
   * **Validates: Requirements 1.1, 2.1**
   */
  it('Property 1: Toggle State Consistency - toggling any state produces opposite state', () => {
    fc.assert(
      fc.property(fc.boolean(), (initialState: boolean) => {
        const newState = toggleState(initialState)
        // After toggle, state should be opposite
        expect(newState).toBe(!initialState)
        // Toggle again should return to original
        expect(toggleState(newState)).toBe(initialState)
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 2: State Persistence Round-Trip
   * For any sidebar state, saving to localStorage then loading from localStorage
   * SHALL produce the same state value.
   * **Validates: Requirements 4.1, 4.2**
   */
  it('Property 2: State Persistence Round-Trip - serialize then parse preserves state', () => {
    fc.assert(
      fc.property(fc.boolean(), (state: boolean) => {
        const serialized = serializeState(state)
        const parsed = parseStoredState(serialized)
        expect(parsed).toBe(state)
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Additional property: parseStoredState handles null/invalid values
   * Edge case for Requirements 4.3
   */
  it('parseStoredState returns false for null (default state)', () => {
    expect(parseStoredState(null)).toBe(false)
  })

  it('parseStoredState returns false for invalid strings', () => {
    fc.assert(
      fc.property(
        fc.string().filter(s => s !== 'true'),
        (invalidString: string) => {
          expect(parseStoredState(invalidString)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })
})
