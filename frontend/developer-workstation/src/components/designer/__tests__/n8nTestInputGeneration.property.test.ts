import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

/**
 * Feature: function-unit-design-review, Property 27: N8N Action 测试输入字段生成
 * Validates: Requirements 17.3
 *
 * For any N8N_ACTION with an inputMapping configuration containing N entries,
 * the test dialog should generate exactly N input fields, each with the correct
 * paramLabel as field label and paramType-appropriate input component type.
 */

interface InputMappingEntry {
  paramName: string
  paramLabel: string
  paramType: string
  required: boolean
}

/** Pure function that generates test input fields from inputMapping */
function generateTestFields(inputMapping: InputMappingEntry[]): Array<{
  name: string
  label: string
  componentType: 'input' | 'input-number' | 'switch'
  required: boolean
}> {
  return inputMapping.map(param => ({
    name: param.paramName,
    label: param.paramLabel || param.paramName,
    componentType: param.paramType === 'number' ? 'input-number'
      : param.paramType === 'boolean' ? 'switch'
      : 'input',
    required: param.required
  }))
}

/** Pure function that generates initial structured data from inputMapping */
function generateInitialData(inputMapping: InputMappingEntry[]): Record<string, unknown> {
  const data: Record<string, unknown> = {}
  for (const param of inputMapping) {
    if (param.paramType === 'number') data[param.paramName] = 0
    else if (param.paramType === 'boolean') data[param.paramName] = false
    else data[param.paramName] = ''
  }
  return data
}

describe('Property 27: N8N Action Test Input Field Generation', () => {
  const paramTypeArb = fc.constantFrom('string', 'number', 'boolean', 'select')

  const inputMappingEntryArb = fc.record({
    paramName: fc.string({ minLength: 1, maxLength: 30 }).filter(s => /^[a-zA-Z_]\w*$/.test(s)),
    paramLabel: fc.string({ minLength: 0, maxLength: 50 }),
    paramType: paramTypeArb,
    required: fc.boolean()
  })

  const inputMappingArb = fc.array(inputMappingEntryArb, { minLength: 0, maxLength: 20 })
    .filter(arr => {
      // Ensure unique paramNames (object keys are unique)
      const names = arr.map(e => e.paramName)
      return new Set(names).size === names.length
    })

  it('should generate exactly N fields for N inputMapping entries', () => {
    fc.assert(
      fc.property(inputMappingArb, (mapping) => {
        const fields = generateTestFields(mapping)
        expect(fields).toHaveLength(mapping.length)
      }),
      { numRuns: 100 }
    )
  })

  it('should use paramLabel as field label, falling back to paramName', () => {
    fc.assert(
      fc.property(inputMappingArb, (mapping) => {
        const fields = generateTestFields(mapping)
        for (let i = 0; i < mapping.length; i++) {
          const expected = mapping[i].paramLabel || mapping[i].paramName
          expect(fields[i].label).toBe(expected)
        }
      }),
      { numRuns: 100 }
    )
  })

  it('should map paramType to correct component type', () => {
    fc.assert(
      fc.property(inputMappingArb, (mapping) => {
        const fields = generateTestFields(mapping)
        for (let i = 0; i < mapping.length; i++) {
          if (mapping[i].paramType === 'number') {
            expect(fields[i].componentType).toBe('input-number')
          } else if (mapping[i].paramType === 'boolean') {
            expect(fields[i].componentType).toBe('switch')
          } else {
            expect(fields[i].componentType).toBe('input')
          }
        }
      }),
      { numRuns: 100 }
    )
  })

  it('should generate initial data with correct default values per type', () => {
    fc.assert(
      fc.property(inputMappingArb, (mapping) => {
        const data = generateInitialData(mapping)
        expect(Object.keys(data)).toHaveLength(mapping.length)
        for (const param of mapping) {
          expect(data).toHaveProperty(param.paramName)
          if (param.paramType === 'number') expect(data[param.paramName]).toBe(0)
          else if (param.paramType === 'boolean') expect(data[param.paramName]).toBe(false)
          else expect(data[param.paramName]).toBe('')
        }
      }),
      { numRuns: 100 }
    )
  })
})
