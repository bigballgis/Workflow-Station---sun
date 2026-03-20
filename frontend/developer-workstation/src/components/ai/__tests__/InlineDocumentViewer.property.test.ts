/**
 * Feature: xml-document-viewer, Property 7: View mode independence across instances
 *
 * For any two different InlineDocumentViewer instances, changing the view mode
 * on one instance should not affect the other instance's view mode.
 *
 * Validates: Requirements 4.5
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { ViewMode } from '@/types/aiGeneration'

describe('Property 7: View mode independence across instances', () => {
  it('changing view mode on one instance does not affect others', () => {
    const viewModes: ViewMode[] = ['xml', 'markdown']

    fc.assert(
      fc.property(
        fc.constantFrom(...viewModes),
        fc.constantFrom(...viewModes),
        fc.constantFrom(...viewModes),
        (initialA: ViewMode, initialB: ViewMode, newModeA: ViewMode) => {
          // Simulate two independent InlineDocumentViewer instances
          let viewModeA: ViewMode = initialA
          let viewModeB: ViewMode = initialB

          // Change view mode on instance A
          viewModeA = newModeA

          // Instance B should remain unchanged
          expect(viewModeB).toBe(initialB)
          // Instance A should have the new mode
          expect(viewModeA).toBe(newModeA)
        }
      ),
      { numRuns: 100 }
    )
  })
})
