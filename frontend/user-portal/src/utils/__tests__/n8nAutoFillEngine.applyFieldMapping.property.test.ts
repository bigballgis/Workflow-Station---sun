import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { applyFieldMapping } from '../n8nAutoFillEngine'

/**
 * Feature: n8n-output-autofill-generalization, Property 2: ValueMapping transformation correctness
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4
 *
 * For any FieldMapping with a valueMapping dictionary:
 * - source value that IS a key → returns mapped value
 * - source value NOT a key → returns defaultValue if provided, or original value
 */
describe('Property 2: ValueMapping transformation correctness', () => {
  const keyArb = fc.string({ minLength: 1, maxLength: 20 })
  const valueArb = fc.string({ minLength: 1, maxLength: 20 })

  it('should return mapped value when source value matches a key in valueMapping', () => {
    fc.assert(
      fc.property(
        // Generate a non-empty valueMapping dict
        fc.array(fc.tuple(keyArb, valueArb), { minLength: 1, maxLength: 5 }),
        fc.string({ minLength: 1, maxLength: 10 }), // sourceField name
        fc.string({ minLength: 1, maxLength: 10 }), // targetField name
        (entries, sourceFieldName, targetFieldName) => {
          const valueMapping = Object.fromEntries(entries)
          const keys = Object.keys(valueMapping)
          // Pick a key that exists in the mapping
          const chosenKey = keys[0]

          const mapping = {
            sourceField: sourceFieldName,
            targetField: targetFieldName,
            valueMapping,
          }
          const sourceItem = { [sourceFieldName]: chosenKey }
          const result = applyFieldMapping(mapping, sourceItem)
          expect(result).toBe(valueMapping[chosenKey])
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should return defaultValue when source value does not match any key and defaultValue is provided', () => {
    fc.assert(
      fc.property(
        fc.dictionary(keyArb, valueArb, { minKeys: 1, maxKeys: 5 }),
        keyArb.filter(k => k.length > 0),
        valueArb,
        keyArb,
        keyArb,
        (valueMapping, unmatchedValue, defaultValue, sourceFieldName, targetFieldName) => {
          // Ensure unmatchedValue is NOT in the mapping
          fc.pre(!(unmatchedValue in valueMapping))

          const mapping = {
            sourceField: sourceFieldName,
            targetField: targetFieldName,
            valueMapping,
            defaultValue,
          }
          const sourceItem = { [sourceFieldName]: unmatchedValue }
          const result = applyFieldMapping(mapping, sourceItem)
          expect(result).toBe(defaultValue)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should return original value when source value does not match and no defaultValue', () => {
    fc.assert(
      fc.property(
        fc.dictionary(keyArb, valueArb, { minKeys: 1, maxKeys: 5 }),
        keyArb.filter(k => k.length > 0),
        keyArb,
        keyArb,
        (valueMapping, unmatchedValue, sourceFieldName, targetFieldName) => {
          fc.pre(!(unmatchedValue in valueMapping))

          const mapping = {
            sourceField: sourceFieldName,
            targetField: targetFieldName,
            valueMapping,
          }
          const sourceItem = { [sourceFieldName]: unmatchedValue }
          const result = applyFieldMapping(mapping, sourceItem)
          expect(result).toBe(unmatchedValue)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should return sourceField value directly when no valueMapping is defined', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        fc.oneof(fc.string(), fc.integer()),
        (sourceFieldName, targetFieldName, rawValue) => {
          const mapping = {
            sourceField: sourceFieldName,
            targetField: targetFieldName,
          }
          const sourceItem = { [sourceFieldName]: rawValue }
          const result = applyFieldMapping(mapping, sourceItem)
          expect(result).toBe(rawValue)
        }
      ),
      { numRuns: 100 }
    )
  })
})
