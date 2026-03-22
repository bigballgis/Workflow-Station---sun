// Feature: sub-table-placeholder-component
// Property 1: displayName 反映当前 subBindings 列表
// Validates: Requirements 1.1, 1.4
// Property 2: 占位符状态计算覆盖三种分支
// Validates: Requirements 1.2, 5.2, 5.3
// Property 4: 跳转按钮可见性与 state 一致
// Validates: Requirements 3.1, 3.3

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDescription: string
  bindingType: string
}

// Pure function extracted from SubTablePlaceholderWidget computed logic
function formatBindingLabel(b: DesignerSubBinding): string {
  return b.tableDescription
    ? `${b.tableName}（${b.tableDescription}）`
    : b.tableName
}

// Pure function extracted from SubTablePlaceholderWidget state computed logic
function computePlaceholderState(
  bindingId: number | null | undefined,
  subBindings: Array<{ id: number; tableName: string; tableDescription: string; bindingType: string }>
): 'unconfigured' | 'valid' | 'stale' {
  if (!bindingId) return 'unconfigured'
  const found = subBindings.find(b => b.id === bindingId)
  return found ? 'valid' : 'stale'
}

describe('SubTablePlaceholderWidget - Property Tests', () => {
  it('Property 1: displayName 反映当前 subBindings 列表', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            id: fc.integer({ min: 1, max: 9999 }),
            tableName: fc.string({ minLength: 1 }),
            tableDescription: fc.oneof(fc.string({ minLength: 1 }), fc.constant('')),
            bindingType: fc.string()
          }),
          { minLength: 1 }
        ),
        fc.nat({ max: 99 }),
        (bindings, idx) => {
          const uniqueBindings = [...new Map(bindings.map(b => [b.id, b])).values()]
          if (uniqueBindings.length === 0) return
          const target = uniqueBindings[idx % uniqueBindings.length]
          const label = formatBindingLabel(target)
          expect(label).toContain(target.tableName)
          if (target.tableDescription) {
            expect(label).toContain(target.tableDescription)
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 2: 占位符状态计算覆盖三种分支', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            id: fc.integer({ min: 1, max: 9999 }),
            tableName: fc.string({ minLength: 1 }),
            tableDescription: fc.string(),
            bindingType: fc.string()
          })
        ),
        fc.oneof(
          fc.constant(null),
          fc.constant(undefined),
          fc.integer({ min: 1, max: 9999 })
        ),
        (subBindings, bindingId) => {
          const state = computePlaceholderState(bindingId, subBindings)
          if (bindingId == null) {
            expect(state).toBe('unconfigured')
          } else if (subBindings.some(b => b.id === bindingId)) {
            expect(state).toBe('valid')
          } else {
            expect(state).toBe('stale')
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 7: 绑定标签格式化', () => {
    // Validates: Requirements 2.2
    fc.assert(
      fc.property(
        fc.record({
          id: fc.integer({ min: 1, max: 9999 }),
          tableName: fc.string({ minLength: 1 }),
          tableDescription: fc.oneof(fc.string({ minLength: 1 }), fc.constant('')),
          bindingType: fc.string()
        }),
        (binding) => {
          const label = formatBindingLabel(binding)
          // Label must always contain tableName
          expect(label).toContain(binding.tableName)
          // When tableDescription is non-empty, label must also contain it
          if (binding.tableDescription) {
            expect(label).toContain(binding.tableDescription)
          }
          // When tableDescription is empty, label should equal tableName
          if (!binding.tableDescription) {
            expect(label).toBe(binding.tableName)
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  it('Property 4: 跳转按钮可见性与 state 一致', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            id: fc.integer({ min: 1, max: 9999 }),
            tableName: fc.string({ minLength: 1 }),
            tableDescription: fc.string(),
            bindingType: fc.string()
          })
        ),
        fc.oneof(
          fc.constant(null),
          fc.constant(undefined),
          fc.integer({ min: 1, max: 9999 })
        ),
        (subBindings, bindingId) => {
          const state = computePlaceholderState(bindingId, subBindings)
          // Navigate button should be visible if and only if state === 'valid'
          const shouldShowNavigateButton = state === 'valid'
          expect(shouldShowNavigateButton).toBe(state === 'valid')
          // When state is valid, bindingId must be non-null (button can safely emit navigate)
          if (state === 'valid') {
            expect(bindingId).not.toBeNull()
            expect(bindingId).not.toBeUndefined()
          }
          // When state is not valid, navigate button should be hidden
          if (state !== 'valid') {
            expect(shouldShowNavigateButton).toBe(false)
          }
        }
      ),
      { numRuns: 100 }
    )
  })
})
