import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { applyAutoFill } from '../n8nAutoFillEngine'
import type {
  SubTableBinding,
  SubTableMappingEntry,
  FieldMapping,
  FieldMappingEntry,
} from '../n8nAutoFillEngine'

/**
 * Feature: n8n-output-autofill-generalization, Property 4: Append mode grows sub-table by valid row count
 * Validates: Requirements 1.2, 1.3, 1.4, 1.6, 4.1, 5.1, 5.2, 5.3
 *
 * For any source array and initial sub-table data, resulting length = initial length +
 * count of source items that produce at least one non-null field.
 */
describe('Property 4: Append mode grows sub-table by valid row count', () => {
  // Generate initial sub-table data (existing rows)
  const existingRowArb = fc.dictionary(
    fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,5}$/),
    fc.string({ minLength: 1, maxLength: 10 }),
    { minKeys: 1, maxKeys: 3 }
  )

  it('should grow sub-table length by the count of valid (non-all-null) source items', () => {
    // Use a fixed set of field names to avoid nested property generation
    const sourceFields = ['fieldA', 'fieldB']
    const fixedFieldMappings: FieldMapping[] = sourceFields.map(name => ({
      sourceField: name,
      targetField: `target_${name}`,
    }))

    const sourceItemArb2 = fc.record({
      fieldA: fc.oneof(fc.string({ minLength: 1, maxLength: 10 }), fc.constant(null as string | null)),
      fieldB: fc.oneof(fc.string({ minLength: 1, maxLength: 10 }), fc.constant(null as string | null)),
    })

    fc.assert(
      fc.property(
        fc.array(existingRowArb, { minLength: 0, maxLength: 3 }),
        fc.array(sourceItemArb2, { minLength: 0, maxLength: 6 }),
        fc.nat({ max: 99 }),
        (initialData, sourceItems, bindingId) => {
          const binding: SubTableBinding = {
            bindingId,
            tableName: 'test_table',
            columns: fixedFieldMappings.map(fm => ({ field: fm.targetField, label: fm.targetField })),
            data: JSON.parse(JSON.stringify(initialData)),
          }

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'append',
            fieldMappings: fixedFieldMappings,
          }

          const n8nOutput = { items: sourceItems }
          const result = applyAutoFill(n8nOutput, [mappingEntry], [binding], {})

          // Count valid rows: source items where at least one mapped field is non-null
          const validCount = sourceItems.filter(item =>
            sourceFields.some(name => (item as Record<string, any>)[name] != null)
          ).length

          const resultBinding = result.updatedBindings.find(b => b.bindingId === bindingId)!
          expect(resultBinding.data.length).toBe(initialData.length + validCount)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should not append rows when all fields in a source item are null', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 99 }),
        (bindingId) => {
          const fieldMappings: FieldMapping[] = [
            { sourceField: 'fieldA', targetField: 'target_fieldA' },
            { sourceField: 'fieldB', targetField: 'target_fieldB' },
          ]
          // Create source items where ALL fields are null
          const allNullItem: Record<string, any> = { fieldA: null, fieldB: null }

          const binding: SubTableBinding = {
            bindingId,
            tableName: 'test_table',
            columns: fieldMappings.map(fm => ({ field: fm.targetField, label: fm.targetField })),
            data: [],
          }

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'append',
            fieldMappings,
          }

          const n8nOutput = { items: [allNullItem, allNullItem] }
          const result = applyAutoFill(n8nOutput, [mappingEntry], [binding], {})

          const resultBinding = result.updatedBindings.find(b => b.bindingId === bindingId)!
          expect(resultBinding.data.length).toBe(0)
          expect(result.filledCount).toBe(0)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should default to append mode when fillMode is undefined', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 99 }),
        (bindingId) => {
          const binding: SubTableBinding = {
            bindingId,
            tableName: 'test_table',
            columns: [{ field: 'target_name', label: 'Name' }],
            data: [{ target_name: 'existing' }],
          }

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            // fillMode is intentionally undefined
            fieldMappings: [{ sourceField: 'name', targetField: 'target_name' }],
          }

          const n8nOutput = { items: [{ name: 'new_item' }] }
          const result = applyAutoFill(n8nOutput, [mappingEntry], [binding], {})

          const resultBinding = result.updatedBindings.find(b => b.bindingId === bindingId)!
          // Should append: 1 existing + 1 new = 2
          expect(resultBinding.data.length).toBe(2)
        }
      ),
      { numRuns: 100 }
    )
  })
})


/**
 * Feature: n8n-output-autofill-generalization, Property 5: Update mode preserves unspecified fields
 * Validates: Requirements 6.1, 6.2, 6.3
 *
 * For any existing rows with arbitrary fields, after update, fields NOT in fieldMappings
 * retain original values; row count unchanged.
 */
describe('Property 5: Update mode preserves unspecified fields', () => {
  const fieldValueArb = fc.oneof(
    fc.string({ minLength: 1, maxLength: 15 }),
    fc.integer({ min: 0, max: 1000 })
  )

  it('should preserve fields not listed in fieldMappings and keep row count unchanged', () => {
    // mapped fields: fieldA (source) -> target_a (target)
    // unspecified fields: extra1, extra2 (should be preserved)
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            target_a: fieldValueArb,
            extra1: fieldValueArb,
            extra2: fieldValueArb,
          }),
          { minLength: 1, maxLength: 5 }
        ),
        fc.array(
          fc.record({
            fieldA: fc.string({ minLength: 1, maxLength: 10 }),
          }),
          { minLength: 0, maxLength: 8 }
        ),
        fc.nat({ max: 99 }),
        (existingRows, sourceItems, bindingId) => {
          const originalRows = JSON.parse(JSON.stringify(existingRows))

          const binding: SubTableBinding = {
            bindingId,
            tableName: 'test_table',
            columns: [
              { field: 'target_a', label: 'A' },
              { field: 'extra1', label: 'Extra1' },
              { field: 'extra2', label: 'Extra2' },
            ],
            data: JSON.parse(JSON.stringify(existingRows)),
          }

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'update',
            fieldMappings: [{ sourceField: 'fieldA', targetField: 'target_a' }],
          }

          const n8nOutput = { items: sourceItems }
          const result = applyAutoFill(n8nOutput, [mappingEntry], [binding], {})

          const resultBinding = result.updatedBindings.find(b => b.bindingId === bindingId)!

          // Row count should remain unchanged
          expect(resultBinding.data.length).toBe(existingRows.length)

          // For each row, unspecified fields (extra1, extra2) should retain original values
          const updateCount = Math.min(sourceItems.length, existingRows.length)
          for (let i = 0; i < existingRows.length; i++) {
            expect(resultBinding.data[i].extra1).toBe(originalRows[i].extra1)
            expect(resultBinding.data[i].extra2).toBe(originalRows[i].extra2)
          }

          // For updated rows, the mapped field should be updated
          for (let i = 0; i < updateCount; i++) {
            expect(resultBinding.data[i].target_a).toBe(sourceItems[i].fieldA)
          }

          // For rows beyond source items, mapped field should remain original
          for (let i = updateCount; i < existingRows.length; i++) {
            expect(resultBinding.data[i].target_a).toBe(originalRows[i].target_a)
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should ignore extra source items beyond existing row count', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 99 }),
        (bindingId) => {
          // 2 existing rows, 5 source items → only 2 should be updated
          const existingRows = [
            { target_a: 'old1', keep: 'k1' },
            { target_a: 'old2', keep: 'k2' },
          ]
          const sourceItems = [
            { fieldA: 'new1' },
            { fieldA: 'new2' },
            { fieldA: 'new3' },
            { fieldA: 'new4' },
            { fieldA: 'new5' },
          ]

          const binding: SubTableBinding = {
            bindingId,
            tableName: 'test_table',
            columns: [
              { field: 'target_a', label: 'A' },
              { field: 'keep', label: 'Keep' },
            ],
            data: JSON.parse(JSON.stringify(existingRows)),
          }

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'update',
            fieldMappings: [{ sourceField: 'fieldA', targetField: 'target_a' }],
          }

          const n8nOutput = { items: sourceItems }
          const result = applyAutoFill(n8nOutput, [mappingEntry], [binding], {})

          const resultBinding = result.updatedBindings.find(b => b.bindingId === bindingId)!

          // Row count unchanged
          expect(resultBinding.data.length).toBe(2)
          // Only first 2 updated
          expect(resultBinding.data[0].target_a).toBe('new1')
          expect(resultBinding.data[1].target_a).toBe('new2')
          // Unspecified fields preserved
          expect(resultBinding.data[0].keep).toBe('k1')
          expect(resultBinding.data[1].keep).toBe('k2')
        }
      ),
      { numRuns: 100 }
    )
  })
})


/**
 * Feature: n8n-output-autofill-generalization, Property 6: Field mapping sets formData correctly
 * Validates: Requirements 1.5, 7.1, 7.2
 *
 * For any FieldMappingEntry with a simple (non-aggregation) source path, and N8N output
 * where that path resolves to a non-null value, the resulting formData should have
 * targetField set to that resolved value.
 */
describe('Property 6: Field mapping sets formData correctly', () => {
  const keyArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,7}$/)
  const valueArb = fc.oneof(
    fc.string({ minLength: 1, maxLength: 20 }),
    fc.integer({ min: -1000, max: 1000 }),
    fc.boolean()
  )

  it('should set formData[targetField] to the resolved value for simple source paths', () => {
    fc.assert(
      fc.property(
        keyArb, // source key in n8nOutput
        keyArb, // targetField name
        valueArb, // value at source path
        (sourceKey, targetField, value) => {
          const n8nOutput = { [sourceKey]: value }
          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: sourceKey,
            targetField,
          }

          const result = applyAutoFill(n8nOutput, [entry], [], {})

          expect(result.updatedFormData[targetField]).toBe(value)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should support dot-notation source paths for nested values', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        keyArb,
        valueArb,
        (outerKey, innerKey, targetField, value) => {
          const n8nOutput = { [outerKey]: { [innerKey]: value } }
          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: `${outerKey}.${innerKey}`,
            targetField,
          }

          const result = applyAutoFill(n8nOutput, [entry], [], {})

          expect(result.updatedFormData[targetField]).toBe(value)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should skip setting formData when source path resolves to null', () => {
    fc.assert(
      fc.property(
        keyArb, // missing key
        keyArb, // targetField
        (missingKey, targetField) => {
          const n8nOutput = { otherKey: 'someValue' }
          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: missingKey,
            targetField,
          }

          // Ensure the key doesn't accidentally exist
          fc.pre(!(missingKey in n8nOutput))

          const result = applyAutoFill(n8nOutput, [entry], [], {})

          expect(result.updatedFormData[targetField]).toBeUndefined()
        }
      ),
      { numRuns: 100 }
    )
  })
})



/**
 * Feature: n8n-output-autofill-generalization, Property 7: Sum aggregation correctness
 * Validates: Requirements 7.3
 *
 * For any array of items with numeric field values, the resulting formData[targetField]
 * should equal the mathematical sum of those numeric values.
 */
describe('Property 7: Sum aggregation correctness', () => {
  const keyArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,7}$/)

  it('should set formData[targetField] to the sum of numeric field values', () => {
    fc.assert(
      fc.property(
        keyArb, // arrayKey in n8nOutput
        keyArb, // field name within each item
        keyArb, // targetField
        fc.array(fc.double({ min: -10000, max: 10000, noNaN: true, noDefaultInfinity: true }), { minLength: 1, maxLength: 20 }),
        (arrayKey, fieldName, targetField, numbers) => {
          // Build n8nOutput with an array of items
          const items = numbers.map(n => ({ [fieldName]: n }))
          const n8nOutput = { [arrayKey]: items }

          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: `sum:${arrayKey}.${fieldName}`,
            targetField,
          }

          const result = applyAutoFill(n8nOutput, [entry], [], {})

          // Expected sum
          const expectedSum = numbers.reduce((acc, n) => acc + n, 0)

          expect(result.updatedFormData[targetField]).toBeCloseTo(expectedSum, 5)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should return 0 for an empty array', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        keyArb,
        (arrayKey, fieldName, targetField) => {
          const n8nOutput = { [arrayKey]: [] }

          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: `sum:${arrayKey}.${fieldName}`,
            targetField,
          }

          const result = applyAutoFill(n8nOutput, [entry], [], {})

          expect(result.updatedFormData[targetField]).toBe(0)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should skip non-numeric values (treat as 0) in sum aggregation', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        keyArb,
        fc.array(
          fc.oneof(
            fc.double({ min: -1000, max: 1000, noNaN: true, noDefaultInfinity: true }),
            fc.constant('not_a_number' as string | number),
            fc.constant(null as null | string | number)
          ),
          { minLength: 1, maxLength: 10 }
        ),
        (arrayKey, fieldName, targetField, values) => {
          const items = values.map(v => ({ [fieldName]: v }))
          const n8nOutput = { [arrayKey]: items }

          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: `sum:${arrayKey}.${fieldName}`,
            targetField,
          }

          const result = applyAutoFill(n8nOutput, [entry], [], {})

          // Expected: sum of numeric values, non-numeric treated as 0
          const expectedSum = values.reduce((acc: number, v) => {
            const num = Number(v)
            return acc + (isNaN(num) ? 0 : num)
          }, 0)

          expect(result.updatedFormData[targetField]).toBeCloseTo(expectedSum, 5)
        }
      ),
      { numRuns: 100 }
    )
  })
})


/**
 * Feature: n8n-output-autofill-generalization, Property 8: No mutation of original inputs
 * Validates: Requirements 10.3
 *
 * For any call to applyAutoFill, the original subTableBindings array and formData object
 * should remain deeply equal to their state before the call.
 */
describe('Property 8: No mutation of original inputs', () => {
  const keyArb = fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9]{0,7}$/)
  const valueArb = fc.oneof(
    fc.string({ minLength: 1, maxLength: 15 }),
    fc.integer({ min: -1000, max: 1000 })
  )

  it('should not mutate the original subTableBindings when using append mode', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 99 }),
        fc.array(
          fc.record({
            fieldA: fc.string({ minLength: 1, maxLength: 10 }),
            fieldB: fc.string({ minLength: 1, maxLength: 10 }),
          }),
          { minLength: 1, maxLength: 5 }
        ),
        (bindingId, sourceItems) => {
          const originalData = [
            { target_a: 'existing1', target_b: 'existing2' },
          ]
          const bindings: SubTableBinding[] = [
            {
              bindingId,
              tableName: 'test_table',
              columns: [
                { field: 'target_a', label: 'A' },
                { field: 'target_b', label: 'B' },
              ],
              data: JSON.parse(JSON.stringify(originalData)),
            },
          ]

          // Deep snapshot before call
          const bindingsSnapshot = JSON.parse(JSON.stringify(bindings))

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'append',
            fieldMappings: [
              { sourceField: 'fieldA', targetField: 'target_a' },
              { sourceField: 'fieldB', targetField: 'target_b' },
            ],
          }

          applyAutoFill({ items: sourceItems }, [mappingEntry], bindings, {})

          // Original bindings should be unchanged
          expect(bindings).toEqual(bindingsSnapshot)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should not mutate the original subTableBindings when using update mode', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 99 }),
        fc.array(
          fc.record({
            fieldA: fc.string({ minLength: 1, maxLength: 10 }),
          }),
          { minLength: 1, maxLength: 5 }
        ),
        (bindingId, sourceItems) => {
          const bindings: SubTableBinding[] = [
            {
              bindingId,
              tableName: 'test_table',
              columns: [{ field: 'target_a', label: 'A' }],
              data: [{ target_a: 'old1' }, { target_a: 'old2' }, { target_a: 'old3' }],
            },
          ]

          const bindingsSnapshot = JSON.parse(JSON.stringify(bindings))

          const mappingEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'update',
            fieldMappings: [{ sourceField: 'fieldA', targetField: 'target_a' }],
          }

          applyAutoFill({ items: sourceItems }, [mappingEntry], bindings, {})

          expect(bindings).toEqual(bindingsSnapshot)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should not mutate the original formData when using field mappings', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        valueArb,
        (sourceKey, targetField, value) => {
          const formData: Record<string, any> = {
            existingField: 'should_not_change',
            anotherField: 42,
          }

          const formDataSnapshot = JSON.parse(JSON.stringify(formData))

          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: sourceKey,
            targetField,
          }

          applyAutoFill({ [sourceKey]: value }, [entry], [], formData)

          expect(formData).toEqual(formDataSnapshot)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should not mutate the original formData when using sum aggregation', () => {
    fc.assert(
      fc.property(
        keyArb,
        keyArb,
        keyArb,
        fc.array(fc.double({ min: 0, max: 1000, noNaN: true, noDefaultInfinity: true }), { minLength: 1, maxLength: 5 }),
        (arrayKey, fieldName, targetField, numbers) => {
          const formData: Record<string, any> = { preserved: 'value' }
          const formDataSnapshot = JSON.parse(JSON.stringify(formData))

          const items = numbers.map(n => ({ [fieldName]: n }))
          const entry: FieldMappingEntry = {
            targetType: 'field',
            source: `sum:${arrayKey}.${fieldName}`,
            targetField,
          }

          applyAutoFill({ [arrayKey]: items }, [entry], [], formData)

          expect(formData).toEqual(formDataSnapshot)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('should not mutate inputs when combining sub-table and field mappings', () => {
    fc.assert(
      fc.property(
        fc.nat({ max: 99 }),
        keyArb,
        (bindingId, targetField) => {
          const bindings: SubTableBinding[] = [
            {
              bindingId,
              tableName: 'test_table',
              columns: [{ field: 'target_a', label: 'A' }],
              data: [{ target_a: 'existing' }],
            },
          ]
          const formData: Record<string, any> = { original: 'untouched' }

          const bindingsSnapshot = JSON.parse(JSON.stringify(bindings))
          const formDataSnapshot = JSON.parse(JSON.stringify(formData))

          const subTableEntry: SubTableMappingEntry = {
            targetType: 'sub_table',
            targetBindingId: bindingId,
            sourceArrayKey: 'items',
            fillMode: 'append',
            fieldMappings: [{ sourceField: 'fieldA', targetField: 'target_a' }],
          }
          const fieldEntry: FieldMappingEntry = {
            targetType: 'field',
            source: 'someValue',
            targetField,
          }

          applyAutoFill(
            { items: [{ fieldA: 'new' }], someValue: 123 },
            [subTableEntry, fieldEntry],
            bindings,
            formData
          )

          expect(bindings).toEqual(bindingsSnapshot)
          expect(formData).toEqual(formDataSnapshot)
        }
      ),
      { numRuns: 100 }
    )
  })
})
