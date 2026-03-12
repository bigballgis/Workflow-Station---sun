import { describe, it, expect } from 'vitest'
import fc from 'fast-check'

/**
 * Property 13: 徽标显示格式化
 * 验证: 需求 6.2, 6.3
 *
 * 徽标格式化规则:
 * - unreadCount === 0 → 隐藏徽标
 * - unreadCount > 99 → 显示 "99+"
 * - 其他 → 显示实际数字
 */

// Extract the badge formatting logic (mirrors NotificationBadge.vue computed)
function formatBadgeValue(unreadCount: number): string | number {
  if (unreadCount > 99) return '99+'
  return unreadCount
}

function isBadgeHidden(unreadCount: number): boolean {
  return unreadCount === 0
}

describe('NotificationBadge 格式化属性测试', () => {
  it('Property 13.1: unreadCount 为 0 时徽标应隐藏', () => {
    fc.assert(
      fc.property(fc.constant(0), (count) => {
        expect(isBadgeHidden(count)).toBe(true)
        expect(formatBadgeValue(count)).toBe(0)
      }),
      { numRuns: 1 }
    )
  })

  it('Property 13.2: unreadCount 在 1-99 之间时应显示实际数字', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 99 }), (count) => {
        expect(isBadgeHidden(count)).toBe(false)
        expect(formatBadgeValue(count)).toBe(count)
      }),
      { numRuns: 100 }
    )
  })

  it('Property 13.3: unreadCount 超过 99 时应显示 "99+"', () => {
    fc.assert(
      fc.property(fc.integer({ min: 100, max: 100000 }), (count) => {
        expect(isBadgeHidden(count)).toBe(false)
        expect(formatBadgeValue(count)).toBe('99+')
      }),
      { numRuns: 100 }
    )
  })

  it('Property 13.4: 对任意非负整数，格式化结果类型正确', () => {
    fc.assert(
      fc.property(fc.nat({ max: 100000 }), (count) => {
        const value = formatBadgeValue(count)
        if (count > 99) {
          expect(typeof value).toBe('string')
          expect(value).toBe('99+')
        } else {
          expect(typeof value).toBe('number')
          expect(value).toBe(count)
        }
      }),
      { numRuns: 200 }
    )
  })
})
