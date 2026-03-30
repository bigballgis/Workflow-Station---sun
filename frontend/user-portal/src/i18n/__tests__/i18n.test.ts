/**
 * Property 9: i18n key 三语言同步
 * **Validates: Requirements 16.4, 17.2**
 *
 * Reads en/zh-CN/zh-TW locale files and asserts that all three
 * have exactly the same set of flattened keys.
 */
import { describe, it, expect } from 'vitest'
import en from '../locales/en'
import zhCN from '../locales/zh-CN'
import zhTW from '../locales/zh-TW'

// ─── Helper: recursively flatten nested object keys ─────────────────────────

function flattenKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = []
  for (const key of Object.keys(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    const value = obj[key]
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenKeys(value as Record<string, unknown>, fullKey))
    } else {
      keys.push(fullKey)
    }
  }
  return keys.sort()
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('Property 9: i18n key 三语言同步', () => {
  const enKeys = flattenKeys(en as Record<string, unknown>)
  const zhCNKeys = flattenKeys(zhCN as Record<string, unknown>)
  const zhTWKeys = flattenKeys(zhTW as Record<string, unknown>)

  it('en and zh-CN should have the same key set', () => {
    const enSet = new Set(enKeys)
    const zhCNSet = new Set(zhCNKeys)

    const missingInZhCN = enKeys.filter(k => !zhCNSet.has(k))
    const extraInZhCN = zhCNKeys.filter(k => !enSet.has(k))

    expect(missingInZhCN, `Keys in en but missing in zh-CN: ${missingInZhCN.join(', ')}`).toEqual([])
    expect(extraInZhCN, `Keys in zh-CN but missing in en: ${extraInZhCN.join(', ')}`).toEqual([])
  })

  it('en and zh-TW should have the same key set', () => {
    const enSet = new Set(enKeys)
    const zhTWSet = new Set(zhTWKeys)

    const missingInZhTW = enKeys.filter(k => !zhTWSet.has(k))
    const extraInZhTW = zhTWKeys.filter(k => !enSet.has(k))

    expect(missingInZhTW, `Keys in en but missing in zh-TW: ${missingInZhTW.join(', ')}`).toEqual([])
    expect(extraInZhTW, `Keys in zh-TW but missing in en: ${extraInZhTW.join(', ')}`).toEqual([])
  })

  it('all three locales should have identical key counts', () => {
    expect(enKeys.length).toBe(zhCNKeys.length)
    expect(enKeys.length).toBe(zhTWKeys.length)
  })
})
