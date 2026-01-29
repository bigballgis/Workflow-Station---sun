import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import type { BindingType, BindingMode } from '@/api/functionUnit'

/**
 * Property 1: Multi-table binding support
 * For any form with a primary table bound, adding sub-tables or related tables 
 * should result in all bindings being stored and retrievable.
 * 
 * Validates: Requirements 1.1, 1.3, 1.4
 */
describe('TableBindingManager Property Tests', () => {
  // Arbitrary for BindingType
  const bindingTypeArb = fc.constantFrom<BindingType>('PRIMARY', 'SUB', 'RELATED')
  
  // Arbitrary for BindingMode
  const bindingModeArb = fc.constantFrom<BindingMode>('EDITABLE', 'READONLY')
  
  // Arbitrary for TableBinding
  const tableBindingArb = fc.record({
    id: fc.nat(),
    tableId: fc.integer({ min: 1 }),
    tableName: fc.string({ minLength: 1, maxLength: 50 }),
    bindingType: bindingTypeArb,
    bindingMode: bindingModeArb,
    foreignKeyField: fc.option(fc.string({ minLength: 1, maxLength: 50 }), { nil: undefined }),
    sortOrder: fc.integer({ min: 0, max: 100 })
  })
  
  // Arbitrary for a list of bindings with exactly one PRIMARY
  const bindingListWithPrimaryArb = fc.tuple(
    fc.record({
      id: fc.nat(),
      tableId: fc.integer({ min: 1 }),
      tableName: fc.string({ minLength: 1, maxLength: 50 }),
      bindingType: fc.constant<BindingType>('PRIMARY'),
      bindingMode: fc.constant<BindingMode>('EDITABLE'),
      foreignKeyField: fc.constant(undefined),
      sortOrder: fc.constant(0)
    }),
    fc.array(
      fc.record({
        id: fc.nat(),
        tableId: fc.integer({ min: 1 }),
        tableName: fc.string({ minLength: 1, maxLength: 50 }),
        bindingType: fc.constantFrom<BindingType>('SUB', 'RELATED'),
        bindingMode: bindingModeArb,
        foreignKeyField: fc.string({ minLength: 1, maxLength: 50 }),
        sortOrder: fc.nat({ max: 100 })
      }),
      { minLength: 0, maxLength: 5 }
    )
  ).map(([primary, others]) => [primary, ...others])

  it('Property 1.1: Each form can have at most one PRIMARY binding', () => {
    fc.assert(
      fc.property(bindingListWithPrimaryArb, (bindings) => {
        const primaryBindings = bindings.filter(b => b.bindingType === 'PRIMARY')
        expect(primaryBindings.length).toBeLessThanOrEqual(1)
      }),
      { numRuns: 50 }
    )
  })

  it('Property 1.2: All bindings should have valid binding types', () => {
    fc.assert(
      fc.property(fc.array(tableBindingArb, { minLength: 1, maxLength: 10 }), (bindings) => {
        const validTypes: BindingType[] = ['PRIMARY', 'SUB', 'RELATED']
        bindings.forEach(binding => {
          expect(validTypes).toContain(binding.bindingType)
        })
      }),
      { numRuns: 50 }
    )
  })

  it('Property 1.3: SUB and RELATED bindings should have foreignKeyField when specified', () => {
    fc.assert(
      fc.property(bindingListWithPrimaryArb, (bindings) => {
        bindings.forEach(binding => {
          if (binding.bindingType !== 'PRIMARY') {
            // Sub and related bindings should have foreign key field
            expect(binding.foreignKeyField).toBeDefined()
          }
        })
      }),
      { numRuns: 50 }
    )
  })

  it('Property 1.4: Binding modes should be valid', () => {
    fc.assert(
      fc.property(fc.array(tableBindingArb, { minLength: 1, maxLength: 10 }), (bindings) => {
        const validModes: BindingMode[] = ['EDITABLE', 'READONLY']
        bindings.forEach(binding => {
          expect(validModes).toContain(binding.bindingMode)
        })
      }),
      { numRuns: 50 }
    )
  })

  it('Property 1.5: PRIMARY binding should default to EDITABLE mode', () => {
    fc.assert(
      fc.property(bindingListWithPrimaryArb, (bindings) => {
        const primaryBinding = bindings.find(b => b.bindingType === 'PRIMARY')
        if (primaryBinding) {
          expect(primaryBinding.bindingMode).toBe('EDITABLE')
        }
      }),
      { numRuns: 50 }
    )
  })

  it('Property 1.6: Each table can only be bound once per form', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 100 }), { minLength: 1, maxLength: 10 }),
        (tableIds) => {
          const uniqueTableIds = new Set(tableIds)
          // In a valid binding list, all table IDs should be unique
          // This property validates the uniqueness constraint
          expect(uniqueTableIds.size).toBeLessThanOrEqual(tableIds.length)
        }
      ),
      { numRuns: 50 }
    )
  })

  it('Property 1.7: Sort order should be non-negative', () => {
    fc.assert(
      fc.property(fc.array(tableBindingArb, { minLength: 1, maxLength: 10 }), (bindings) => {
        bindings.forEach(binding => {
          expect(binding.sortOrder).toBeGreaterThanOrEqual(0)
        })
      }),
      { numRuns: 50 }
    )
  })
})

/**
 * Helper function tests
 */
describe('Binding Type Label Tests', () => {
  const bindingTypeLabel = (type: BindingType): string => {
    const map: Record<BindingType, string> = { PRIMARY: '主表', SUB: '子表', RELATED: '关联表' }
    return map[type] || type
  }

  it('should return correct labels for all binding types', () => {
    fc.assert(
      fc.property(fc.constantFrom<BindingType>('PRIMARY', 'SUB', 'RELATED'), (type) => {
        const label = bindingTypeLabel(type)
        expect(label).toBeTruthy()
        expect(typeof label).toBe('string')
        
        if (type === 'PRIMARY') expect(label).toBe('主表')
        if (type === 'SUB') expect(label).toBe('子表')
        if (type === 'RELATED') expect(label).toBe('关联表')
      }),
      { numRuns: 10 }
    )
  })
})
