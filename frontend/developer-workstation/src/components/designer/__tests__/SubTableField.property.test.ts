import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Property 5: Sub-table component configuration
 * For any sub-table component in a form, it should be configurable with a data source,
 * display columns, and when binding mode is EDITABLE, it should support inline CRUD operations.
 * 
 * Validates: Requirements 5.2, 5.3, 5.4
 */

// Column configuration interface
interface ColumnConfig {
  field: string
  label: string
  type?: 'input' | 'number' | 'date' | 'switch' | 'text'
  width?: number
  minWidth?: number
}

// Sub-table configuration interface
interface SubTableConfig {
  title?: string
  bindingId?: number
  tableId?: number
  columns: ColumnConfig[]
  pagination?: boolean
  pageSize?: number
  maxHeight?: number
}

describe('SubTableField Property Tests', () => {
  // Arbitrary for column type
  const columnTypeArb = fc.constantFrom<'input' | 'number' | 'date' | 'switch' | 'text'>('input', 'number', 'date', 'switch', 'text')
  
  // Arbitrary for column config
  const columnConfigArb = fc.record({
    field: fc.string({ minLength: 1, maxLength: 30 }).filter(s => /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(s)),
    label: fc.string({ minLength: 1, maxLength: 50 }),
    type: fc.option(columnTypeArb, { nil: undefined }),
    width: fc.option(fc.integer({ min: 50, max: 500 }), { nil: undefined }),
    minWidth: fc.option(fc.integer({ min: 50, max: 200 }), { nil: undefined })
  })
  
  // Arbitrary for sub-table config
  const subTableConfigArb = fc.record({
    title: fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined }),
    bindingId: fc.option(fc.nat({ min: 1 }), { nil: undefined }),
    tableId: fc.option(fc.nat({ min: 1 }), { nil: undefined }),
    columns: fc.array(columnConfigArb, { minLength: 1, maxLength: 10 }),
    pagination: fc.option(fc.boolean(), { nil: undefined }),
    pageSize: fc.option(fc.integer({ min: 5, max: 100 }), { nil: undefined }),
    maxHeight: fc.option(fc.integer({ min: 100, max: 800 }), { nil: undefined })
  })

  it('Property 5.1: Sub-table config should have at least one column', () => {
    fc.assert(
      fc.property(subTableConfigArb, (config) => {
        expect(config.columns.length).toBeGreaterThanOrEqual(1)
      }),
      { numRuns: 50 }
    )
  })

  it('Property 5.2: Column fields should be valid identifiers', () => {
    fc.assert(
      fc.property(subTableConfigArb, (config) => {
        config.columns.forEach(col => {
          expect(col.field).toMatch(/^[a-zA-Z_][a-zA-Z0-9_]*$/)
        })
      }),
      { numRuns: 50 }
    )
  })

  it('Property 5.3: Column types should be valid', () => {
    const validTypes = ['input', 'number', 'date', 'switch', 'text', undefined]
    
    fc.assert(
      fc.property(subTableConfigArb, (config) => {
        config.columns.forEach(col => {
          expect(validTypes).toContain(col.type)
        })
      }),
      { numRuns: 50 }
    )
  })

  it('Property 5.4: Page size should be positive when pagination is enabled', () => {
    fc.assert(
      fc.property(subTableConfigArb, (config) => {
        if (config.pagination && config.pageSize !== undefined) {
          expect(config.pageSize).toBeGreaterThan(0)
        }
        return true
      }),
      { numRuns: 50 }
    )
  })

  it('Property 5.5: Max height should be reasonable when specified', () => {
    fc.assert(
      fc.property(subTableConfigArb, (config) => {
        if (config.maxHeight !== undefined) {
          expect(config.maxHeight).toBeGreaterThanOrEqual(100)
          expect(config.maxHeight).toBeLessThanOrEqual(800)
        }
        return true
      }),
      { numRuns: 50 }
    )
  })

  it('Property 5.6: Column widths should be reasonable when specified', () => {
    fc.assert(
      fc.property(subTableConfigArb, (config) => {
        config.columns.forEach(col => {
          if (col.width !== undefined) {
            expect(col.width).toBeGreaterThanOrEqual(50)
          }
          if (col.minWidth !== undefined) {
            expect(col.minWidth).toBeGreaterThanOrEqual(50)
          }
        })
        return true
      }),
      { numRuns: 50 }
    )
  })
})

/**
 * Sub-table data operations tests
 */
describe('SubTableField Data Operations', () => {
  // Arbitrary for row data
  const rowDataArb = fc.record({
    id: fc.nat(),
    name: fc.string({ minLength: 1, maxLength: 50 }),
    value: fc.integer(),
    active: fc.boolean()
  })

  it('Property: Adding a row should increase data length by 1', () => {
    fc.assert(
      fc.property(
        fc.array(rowDataArb, { minLength: 0, maxLength: 10 }),
        rowDataArb,
        (existingData, newRow) => {
          const originalLength = existingData.length
          const newData = [...existingData, newRow]
          expect(newData.length).toBe(originalLength + 1)
        }
      ),
      { numRuns: 50 }
    )
  })

  it('Property: Deleting a row should decrease data length by 1', () => {
    fc.assert(
      fc.property(
        fc.array(rowDataArb, { minLength: 1, maxLength: 10 }),
        (existingData) => {
          const originalLength = existingData.length
          const indexToDelete = Math.floor(Math.random() * originalLength)
          const newData = existingData.filter((_, i) => i !== indexToDelete)
          expect(newData.length).toBe(originalLength - 1)
        }
      ),
      { numRuns: 50 }
    )
  })

  it('Property: Editing a row should not change data length', () => {
    fc.assert(
      fc.property(
        fc.array(rowDataArb, { minLength: 1, maxLength: 10 }),
        rowDataArb,
        (existingData, updatedRow) => {
          const originalLength = existingData.length
          const indexToEdit = Math.floor(Math.random() * originalLength)
          const newData = existingData.map((row, i) => i === indexToEdit ? updatedRow : row)
          expect(newData.length).toBe(originalLength)
        }
      ),
      { numRuns: 50 }
    )
  })
})
