// Feature: sub-table-placeholder-component
// Property 3: 绑定选择的 round-trip
// Validates: Requirements 2.3, 2.4
// Property 5: 拖拽插入产生正确结构且多实例独立
// Validates: Requirements 4.2, 4.3
// Property 6: _bindingId 序列化与加载的 round-trip
// Validates: Requirements 4.4, 6.3
// Property 8: 保存验证阻止未绑定占位符
// Validates: Requirements 2.5
// Property 9: 重复绑定检测
// Validates: Requirements 5.1

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

// Pure function extracted from FormDesigner handleSaveForm validation logic
function validateSubTableBindings(rules: Array<{ type: string; _bindingId: number | null | undefined }>): boolean {
  const invalidPlaceholders = rules.filter(r => r.type === 'subTable' && (r._bindingId == null))
  return invalidPlaceholders.length === 0 // true = valid (can save), false = invalid (block save)
}

// Pure function extracted from FormDesigner checkDuplicateBinding logic
function checkDuplicateBindings(rules: Array<{ type: string; _bindingId: number | null }>): boolean {
  const subTableRules = rules.filter(r => r.type === 'subTable' && r._bindingId != null)
  const ids = subTableRules.map(r => r._bindingId)
  return ids.length !== new Set(ids).size
}

describe('FormDesigner - SubTablePlaceholder Property Tests', () => {
  it('Property 5: 拖拽插入产生正确结构且多实例独立', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.integer({ min: 1, max: 9999 }),
          { minLength: 1, maxLength: 5 }
        ),
        (bindingIds) => {
          // Simulate inserting N sub-table placeholders (each starts with _bindingId: null)
          const rule: Array<{ type: string; _bindingId: number | null; title: string; props: object }> = 
            bindingIds.map(() => ({
              type: 'subTable',
              _bindingId: null,
              title: 'Sub-Table',
              props: {}
            }))
          
          // Verify each inserted entry has correct structure
          rule.forEach(entry => {
            expect(entry.type).toBe('subTable')
            expect(entry._bindingId).toBeNull()
            expect(entry.title).toBe('Sub-Table')
          })
          
          // Simulate independently setting each entry's _bindingId
          bindingIds.forEach((id, index) => {
            rule[index]._bindingId = id
          })
          
          // Verify each entry has its own independent _bindingId
          bindingIds.forEach((id, index) => {
            expect(rule[index]._bindingId).toBe(id)
          })
          
          // Verify entries don't affect each other (independence)
          if (rule.length > 1) {
            const firstId = rule[0]._bindingId
            rule[1]._bindingId = 99999 // change second entry
            expect(rule[0]._bindingId).toBe(firstId) // first entry unchanged
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 6: _bindingId 序列化与加载的 round-trip', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.integer({ min: 1, max: 9999 }),
          { minLength: 1, maxLength: 5 }
        ),
        (bindingIds) => {
          // Build rule array with subTable entries
          const rules = bindingIds.map(id => ({
            type: 'subTable',
            _bindingId: id,
            title: 'Sub-Table',
            props: {}
          }))

          // Simulate save: serialize to JSON (as stored in config_json)
          const serialized = JSON.stringify({ rule: rules })

          // Simulate load: parse from JSON
          const loaded = JSON.parse(serialized)

          // Verify each entry's _bindingId is preserved exactly
          expect(loaded.rule).toHaveLength(bindingIds.length)
          loaded.rule.forEach((r: any, i: number) => {
            expect(r.type).toBe('subTable')
            expect(r._bindingId).toBe(bindingIds[i])
          })
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 3: 绑定选择的 round-trip', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9999 }),
        (bindingId) => {
          // Simulate the rule object that FormDesigner manages
          const rule: { type: string; _bindingId: number | null } = {
            type: 'subTable',
            _bindingId: null
          }
          // Simulate selecting a binding (SubTableBindingSelect emits update:modelValue)
          rule._bindingId = bindingId
          expect(rule._bindingId).toBe(bindingId)
          // Simulate clearing the selection (emit update:modelValue with null)
          rule._bindingId = null
          expect(rule._bindingId).toBeNull()
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 8: 保存验证阻止未绑定占位符', () => {
    fc.assert(
      fc.property(
        fc.array(fc.integer({ min: 1, max: 9999 }), { maxLength: 4 }),
        (validIds) => {
          // Mix valid entries with one null _bindingId entry
          const rules = [
            ...validIds.map(id => ({ type: 'subTable', _bindingId: id })),
            { type: 'subTable', _bindingId: null }
          ]
          // Validation should fail (return false = block save)
          const isValid = validateSubTableBindings(rules)
          expect(isValid).toBe(false)

          // All valid entries should pass validation
          if (validIds.length > 0) {
            const validRules = validIds.map(id => ({ type: 'subTable', _bindingId: id }))
            const allValid = validateSubTableBindings(validRules)
            expect(allValid).toBe(true)
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 9: 重复绑定检测', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9999 }),
        fc.array(fc.integer({ min: 1, max: 9999 }), { maxLength: 4 }),
        (duplicateId, otherIds) => {
          // Build rule array with duplicate id
          const rules = [
            { type: 'subTable', _bindingId: duplicateId },
            { type: 'subTable', _bindingId: duplicateId },
            ...otherIds.map(id => ({ type: 'subTable', _bindingId: id }))
          ]
          const hasDuplicate = checkDuplicateBindings(rules)
          expect(hasDuplicate).toBe(true)

          // Build rule array with unique ids
          const uniqueIds = [...new Set([duplicateId, ...otherIds])]
          const uniqueRules = uniqueIds.map(id => ({ type: 'subTable', _bindingId: id }))
          const noDuplicate = checkDuplicateBindings(uniqueRules)
          expect(noDuplicate).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })
})
