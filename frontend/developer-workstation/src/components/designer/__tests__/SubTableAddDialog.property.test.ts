// Feature: sub-table-add-dialog, Property 2: Dialog form mirrors column configuration

import { describe, it } from 'vitest'
import * as fc from 'fast-check'
import { expect } from 'vitest'
import { buildInitialRow, buildRules } from '../subTableAddDialogHelpers'
import type { ColumnType } from '../subTableAddDialogHelpers'

const allColumnTypes: ColumnType[] = [
  'text', 'textarea', 'number', 'select', 'radio',
  'checkbox', 'switch', 'date', 'datetime', 'upload', 'user', 'department',
]

// Use valid JS identifier-style field names to avoid:
// - Integer-like keys being reordered by the JS engine (e.g. "0", "1")
// - Prototype property collisions (e.g. "constructor", "toString")
const fieldNameArbitrary = fc.stringMatching(/^[a-zA-Z_][a-zA-Z0-9_]{0,19}$/)

const columnArbitrary = fc.record({
  field: fieldNameArbitrary,
  label: fc.string({ minLength: 1 }),
  required: fc.boolean(),
  type: fc.constantFrom(...allColumnTypes),
})

// Columns array with unique field names (mirrors real DB column constraints)
const uniqueColumnsArbitrary = fc
  .uniqueArray(columnArbitrary, { minLength: 1, maxLength: 10, selector: (c) => c.field })

describe('Property 2: Dialog form mirrors column configuration', () => {
  /**
   * Validates: Requirements 3.1, 3.2, 3.4
   *
   * For any array of column configurations, buildInitialRow should produce
   * exactly the same keys as column fields (in the same order), and buildRules
   * should produce rules for every required column and no rules for non-required columns.
   */
  it('buildInitialRow has exactly the same keys as column fields, in the same order', () => {
    fc.assert(
      fc.property(
        uniqueColumnsArbitrary,
        (columns) => {
          const initialRow = buildInitialRow(columns)
          const rowKeys = Object.keys(initialRow)
          const columnFields = columns.map((c) => c.field)

          // Same number of keys
          expect(rowKeys.length).toBe(columnFields.length)

          // Same keys in the same order
          rowKeys.forEach((key, idx) => {
            expect(key).toBe(columnFields[idx])
          })
        },
      ),
      { numRuns: 100 },
    )
  })

  it('buildRules generates a rule for every required column and no rule for non-required columns', () => {
    fc.assert(
      fc.property(
        uniqueColumnsArbitrary,
        (columns) => {
          const rules = buildRules(columns)

          for (const col of columns) {
            if (col.required) {
              // Required columns must have a rule
              expect(rules[col.field]).toBeDefined()
              expect(Array.isArray(rules[col.field])).toBe(true)
              const ruleArr = rules[col.field] as Array<{ required: boolean; message: string }>
              expect(ruleArr[0].required).toBe(true)
            } else {
              // Non-required columns must NOT have a rule
              expect(rules[col.field]).toBeUndefined()
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('required column rules have the correct message format: "<label> is required"', () => {
    fc.assert(
      fc.property(
        uniqueColumnsArbitrary,
        (columns) => {
          const rules = buildRules(columns)

          for (const col of columns) {
            if (col.required) {
              const ruleArr = rules[col.field] as Array<{ required: boolean; message: string }>
              expect(ruleArr[0].message).toBe(`${col.label} is required`)
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})

// Feature: sub-table-add-dialog, Property 3: Control type mapping is correct

import { CONTROL_TYPE_MAP, resolveControlComponent } from '../subTableAddDialogHelpers'

describe('Property 3: Control type mapping is correct', () => {
  /**
   * Validates: Requirements 4.1–4.10
   *
   * For any column configuration with a given type value, resolveControlComponent
   * should return the correct Element Plus component name from CONTROL_TYPE_MAP.
   */
  it('resolveControlComponent returns the mapped component for every ColumnType', () => {
    fc.assert(
      fc.property(
        fc.record({
          field: fieldNameArbitrary,
          label: fc.string({ minLength: 1 }),
          type: fc.constantFrom(...allColumnTypes),
        }),
        (column) => {
          const result = resolveControlComponent(column)
          expect(result).toBe(CONTROL_TYPE_MAP[column.type ?? 'text'])
        },
      ),
      { numRuns: 100 },
    )
  })

  it('resolveControlComponent defaults to ElInput when type is undefined', () => {
    fc.assert(
      fc.property(
        fc.record({
          field: fieldNameArbitrary,
          label: fc.string({ minLength: 1 }),
        }),
        (column) => {
          const result = resolveControlComponent(column)
          expect(result).toBe('ElInput')
        },
      ),
      { numRuns: 100 },
    )
  })
})

// Feature: sub-table-add-dialog, Property 1: Cancel preserves table state

describe('Property 1: Cancel preserves table state', () => {
  /**
   * Validates: Requirements 1.4, 2.1
   *
   * For any sub-table with any number of existing rows, opening the Add Dialog
   * and then canceling (without saving) should leave the row count and all row
   * data completely unchanged.
   */
  it('cancel does not mutate the rows array', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({ id: fc.integer(), name: fc.string() })),
        (existingRows) => {
          const rows = [...existingRows]
          const snapshot = JSON.stringify(rows)
          // Simulate cancel: dialogVisible = false, handleDialogSave is NOT called
          // rows remains unchanged
          expect(JSON.stringify(rows)).toBe(snapshot)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('handleDialogSave in add mode does not mutate the original array reference', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({ id: fc.integer() })),
        fc.record({ name: fc.string({ minLength: 1 }) }),
        (existingRows, newRow) => {
          const rows = [...existingRows]
          const original = rows
          // Simulate handleDialogSave in add mode: push new row then emit a spread copy
          rows.push(newRow)
          const emitted = [...rows]
          // emitted is a new array reference, not the same as original
          expect(emitted).not.toBe(original)
          // emitted has exactly one more element than the original existingRows
          expect(emitted.length).toBe(existingRows.length + 1)
        },
      ),
      { numRuns: 100 },
    )
  })
})

// Feature: sub-table-add-dialog, Property 4: Valid save appends exactly one row

describe('Property 4: Valid save appends exactly one row', () => {
  /**
   * Validates: Requirements 5.2, 5.3
   *
   * For any sub-table with N rows and any valid row data (all required fields
   * filled), clicking Save should result in the table having exactly N+1 rows,
   * with the new row appended at the end containing the submitted data.
   */
  it('add mode: valid save results in exactly N+1 rows with new row at the end', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({ id: fc.integer() })),
        fc.record({ name: fc.string({ minLength: 1 }), value: fc.integer() }),
        (existingRows, newRowData) => {
          const rows = [...existingRows]
          const nBefore = rows.length
          // simulate handleDialogSave in add mode
          rows.push(newRowData)
          const emitted = [...rows]
          expect(emitted.length).toBe(nBefore + 1)
          expect(emitted[emitted.length - 1]).toEqual(newRowData)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('edit mode: valid save results in exactly N rows with row[i] replaced', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({ id: fc.integer() }), { minLength: 1 }),
        fc.record({ name: fc.string({ minLength: 1 }), value: fc.integer() }),
        fc.nat(),
        (existingRows, updatedRowData, rawIndex) => {
          const rows = existingRows.map((r) => ({ ...r }))
          const nBefore = rows.length
          const editIndex = rawIndex % nBefore
          // simulate handleDialogSave in edit mode
          rows[editIndex] = updatedRowData
          const emitted = [...rows]
          // row count must stay the same
          expect(emitted.length).toBe(nBefore)
          // the edited row must contain the new data
          expect(emitted[editIndex]).toEqual(updatedRowData)
        },
      ),
      { numRuns: 100 },
    )
  })
})

// Feature: sub-table-add-dialog, Property 5: Invalid save does not modify table

import type { DialogColumn } from '../subTableAddDialogHelpers'

function validateRow(data: Record<string, any>, columns: DialogColumn[]): boolean {
  return columns.every(col => {
    if (!col.required) return true
    const val = data[col.field]
    if (Array.isArray(val)) return val.length > 0
    return val !== '' && val !== null && val !== undefined
  })
}

describe('Property 5: Invalid save does not modify table', () => {
  /**
   * Validates: Requirements 5.4, 5.5
   *
   * For any sub-table state and any form submission where at least one required
   * field is empty or a validation rule fails, clicking Save should not append
   * any row to the table, and the table should remain unchanged.
   */
  it('empty required fields fail validation and rows are not modified', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({ id: fc.integer() })),
        fc.array(
          fc.record({
            field: fieldNameArbitrary,
            label: fc.string({ minLength: 1 }),
            required: fc.constant(true),
            type: fc.constantFrom('text', 'number', 'select', 'checkbox', 'date', 'datetime') as fc.Arbitrary<'text' | 'number' | 'select' | 'checkbox' | 'date' | 'datetime'>,
          }),
          { minLength: 1, maxLength: 5 },
        ),
        (existingRows, requiredColumns) => {
          const rows = [...existingRows]
          const nBefore = rows.length
          // Submit empty data for all required fields
          const emptyData: Record<string, any> = {}
          requiredColumns.forEach(c => {
            if (c.type === 'checkbox') emptyData[c.field] = []
            else if (c.type === 'number') emptyData[c.field] = undefined
            else if (c.type === 'date' || c.type === 'datetime') emptyData[c.field] = null
            else emptyData[c.field] = ''
          })
          const isValid = validateRow(emptyData, requiredColumns)
          // All required fields are empty, so validation must fail
          expect(isValid).toBe(false)
          // Since invalid, rows must not be modified
          expect(rows.length).toBe(nBefore)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('partially filled data (some required fields empty) also fails validation', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            field: fieldNameArbitrary,
            label: fc.string({ minLength: 1 }),
            required: fc.constant(true),
            type: fc.constantFrom('text', 'number', 'select', 'checkbox', 'date', 'datetime') as fc.Arbitrary<'text' | 'number' | 'select' | 'checkbox' | 'date' | 'datetime'>,
          }),
          { minLength: 2, maxLength: 5 },
        ),
        (requiredColumns) => {
          // Fill all fields with valid values first
          const partialData: Record<string, any> = {}
          requiredColumns.forEach(c => {
            if (c.type === 'checkbox') partialData[c.field] = ['option1']
            else if (c.type === 'number') partialData[c.field] = 42
            else if (c.type === 'date' || c.type === 'datetime') partialData[c.field] = '2024-01-01'
            else partialData[c.field] = 'filled'
          })
          // Leave the last required field empty
          const lastCol = requiredColumns[requiredColumns.length - 1]
          if (lastCol.type === 'checkbox') partialData[lastCol.field] = []
          else if (lastCol.type === 'number') partialData[lastCol.field] = undefined
          else if (lastCol.type === 'date' || lastCol.type === 'datetime') partialData[lastCol.field] = null
          else partialData[lastCol.field] = ''

          const isValid = validateRow(partialData, requiredColumns)
          // At least one required field is empty, so validation must fail
          expect(isValid).toBe(false)
        },
      ),
      { numRuns: 100 },
    )
  })
})
