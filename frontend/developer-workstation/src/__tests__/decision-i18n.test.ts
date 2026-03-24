import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Property 13: i18n 键完整性
 *
 * For any decision namespace i18n key, en, zh-CN, and zh-TW locale files
 * should all contain that key with a non-empty translation value.
 *
 * **Validates: Requirements 14.2**
 */

const LOCALES_DIR = path.resolve(__dirname, '..', 'i18n', 'locales')

/**
 * Parse a TypeScript locale file and extract the default export object.
 */
function parseLocaleFile(filePath: string): Record<string, unknown> {
  const content = fs.readFileSync(filePath, 'utf-8')
  const match = content.match(/export\s+default\s+(\{[\s\S]*\})\s*;?\s*$/)
  if (!match) {
    throw new Error(`Could not parse locale file: ${filePath}`)
  }
  const fn = new Function(`return (${match[1]})`)
  return fn() as Record<string, unknown>
}

/**
 * Recursively extract all keys from a nested object as flattened dot-notation strings.
 */
function extractKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = []
  for (const key of Object.keys(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    const value = obj[key]
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...extractKeys(value as Record<string, unknown>, fullKey))
    } else {
      keys.push(fullKey)
    }
  }
  return keys.sort()
}

/**
 * Resolve a dot-notation key path on a nested object, returning the leaf value.
 */
function resolveKey(obj: Record<string, unknown>, keyPath: string): unknown {
  const parts = keyPath.split('.')
  let current: unknown = obj
  for (const part of parts) {
    if (current === null || current === undefined || typeof current !== 'object') {
      return undefined
    }
    current = (current as Record<string, unknown>)[part]
  }
  return current
}

// Parse all three locale files once
const enObj = parseLocaleFile(path.join(LOCALES_DIR, 'en.ts'))
const zhCNObj = parseLocaleFile(path.join(LOCALES_DIR, 'zh-CN.ts'))
const zhTWObj = parseLocaleFile(path.join(LOCALES_DIR, 'zh-TW.ts'))

// Extract decision namespace objects
const enDecision = enObj.decision as Record<string, unknown> | undefined
const zhCNDecision = zhCNObj.decision as Record<string, unknown> | undefined
const zhTWDecision = zhTWObj.decision as Record<string, unknown> | undefined

// Extract flattened keys for the decision namespace from each locale
const enDecisionKeys = enDecision ? extractKeys(enDecision) : []
const zhCNDecisionKeys = zhCNDecision ? extractKeys(zhCNDecision) : []
const zhTWDecisionKeys = zhTWDecision ? extractKeys(zhTWDecision) : []

// Union of all decision keys across all locales
const allDecisionKeys = [...new Set([...enDecisionKeys, ...zhCNDecisionKeys, ...zhTWDecisionKeys])].sort()

describe('Property 13: i18n 键完整性 — decision 命名空间', () => {
  it('all three locale files should have a decision namespace', () => {
    expect(enDecision, 'en.ts should have decision namespace').toBeDefined()
    expect(zhCNDecision, 'zh-CN.ts should have decision namespace').toBeDefined()
    expect(zhTWDecision, 'zh-TW.ts should have decision namespace').toBeDefined()
  })

  it('decision namespace should have at least one key', () => {
    expect(allDecisionKeys.length).toBeGreaterThan(0)
  })

  /**
   * Property test: for any randomly selected decision key from the union of all
   * three locales, that key must exist in all three locales with a non-empty value.
   *
   * **Validates: Requirements 14.2**
   */
  it('any decision key should exist in all three locales with non-empty value', () => {
    // Build an arbitrary that picks from the union of all decision keys
    const keyArb = fc.constantFrom(...allDecisionKeys)

    fc.assert(
      fc.property(keyArb, (key) => {
        const prefixedKey = `decision.${key}`

        // Check en
        const enValue = resolveKey(enObj, prefixedKey)
        expect(enValue, `en.ts missing key: ${prefixedKey}`).toBeDefined()
        expect(typeof enValue === 'string' ? enValue : 'non-empty', `en.ts empty value for: ${prefixedKey}`).not.toBe('')

        // Check zh-CN
        const zhCNValue = resolveKey(zhCNObj, prefixedKey)
        expect(zhCNValue, `zh-CN.ts missing key: ${prefixedKey}`).toBeDefined()
        expect(typeof zhCNValue === 'string' ? zhCNValue : 'non-empty', `zh-CN.ts empty value for: ${prefixedKey}`).not.toBe('')

        // Check zh-TW
        const zhTWValue = resolveKey(zhTWObj, prefixedKey)
        expect(zhTWValue, `zh-TW.ts missing key: ${prefixedKey}`).toBeDefined()
        expect(typeof zhTWValue === 'string' ? zhTWValue : 'non-empty', `zh-TW.ts empty value for: ${prefixedKey}`).not.toBe('')
      }),
      { numRuns: allDecisionKeys.length * 3 }
    )
  })

  it('en, zh-CN, zh-TW decision namespaces should have the same key set', () => {
    const enSet = new Set(enDecisionKeys)
    const zhCNSet = new Set(zhCNDecisionKeys)
    const zhTWSet = new Set(zhTWDecisionKeys)

    const missingInZhCN = enDecisionKeys.filter((k) => !zhCNSet.has(k))
    const missingInZhTW = enDecisionKeys.filter((k) => !zhTWSet.has(k))
    const missingInEn = [...new Set([
      ...zhCNDecisionKeys.filter((k) => !enSet.has(k)),
      ...zhTWDecisionKeys.filter((k) => !enSet.has(k)),
    ])]

    const issues: string[] = []
    if (missingInZhCN.length > 0) issues.push(`Keys in en but missing from zh-CN: ${missingInZhCN.join(', ')}`)
    if (missingInZhTW.length > 0) issues.push(`Keys in en but missing from zh-TW: ${missingInZhTW.join(', ')}`)
    if (missingInEn.length > 0) issues.push(`Keys in zh-CN/zh-TW but missing from en: ${missingInEn.join(', ')}`)

    expect(issues, issues.join('\n')).toHaveLength(0)
  })
})
