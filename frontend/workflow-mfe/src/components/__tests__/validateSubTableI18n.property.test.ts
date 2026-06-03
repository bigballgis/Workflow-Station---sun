/**
 * Property 8: validateSubTable i18n error messages
 * **Validates: Requirements 14.2**
 *
 * Tests that validateSubTable uses the t() function when provided,
 * and falls back to English default messages when t() is not provided.
 */
import { describe, it, expect, vi } from 'vitest'
import * as fc from 'fast-check'
import { validateSubTable } from '../businessLogicEngine'
import type { SubTableValidationConfig } from '../formRendererHelpers'

describe('Property 8: validateSubTable i18n error messages', () => {
  it('uses t() for minRows error when t is provided', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),  // minRows
        fc.integer({ min: 0, max: 1 }),   // actual rows (less than minRows)
        (minRows, rowCount) => {
          const config: SubTableValidationConfig = { minRows }
          const rows = Array.from({ length: rowCount }, () => ({}))

          const mockT = vi.fn((key: string, params?: Record<string, unknown>) => {
            return `i18n:${key}:${JSON.stringify(params)}`
          })

          const result = validateSubTable(rows, config, mockT)

          expect(result.valid).toBe(false)
          expect(result.rowCountError).toBeDefined()
          expect(mockT).toHaveBeenCalledWith('subTable.minRowsError', { min: minRows, actual: rowCount })
          expect(result.rowCountError).toContain('i18n:subTable.minRowsError')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('uses t() for maxRows error when t is provided', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 5 }),    // maxRows
        fc.integer({ min: 6, max: 20 }),   // actual rows (more than maxRows)
        (maxRows, rowCount) => {
          const config: SubTableValidationConfig = { maxRows }
          const rows = Array.from({ length: rowCount }, () => ({}))

          const mockT = vi.fn((key: string, params?: Record<string, unknown>) => {
            return `i18n:${key}:${JSON.stringify(params)}`
          })

          const result = validateSubTable(rows, config, mockT)

          expect(result.valid).toBe(false)
          expect(result.rowCountError).toBeDefined()
          expect(mockT).toHaveBeenCalledWith('subTable.maxRowsError', { max: maxRows, actual: rowCount })
          expect(result.rowCountError).toContain('i18n:subTable.maxRowsError')
        },
      ),
      { numRuns: 100 },
    )
  })

  it('uses English fallback when t is not provided', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 2, max: 10 }),
        fc.integer({ min: 0, max: 1 }),
        (minRows, rowCount) => {
          const config: SubTableValidationConfig = { minRows }
          const rows = Array.from({ length: rowCount }, () => ({}))

          // No t function provided
          const result = validateSubTable(rows, config)

          expect(result.valid).toBe(false)
          expect(result.rowCountError).toBe(
            `Minimum ${minRows} row(s) required, got ${rowCount}`
          )
        },
      ),
      { numRuns: 100 },
    )
  })

  it('valid row count produces no error regardless of t presence', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 5 }),
        fc.integer({ min: 5, max: 10 }),
        fc.boolean(),
        (minRows, maxRows, useT) => {
          const rowCount = minRows + Math.floor((maxRows - minRows) / 2)
          const config: SubTableValidationConfig = { minRows, maxRows }
          const rows = Array.from({ length: rowCount }, () => ({}))

          const mockT = vi.fn(() => 'translated')
          const result = useT
            ? validateSubTable(rows, config, mockT)
            : validateSubTable(rows, config)

          expect(result.valid).toBe(true)
          expect(result.rowCountError).toBeUndefined()
          if (useT) {
            expect(mockT).not.toHaveBeenCalled()
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
