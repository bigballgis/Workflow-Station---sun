import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { applyFormatTemplate } from '../n8nAutoFillEngine'

/**
 * Feature: n8n-output-autofill-generalization, Property 3: FormatTemplate placeholder substitution
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4
 *
 * For any formatTemplate with {fieldName} placeholders and source item where all referenced
 * fields have non-null values, the result should contain each field's value joined by separator.
 * Segments referencing null/undefined fields should be omitted.
 */
describe('Property 3: FormatTemplate placeholder substitution', () => {
  const fieldNameArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,9}$/)
  const fieldValueArb = fc.string({ minLength: 1, maxLength: 20 })
    .filter(s => !s.includes('|') && !s.includes('{') && !s.includes('}') && s.trim().length > 0)

  it('should substitute all placeholders when all fields are present', () => {
    fc.assert(
      fc.property(
        fc.array(fc.tuple(fieldNameArb, fieldValueArb), { minLength: 1, maxLength: 4 }),
        (entries) => {
          // Deduplicate field names
          const uniqueEntries = [...new Map(entries).entries()]
          fc.pre(uniqueEntries.length > 0)

          const sourceItem = Object.fromEntries(uniqueEntries)
          const template = uniqueEntries.map(([k]) => `{${k}}`).join(' | ')
          const result = applyFormatTemplate(template, sourceItem)

          // Each segment is trimmed by the implementation, so expected values are trimmed too
          const expected = uniqueEntries.map(([, v]) => v.trim()).filter(v => v !== '').join(' | ')
          expect(result).toBe(expected)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should omit segments where placeholder references null/undefined', () => {
    fc.assert(
      fc.property(
        fieldNameArb,
        fieldNameArb,
        fieldValueArb,
        (presentField, nullField, presentValue) => {
          fc.pre(presentField !== nullField)
          // Ensure presentValue.trim() doesn't coincidentally equal nullField
          fc.pre(presentValue.trim() !== nullField)

          const sourceItem: Record<string, any> = {
            [presentField]: presentValue,
            [nullField]: null,
          }
          const template = `{${presentField}} | {${nullField}}`
          const result = applyFormatTemplate(template, sourceItem)

          // The null segment should be omitted, only the present value remains (trimmed)
          expect(result).toBe(presentValue.trim())
          // Result should not contain the separator (only one segment remains)
          expect(result).not.toContain(' | ')
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should use custom separator when provided', () => {
    fc.assert(
      fc.property(
        fc.array(fc.tuple(fieldNameArb, fieldValueArb), { minLength: 2, maxLength: 4 }),
        (entries) => {
          const uniqueEntries = [...new Map(entries).entries()]
          fc.pre(uniqueEntries.length >= 2)

          const sourceItem = Object.fromEntries(uniqueEntries)
          const sep = ' - '
          const template = uniqueEntries.map(([k]) => `{${k}}`).join(sep)
          const result = applyFormatTemplate(template, sourceItem, sep)

          const expected = uniqueEntries.map(([, v]) => v.trim()).filter(v => v !== '').join(sep)
          expect(result).toBe(expected)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should return empty string when all placeholders are null', () => {
    fc.assert(
      fc.property(
        fc.array(fieldNameArb, { minLength: 1, maxLength: 3 }),
        (fields) => {
          const uniqueFields = [...new Set(fields)]
          fc.pre(uniqueFields.length > 0)

          const sourceItem: Record<string, any> = {}
          for (const f of uniqueFields) {
            sourceItem[f] = null
          }
          const template = uniqueFields.map(f => `{${f}}`).join(' | ')
          const result = applyFormatTemplate(template, sourceItem)
          expect(result).toBe('')
        }
      ),
      { numRuns: 100 }
    )
  })
})
