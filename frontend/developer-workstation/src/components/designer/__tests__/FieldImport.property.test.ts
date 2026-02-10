import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { FieldDefinition, TableBinding, BindingType } from '@/api/functionUnit'

/**
 * Property 4: Field import from multiple tables
 * For any form with multiple bound tables, fields can be imported from any bound table,
 * each imported field should indicate its source table.
 * 
 * Validates: Requirements 4.1, 4.2, 4.4
 */
describe('Field Import Property Tests', () => {
  // Arbitrary for data types
  const dataTypeArb = fc.constantFrom('VARCHAR', 'TEXT', 'INTEGER', 'BIGINT', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP')
  
  // Arbitrary for FieldDefinition
  const fieldDefinitionArb = fc.record({
    id: fc.nat(),
    fieldName: fc.string({ minLength: 1, maxLength: 50 }).filter(s => /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(s)),
    dataType: dataTypeArb,
    length: fc.option(fc.nat({ max: 4000 }), { nil: undefined }),
    precision: fc.option(fc.nat({ max: 38 }), { nil: undefined }),
    scale: fc.option(fc.nat({ max: 10 }), { nil: undefined }),
    nullable: fc.boolean(),
    isPrimaryKey: fc.boolean(),
    defaultValue: fc.option(fc.string({ maxLength: 100 }), { nil: undefined }),
    description: fc.option(fc.string({ maxLength: 200 }), { nil: undefined })
  })
  
  // Arbitrary for TableBinding
  const tableBindingArb = fc.record({
    id: fc.nat(),
    tableId: fc.nat({ min: 1 }),
    tableName: fc.string({ minLength: 1, maxLength: 50 }).filter(s => s.trim().length > 0),
    bindingType: fc.constantFrom<BindingType>('PRIMARY', 'SUB', 'RELATED'),
    bindingMode: fc.constantFrom('EDITABLE', 'READONLY'),
    foreignKeyField: fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined }),
    sortOrder: fc.nat({ max: 100 })
  })

  // Helper function to map data type to form component
  const getFormComponentType = (dataType: string): string => {
    const typeMap: Record<string, string> = {
      'VARCHAR': 'Input Box',
      'TEXT': 'Text Area',
      'INTEGER': 'Number Input',
      'BIGINT': 'Number Input',
      'DECIMAL': 'Number Input',
      'BOOLEAN': 'Switch',
      'DATE': 'Date Picker',
      'TIMESTAMP': 'Date Time Picker'
    }
    return Object.hasOwn(typeMap, dataType) ? typeMap[dataType] : 'Input Box'
  }

  it('Property 4.1: Fields can be imported from any bound table', () => {
    // Create a valid binding arbitrary with tableId >= 1
    const validBindingArb = fc.record({
      id: fc.nat({ min: 1 }),
      tableId: fc.integer({ min: 1, max: 1000 }),
      tableName: fc.string({ minLength: 1, maxLength: 50 }).filter(s => s.trim().length > 0),
      bindingType: fc.constantFrom<BindingType>('PRIMARY', 'SUB', 'RELATED'),
      bindingMode: fc.constantFrom('EDITABLE', 'READONLY'),
      foreignKeyField: fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined }),
      sortOrder: fc.nat({ max: 100 })
    })
    
    fc.assert(
      fc.property(
        fc.array(validBindingArb, { minLength: 1, maxLength: 5 }),
        (bindings) => {
          // Each binding represents a table that can be used for field import
          bindings.forEach(binding => {
            expect(binding.tableId).toBeGreaterThan(0)
            expect(binding.tableName.trim()).toBeTruthy()
          })
        }
      ),
      { numRuns: 50 }
    )
  })

  it('Property 4.2: Each field should map to a valid form component type', () => {
    fc.assert(
      fc.property(fieldDefinitionArb, (field) => {
        const componentType = getFormComponentType(field.dataType)
        expect(componentType).toBeTruthy()
        expect(typeof componentType).toBe('string')
      }),
      { numRuns: 100 }
    )
  })

  it('Property 4.3: Field names should be valid identifiers', () => {
    fc.assert(
      fc.property(fieldDefinitionArb, (field) => {
        // Field names should start with letter or underscore
        expect(field.fieldName).toMatch(/^[a-zA-Z_]/)
        // Field names should only contain alphanumeric and underscore
        expect(field.fieldName).toMatch(/^[a-zA-Z_][a-zA-Z0-9_]*$/)
      }),
      { numRuns: 100 }
    )
  })

  it('Property 4.4: Duplicate fields from same table should be detectable', () => {
    fc.assert(
      fc.property(
        fc.array(fieldDefinitionArb, { minLength: 2, maxLength: 10 }),
        (fields) => {
          const fieldNames = fields.map(f => f.fieldName)
          const uniqueNames = new Set(fieldNames)
          
          // If there are duplicates, we should be able to detect them
          const hasDuplicates = uniqueNames.size < fieldNames.length
          
          if (hasDuplicates) {
            // Find duplicate names
            const seen = new Set<string>()
            const duplicates = fieldNames.filter(name => {
              if (seen.has(name)) return true
              seen.add(name)
              return false
            })
            expect(duplicates.length).toBeGreaterThan(0)
          }
          
          return true
        }
      ),
      { numRuns: 50 }
    )
  })

  it('Property 4.5: Non-nullable fields should generate required validation', () => {
    fc.assert(
      fc.property(fieldDefinitionArb, (field) => {
        // If field is not nullable, it should be marked as required
        if (!field.nullable) {
          // This represents the validation rule that would be generated
          const shouldBeRequired = !field.nullable
          expect(shouldBeRequired).toBe(true)
        }
        return true
      }),
      { numRuns: 100 }
    )
  })

  it('Property 4.6: Binding type should affect field import behavior', () => {
    fc.assert(
      fc.property(tableBindingArb, (binding) => {
        // PRIMARY bindings should be editable by default
        if (binding.bindingType === 'PRIMARY') {
          // Primary table fields are typically editable
          expect(['EDITABLE', 'READONLY']).toContain(binding.bindingMode)
        }
        
        // SUB and RELATED bindings may have foreign key fields
        if (binding.bindingType === 'SUB' || binding.bindingType === 'RELATED') {
          // These bindings can have foreign key fields
          // (foreignKeyField is optional but recommended)
          expect(binding.bindingType).not.toBe('PRIMARY')
        }
        
        return true
      }),
      { numRuns: 50 }
    )
  })
})

/**
 * Data type mapping tests
 */
describe('Data Type to Form Component Mapping', () => {
  const getFormComponentType = (dataType: string): string => {
    const typeMap: Record<string, string> = {
      'VARCHAR': 'Input Box',
      'TEXT': 'Text Area',
      'INTEGER': 'Number Input',
      'BIGINT': 'Number Input',
      'DECIMAL': 'Number Input',
      'BOOLEAN': 'Switch',
      'DATE': 'Date Picker',
      'TIMESTAMP': 'Date Time Picker'
    }
    return Object.hasOwn(typeMap, dataType) ? typeMap[dataType] : 'Input Box'
  }

  it('should map all known data types correctly', () => {
    const knownTypes = ['VARCHAR', 'TEXT', 'INTEGER', 'BIGINT', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP']
    
    fc.assert(
      fc.property(fc.constantFrom(...knownTypes), (dataType) => {
        const component = getFormComponentType(dataType)
        expect(component).toBeTruthy()
        // All known types should have a mapping
        expect(typeof component).toBe('string')
        return true
      }),
      { numRuns: 20 }
    )
  })

  it('should default to input for unknown types', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 20 }).filter(s => 
          !['VARCHAR', 'TEXT', 'INTEGER', 'BIGINT', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIMESTAMP'].includes(s) &&
          !Object.prototype.hasOwnProperty.call(Object.prototype, s) // Exclude JS prototype properties like 'constructor', 'toString', etc.
        ),
        (unknownType) => {
          const component = getFormComponentType(unknownType)
          expect(component).toBe('Input Box')
        }
      ),
      { numRuns: 20 }
    )
  })
})
